package de.magynhard.crystal.run

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.events.*
import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor

/**
 * Parses Crystal spec verbose output (-v --no-color) into SMTRunner test events.
 *
 * Crystal's verbose output format:
 *   <SuiteName>
 *     <testName>  <testName>
 *   Failures:
 *     1) SuiteName testName
 *        Failure/Error: <code>
 *        # file:line
 *   Finished in <time>
 *   <N> examples, <M> failures, ...
 *
 * If testLocations are provided, each test event includes a crystal_spec:// URL
 * for navigation to the source location.
 */
class CrystalTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties,
    private val testLocations: Map<String, CrystalSpecFileIndexer.TestLocation> = emptyMap()
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {

    private enum class State { RUNNING, FAILURES, SUMMARY }

    private var state = State.RUNNING
    private val suiteStack = mutableListOf<String>()
    private var currentTestName: String? = null
    private var currentFailureName: String? = null
    private var currentFailureMessage = StringBuilder()
    private var failureFileLocation: String? = null
    private val failedTests = mutableSetOf<String>()
    private val startedTests = mutableListOf<String>()
    private var collectingFailureMessage = false
    private var lineBuffer = StringBuilder()

    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        // Only parse stdout from Crystal spec
        if (outputType != ProcessOutputTypes.STDOUT) {
            return false
        }

        // Buffer text and process complete lines
        lineBuffer.append(text)
        while (true) {
            val newlineIdx = lineBuffer.indexOf('\n')
            if (newlineIdx < 0) break
            val line = lineBuffer.substring(0, newlineIdx).trimEnd('\r')
            lineBuffer.delete(0, newlineIdx + 1)
            processLine(line)
        }

        return true
    }

    override fun dispose() {
        // Flush remaining buffer
        if (lineBuffer.isNotEmpty()) {
            processLine(lineBuffer.toString().trimEnd('\r', '\n'))
            lineBuffer.clear()
        }
        finalizeTests()
        super.dispose()
    }

    private fun processLine(line: String) {
        when (state) {
            State.RUNNING -> processRunningLine(line)
            State.FAILURES -> processFailureLine(line)
            State.SUMMARY -> {} // nothing
        }
    }

    private fun processRunningLine(line: String) {
        if (line.trimStart() == "Failures:") {
            finishCurrentTest()
            state = State.FAILURES
            return
        }

        if (line.trimStart().startsWith("Finished in")) {
            finishCurrentTest()
            closeAllSuites()
            state = State.SUMMARY
            return
        }

        if (line.isBlank()) return

        val stripped = line.trimStart()
        val indent = line.length - stripped.length

        if (isDuplicatedName(stripped)) {
            // Test case
            val testName = stripped.substring(0, stripped.length / 2).trimEnd()
            finishCurrentTest()
            currentTestName = testName
            val fullName = (suiteStack + testName).joinToString(" ")
            startedTests.add(fullName)

            // Look up source location for navigation
            val location = testLocations[fullName]
            val url = if (location != null) "${CrystalTestLocator.PROTOCOL}://${location.file}:${location.line}" else null
            getProcessor().onTestStarted(TestStartedEvent(testName, url))
        } else {
            // Suite — adjust stack based on indentation
            val level = indent / 2
            while (suiteStack.size > level) {
                finishCurrentTest()
                val closed = suiteStack.removeAt(suiteStack.size - 1)
                getProcessor().onSuiteFinished(TestSuiteFinishedEvent(closed))
            }
            finishCurrentTest()
            suiteStack.add(stripped)
            getProcessor().onSuiteStarted(TestSuiteStartedEvent(stripped, null))
        }
    }

    private fun processFailureLine(line: String) {
        val trimmed = line.trimStart()

        if (trimmed.startsWith("Finished in")) {
            flushCurrentFailure()
            closeAllSuites()
            state = State.SUMMARY
            return
        }

        val failureMatch = Regex("""^\s*\d+\)\s+(.+)$""").find(line)
        if (failureMatch != null) {
            flushCurrentFailure()
            currentFailureName = failureMatch.groupValues[1].trim()
            currentFailureMessage.clear()
            failureFileLocation = null
            collectingFailureMessage = false
            return
        }

        if (currentFailureName != null) {
            if (trimmed.startsWith("Failure/Error:")) {
                collectingFailureMessage = true
                val msg = trimmed.removePrefix("Failure/Error:").trim()
                if (msg.isNotEmpty()) currentFailureMessage.appendLine(msg)
                return
            }

            val locationMatch = Regex("""^\s*#\s+(.+:\d+)\s*$""").find(line)
            if (locationMatch != null) {
                failureFileLocation = locationMatch.groupValues[1]
                collectingFailureMessage = false
                return
            }

            if (collectingFailureMessage && trimmed.isNotEmpty()) {
                currentFailureMessage.appendLine(trimmed)
            }
        }
    }

    private fun flushCurrentFailure() {
        val name = currentFailureName ?: return
        currentFailureName = null
        failedTests.add(name)

        val testName = findTestNameFromFullName(name)
        val message = currentFailureMessage.toString().trim()
        val details = if (failureFileLocation != null) "${CrystalTestLocator.PROTOCOL}://$failureFileLocation" else ""

        getProcessor().onTestFailure(TestFailedEvent(testName, message, details, false, null, null))
        getProcessor().onTestFinished(TestFinishedEvent(testName, null))
    }

    private fun findTestNameFromFullName(fullName: String): String {
        for (started in startedTests.reversed()) {
            if (started == fullName || fullName.endsWith(started)) {
                return started.split(" ").last()
            }
        }
        return fullName.split(" ").last()
    }

    private fun finishCurrentTest() {
        val testName = currentTestName ?: return
        currentTestName = null
        val fullName = (suiteStack + testName).joinToString(" ")
        if (fullName !in failedTests) {
            getProcessor().onTestFinished(TestFinishedEvent(testName, null))
        }
    }

    private fun closeAllSuites() {
        while (suiteStack.isNotEmpty()) {
            val closed = suiteStack.removeAt(suiteStack.size - 1)
            getProcessor().onSuiteFinished(TestSuiteFinishedEvent(closed))
        }
    }

    private fun finalizeTests() {
        finishCurrentTest()
        flushCurrentFailure()
        closeAllSuites()
    }

    companion object {
        fun isDuplicatedName(stripped: String): Boolean {
            if (stripped.length < 3) return false
            val splitRegex = Regex("""^(.+?)\s{2,}\1$""")
            return splitRegex.matches(stripped)
        }
    }
}
