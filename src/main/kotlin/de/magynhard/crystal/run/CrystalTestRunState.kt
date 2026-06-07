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

/**
 * Run state for Crystal spec that integrates with IntelliJ's test runner UI.
 * Launches "crystal spec -v --no-color [file[:line]]" and parses output in real-time
 * via CrystalTestEventsConverter (registered through CrystalTestConsoleProperties).
 */
class CrystalTestRunState(
    environment: ExecutionEnvironment,
    private val configuration: CrystalRunConfiguration
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val commandLine = buildCommandLine()
        val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()

        // Build test location index for navigation
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

        val properties = CrystalTestConsoleProperties(configuration, executor, testLocations)
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
                    // Pass directory path — crystal spec runs all *_spec.cr files recursively
                    addParameter(configuration.filePath)
                } else if (configuration.specLine > 0) {
                    addParameter("${configuration.filePath}:${configuration.specLine}")
                } else {
                    addParameter(configuration.filePath)
                }
            }

            addParameter("-v")
            addParameter("--no-color")

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
