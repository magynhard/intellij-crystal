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
 *
 * Important: Crystal reports ALL failures at the end (after all tests ran).
 * We must defer onTestFinished until after the FAILURES block is parsed,
 * so we can correctly mark failed tests as FAILED (not PASSED).
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
    private val pendingFinishes = mutableListOf<Pair<String, String>>() // (testName, fullName)
    private var collectingFailureMessage = false
    private var lineBuffer = StringBuilder()
    private var hasSeenTests = false
    private var pendingSuite: String? = null

    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        if (outputType != ProcessOutputTypes.STDOUT) {
            return false
        }

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
            State.SUMMARY -> {}
        }
    }

    private fun processRunningLine(line: String) {
        if (line.trimStart() == "Failures:") {
            state = State.FAILURES
            return
        }

        if (line.trimStart().startsWith("Finished in")) {
            deferFinishCurrentTest()
            state = State.SUMMARY
            return
        }

        if (line.isBlank()) return

        val stripped = line.trimStart()
        val indent = line.length - stripped.length

        if (isDuplicatedName(stripped)) {
            confirmPendingSuite(indent)
            val testName = stripped.substring(0, stripped.length / 2).trimEnd()
            deferFinishCurrentTest()
            currentTestName = testName
            hasSeenTests = true
            val fullName = (suiteStack + testName).joinToString(" ")
            fullNameToTestName[fullName] = testName

            val location = testLocations[fullName]
            val url = if (location != null) "${CrystalTestLocator.PROTOCOL}://${location.file}:${location.line}" else null
            getProcessor().onTestStarted(TestStartedEvent(testName, url))
        } else {
            val level = indent / 2

            if (!hasSeenTests && indent == 0) {
                pendingSuite = stripped
                return
            }

            confirmPendingSuite(indent)

            while (suiteStack.size > level) {
                deferFinishCurrentTest()
                val closed = suiteStack.removeAt(suiteStack.size - 1)
                getProcessor().onSuiteFinished(TestSuiteFinishedEvent(closed))
            }
            deferFinishCurrentTest()
            suiteStack.add(stripped)
            getProcessor().onSuiteStarted(TestSuiteStartedEvent(stripped, null))
        }
    }

    private fun confirmPendingSuite(indent: Int) {
        val pending = pendingSuite ?: return
        pendingSuite = null
        val pendingLevel = 0
        while (suiteStack.size > pendingLevel) {
            deferFinishCurrentTest()
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
            deferFinishCurrentTest()
            firePendingFinishes()
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

        // Remove from pending finishes — it will be finished as FAILED here
        pendingFinishes.removeAll { it.second == name }

        getProcessor().onTestFailure(TestFailedEvent(testName, message, details, false, null, null))
        getProcessor().onTestFinished(TestFinishedEvent(testName, null))
    }

    /**
     * Defer finishing the current test: save (testName, fullName) for later.
     * onTestFinished is NOT called here — it will be called in firePendingFinishes
     * after we know which tests failed.
     */
    private fun deferFinishCurrentTest() {
        val testName = currentTestName ?: return
        currentTestName = null
        val fullName = (suiteStack + testName).joinToString(" ")
        pendingFinishes.add(testName to fullName)
    }

    /**
     * Fire onTestFinished for all deferred tests that were NOT in the failures list.
     * Called after the FAILURES block is fully parsed.
     */
    private fun firePendingFinishes() {
        for ((testName, fullName) in pendingFinishes) {
            if (fullName !in failedTests) {
                getProcessor().onTestFinished(TestFinishedEvent(testName, null))
            }
        }
        pendingFinishes.clear()
    }

    private fun closeAllSuites() {
        while (suiteStack.isNotEmpty()) {
            val closed = suiteStack.removeAt(suiteStack.size - 1)
            getProcessor().onSuiteFinished(TestSuiteFinishedEvent(closed))
        }
    }

    private fun finalizeTests() {
        flushCurrentFailure()
        firePendingFinishes()
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
