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
    private val fullNameToTestName = mutableMapOf<String, String>()
    private var collectingFailureMessage = false
    private var lineBuffer = StringBuilder()
    private var hasSeenTests = false
    private var pendingSuite: String? = null

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
            // Test case — confirm pending suite if any
            confirmPendingSuite(indent)
            val testName = stripped.substring(0, stripped.length / 2).trimEnd()
            finishCurrentTest()
            currentTestName = testName
            hasSeenTests = true
            val fullName = (suiteStack + testName).joinToString(" ")
            fullNameToTestName[fullName] = testName

            // Look up source location for navigation
            val location = testLocations[fullName]
            val url = if (location != null) "${CrystalTestLocator.PROTOCOL}://${location.file}:${location.line}" else null
            getProcessor().onTestStarted(TestStartedEvent(testName, url))
        } else {
            // Suite or non-test output (e.g. puts from main program)
            val level = indent / 2

            if (!hasSeenTests && indent == 0) {
                // Indent-0 line before first test: could be puts output or real suite
                // Save as pending — will be confirmed when a test or sub-suite follows
                pendingSuite = stripped
                return
            }

            // Confirm pending suite if we're entering a deeper indent
            confirmPendingSuite(indent)

            // Suite — adjust stack based on indentation
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

    private fun confirmPendingSuite(indent: Int) {
        val pending = pendingSuite ?: return
        pendingSuite = null
        // Add the pending suite at its level (indent 0 → level 0)
        val pendingLevel = 0
        while (suiteStack.size > pendingLevel) {
            finishCurrentTest()
            val closed = suiteStack.removeAt(suiteStack.size - 1)
            getProcessor().onSuiteFinished(TestSuiteFinishedEvent(closed))
        }
        suiteStack.add(pending)
        getProcessor().onSuiteStarted(TestSuiteStartedEvent(pending, null))
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

        val testName = fullNameToTestName[name] ?: name.split(" ").last()
        val message = currentFailureMessage.toString().trim()
        val details = if (failureFileLocation != null) "${CrystalTestLocator.PROTOCOL}://$failureFileLocation" else ""

        // If this failure matches the current (un-finished) test, clear it so finishCurrentTest won't double-fire
        val currentFullName = if (currentTestName != null) (suiteStack + currentTestName).joinToString(" ") else null
        if (currentFullName == name) {
            currentTestName = null
        }

        getProcessor().onTestFailure(TestFailedEvent(testName, message, details, false, null, null))
        getProcessor().onTestFinished(TestFinishedEvent(testName, null))
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
