package de.magynhard.crystal.run

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.events.*
import com.intellij.openapi.diagnostic.Logger
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
 * Two-pass architecture:
 * Pass 1: Parse output into an in-memory tree (suites + tests + failures)
 * Pass 2: Walk tree and emit framework events in correct order
 *
 * This ensures suites know about child failures before they're closed,
 * so the framework correctly marks parent suites as failed.
 */
class CrystalTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties,
    private val testLocations: Map<String, CrystalSpecFileIndexer.TestLocation> = emptyMap()
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {

    private val LOG = Logger.getInstance(CrystalTestEventsConverter::class.java)

    private val parser = Parser(testLocations) {
        emitEvents()
    }

    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        if (outputType != ProcessOutputTypes.STDOUT) {
            return false
        }
        parser.feedText(text)
        return true
    }

    override fun dispose() {
        parser.finish()
        super.dispose()
    }

    // ==================== Pass 2: Emitting ====================

    private fun emitEvents() {
        for (node in parser.rootChildren) {
            emitNode(node)
        }
    }

    private fun emitNode(node: TestNode) {
        when (node) {
            is TestNode.Suite -> emitSuite(node)
            is TestNode.Test -> emitTest(node)
        }
    }

    private fun emitSuite(suite: TestNode.Suite) {
        getProcessor().onSuiteStarted(TestSuiteStartedEvent(suite.name, null))
        for (child in suite.children) {
            emitNode(child)
        }
        getProcessor().onSuiteFinished(TestSuiteFinishedEvent(suite.name))
    }

    private fun emitTest(test: TestNode.Test) {
        getProcessor().onTestStarted(TestStartedEvent(test.name, test.url))
        if (test.failed) {
            getProcessor().onTestFailure(
                TestFailedEvent(test.name, test.failureMessage, test.failureDetails, false, null, null)
            )
        }
        getProcessor().onTestFinished(TestFinishedEvent(test.name, null))
    }

    // ==================== Tree Node Types ====================

    sealed class TestNode {
        abstract val name: String

        data class Suite(
            override val name: String,
            val children: MutableList<TestNode> = mutableListOf()
        ) : TestNode()

        data class Test(
            override val name: String,
            val fullName: String,
            val url: String?,
            var failed: Boolean = false,
            var failureMessage: String = "",
            var failureDetails: String = ""
        ) : TestNode()
    }

    // ==================== Parsing (self-contained, testable) ====================

    class Parser(
        private val testLocations: Map<String, CrystalSpecFileIndexer.TestLocation> = emptyMap(),
        private val onParsingComplete: (() -> Unit)? = null
    ) {

        private enum class ParseState { RUNNING, FAILURES, SUMMARY }

        private var parseState = ParseState.RUNNING
        internal val rootChildren = mutableListOf<TestNode>()
        private val suiteStack = mutableListOf<TestNode.Suite>()
        private var pendingSuiteName: String? = null
        private var currentTestName: String? = null
        private var currentTestFullName: String? = null
        private var hasSeenTests = false
        private var currentFailureName: String? = null
        private var currentFailureMessage = StringBuilder()
        private var failureFileLocation: String? = null
        private var collectingFailureMessage = false
        private var lineBuffer = StringBuilder()

        fun feedText(text: String) {
            lineBuffer.append(text)
            while (true) {
                val newlineIdx = lineBuffer.indexOf('\n')
                if (newlineIdx < 0) break
                val line = lineBuffer.substring(0, newlineIdx).trimEnd('\r')
                lineBuffer.delete(0, newlineIdx + 1)
                parseLine(line)
            }
        }

        fun finish() {
            if (lineBuffer.isNotEmpty()) {
                parseLine(lineBuffer.toString().trimEnd('\r', '\n'))
                lineBuffer.clear()
            }
            finalizeParsing()
        }

        private fun parseLine(line: String) {
            when (parseState) {
                ParseState.RUNNING -> parseRunningLine(line)
                ParseState.FAILURES -> parseFailureLine(line)
                ParseState.SUMMARY -> {}
            }
        }

        private fun parseRunningLine(line: String) {
            if (line.trimStart() == "Failures:") {
                parseState = ParseState.FAILURES
                return
            }

            if (line.trimStart().startsWith("Finished in")) {
                finishCurrentTest()
                finalizeParsing()
                parseState = ParseState.SUMMARY
                onParsingComplete?.invoke()
                return
            }

            if (line.isBlank()) return

            val stripped = line.trimStart()
            val indent = line.length - stripped.length

            if (isDuplicatedName(stripped)) {
                confirmPendingSuite(indent)
                val testName = stripped.substring(0, stripped.length / 2).trimEnd()
                finishCurrentTest()

                val fullName = (suiteStack.map { it.name } + testName).joinToString(" ")
                val location = testLocations[fullName]
                val url = if (location != null) "${CrystalTestLocator.PROTOCOL}://${location.file}:${location.line}" else null

                val testNode = TestNode.Test(testName, fullName, url)
                currentTestName = testName
                currentTestFullName = fullName
                hasSeenTests = true

                getCurrentContainer().add(testNode)
            } else {
                val level = indent / 2

                if (!hasSeenTests && indent == 0) {
                    pendingSuiteName = stripped
                    return
                }

                confirmPendingSuite(indent)

                while (suiteStack.size > level) {
                    suiteStack.removeAt(suiteStack.size - 1)
                }

                val container = getCurrentContainer()
                val suiteNode = TestNode.Suite(stripped)
                suiteStack.add(suiteNode)
                container.add(suiteNode)
            }
        }

        private fun confirmPendingSuite(indent: Int) {
            val pending = pendingSuiteName ?: return
            pendingSuiteName = null

            while (suiteStack.size > 0) {
                suiteStack.removeAt(suiteStack.size - 1)
            }

            val suiteNode = TestNode.Suite(pending)
            suiteStack.add(suiteNode)
            rootChildren.add(suiteNode)
        }

        private fun getCurrentContainer(): MutableList<TestNode> {
            return if (suiteStack.isNotEmpty()) {
                suiteStack.last().children
            } else {
                rootChildren
            }
        }

        private fun finishCurrentTest() {
            currentTestName = null
            currentTestFullName = null
        }

        private fun parseFailureLine(line: String) {
            val trimmed = line.trimStart()

            if (trimmed.startsWith("Finished in")) {
                flushCurrentFailure()
                finalizeParsing()
                parseState = ParseState.SUMMARY
                onParsingComplete?.invoke()
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

            findTestByName(name)?.let { test ->
                test.failed = true
                test.failureMessage = currentFailureMessage.toString().trim()
                test.failureDetails = if (failureFileLocation != null) "${CrystalTestLocator.PROTOCOL}://$failureFileLocation" else ""
            }
        }

        private fun findTestByName(fullName: String): TestNode.Test? {
            for (root in rootChildren) {
                val found = findTestInNode(root, fullName)
                if (found != null) return found
            }
            return null
        }

        private fun findTestInNode(node: TestNode, fullName: String): TestNode.Test? {
            return when (node) {
                is TestNode.Test -> if (node.fullName == fullName) node else null
                is TestNode.Suite -> {
                    for (child in node.children) {
                        val found = findTestInNode(child, fullName)
                        if (found != null) return found
                    }
                    null
                }
            }
        }

        private fun finalizeParsing() {
            if (pendingSuiteName != null) {
                confirmPendingSuite(0)
            }
        }
    }

    companion object {
        fun isDuplicatedName(stripped: String): Boolean {
            if (stripped.length < 3) return false
            val splitRegex = Regex("""^(.+?)\s{2,}\1$""")
            return splitRegex.matches(stripped)
        }

        /**
         * Parse Crystal verbose spec output into a tree structure for testing.
         * Does not require TestConsoleProperties or framework dependencies.
         */
        fun parseForTest(
            output: String,
            testLocations: Map<String, CrystalSpecFileIndexer.TestLocation> = emptyMap()
        ): List<TestNode> {
            val parser = Parser(testLocations)
            parser.feedText(output)
            parser.finish()
            return parser.rootChildren
        }
    }
}
