package de.magynhard.crystal.run

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import java.io.File
import java.nio.file.Files

/**
 * Run state for Crystal spec that integrates with IntelliJ's test runner UI.
 * Launches "crystal spec -v --no-color --junit_output <file> [file[:line]]"
 * and parses output in real-time via CrystalTestEventsConverter.
 * After tests complete, JUnit XML is parsed for accurate per-test timing.
 */
class CrystalTestRunState(
    environment: ExecutionEnvironment,
    private val configuration: CrystalRunConfiguration
) : CommandLineState(environment) {

    private var junitOutputFile: File? = null

    override fun startProcess(): ProcessHandler {
        val commandLine = buildCommandLine()
        val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        junitOutputFile = Files.createTempFile("crystal_junit", ".xml").toFile()
        val processHandler = startProcess()

        // Clear cache to ensure fresh indexing after file modifications
        CrystalSpecFileIndexer.clearCache()

        val testLocations = if (configuration.filePath.isNotBlank()) {
            val file = java.io.File(configuration.filePath)
            if (file.isDirectory) {
                CrystalSpecFileIndexer.getTestLocationsForDirectory(configuration.filePath)
            } else {
                CrystalSpecFileIndexer.getTestLocations(configuration.filePath)
            }
        } else {
            emptyMap()
        }

        val properties = CrystalTestConsoleProperties(configuration, executor, testLocations, junitOutputFile!!)
        val console = SMTestRunnerConnectionUtil.createAndAttachConsole(
            "CrystalSpec",
            processHandler,
            properties
        )
        return DefaultExecutionResult(console, processHandler)
    }

    private fun buildCommandLine(): GeneralCommandLine {
        return GeneralCommandLine().apply {
            exePath = configuration.crystalPath
            addParameter("spec")

            if (configuration.filePath.isNotBlank()) {
                val file = java.io.File(configuration.filePath)
                if (file.isDirectory) {
                    addParameter(configuration.filePath)
                } else if (configuration.specLine > 0) {
                    addParameter("${configuration.filePath}:${configuration.specLine}")
                } else {
                    addParameter(configuration.filePath)
                }
            }

            addParameter("-v")
            addParameter("--no-color")

            val junitFile = junitOutputFile
            if (junitFile != null) {
                addParameter("--junit_output")
                addParameter(junitFile.absolutePath)
            }

            if (configuration.arguments.isNotBlank()) {
                addParameters(configuration.arguments.split(" ").filter { it.isNotBlank() })
            }

            workDirectory = File(configuration.workingDirectory)

            if (configuration.environmentVariables.isNotBlank()) {
                for (line in configuration.environmentVariables.split("\n")) {
                    val parts = line.trim().split("=", limit = 2)
                    if (parts.size == 2) {
                        environment.put(parts[0].trim(), parts[1].trim())
                    }
                }
            }

            withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        }
    }
}
