package de.magynhard.crystal.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import java.io.File

class CrystalRunState(
    environment: ExecutionEnvironment,
    private val configuration: CrystalRunConfiguration
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val commandLine = GeneralCommandLine().apply {
            exePath = configuration.crystalPath
            addParameter(configuration.command.command)

            if (configuration.filePath.isNotBlank()) {
                addParameter(configuration.filePath)
            }

            if (configuration.arguments.isNotBlank()) {
                // For spec/run, arguments after -- are passed to the program
                if (configuration.command == CrystalCommand.RUN) {
                    addParameter("--")
                }
                addParameters(configuration.arguments.split(" ").filter { it.isNotBlank() })
            }

            workDirectory = File(configuration.workingDirectory)

            // Parse environment variables (KEY=VALUE per line)
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

        val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}
