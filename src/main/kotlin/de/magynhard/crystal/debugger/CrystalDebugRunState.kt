package de.magynhard.crystal.debugger

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.platform.dap.DapLaunchArgumentsProvider
import com.intellij.platform.dap.DapStartRequest
import com.intellij.platform.dap.LaunchRequestArguments
import de.magynhard.crystal.run.CrystalRunConfiguration
import java.io.File

/**
 * Run state for Crystal debug sessions.
 * Compiles the target with --debug flag, then provides DAP launch arguments
 * for lldb-dap to debug the resulting binary.
 */
class CrystalDebugRunState(
    environment: ExecutionEnvironment,
    private val configuration: CrystalRunConfiguration
) : CommandLineState(environment), DapLaunchArgumentsProvider {

    private val outputBinary: File by lazy {
        val baseName = File(configuration.filePath).nameWithoutExtension
        File(configuration.workingDirectory, "bin/$baseName")
    }

    override fun isApplicable(executorId: String, profile: RunProfile): Boolean {
        return profile is CrystalRunConfiguration
    }

    override fun getLaunchArguments(project: Project, profile: RunProfile): LaunchRequestArguments {
        buildWithDebugInfo()

        val args = mutableMapOf<String, Any>(
            "program" to outputBinary.absolutePath,
            "cwd" to configuration.workingDirectory
        )

        if (configuration.arguments.isNotBlank()) {
            args["args"] = configuration.arguments.split(" ").filter { it.isNotBlank() }
        }

        if (configuration.environmentVariables.isNotBlank()) {
            val env = mutableMapOf<String, String>()
            for (line in configuration.environmentVariables.split("\n")) {
                val parts = line.trim().split("=", limit = 2)
                if (parts.size == 2) {
                    env[parts[0].trim()] = parts[1].trim()
                }
            }
            if (env.isNotEmpty()) {
                args["env"] = env
            }
        }

        val formattersPath = extractFormattersScript()
        args["initCommands"] = listOf(
            "command script import $formattersPath"
        )

        return LaunchRequestArguments(
            CrystalDebugAdapterId,
            DapStartRequest.Launch,
            args
        )
    }

    override fun startProcess(): ProcessHandler {
        buildWithDebugInfo()

        val commandLine = GeneralCommandLine(outputBinary.absolutePath)
        commandLine.workDirectory = File(configuration.workingDirectory)
        val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    private var built = false

    private fun buildWithDebugInfo() {
        if (built) return

        val outputDir = outputBinary.parentFile
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val buildArgs = mutableListOf<String>().apply {
            add(configuration.crystalPath)
            add("build")
            add("--debug")
            add(configuration.filePath)
            add("-o")
            add(outputBinary.absolutePath)
        }

        val buildCommand = GeneralCommandLine(buildArgs).apply {
            workDirectory = File(configuration.workingDirectory)
            withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        }

        val process = buildCommand.createProcess()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val stderr = process.errorStream.bufferedReader().readText()
            throw ExecutionException("Crystal build failed (exit code $exitCode):\n$stderr")
        }

        built = true
    }

    private fun extractFormattersScript(): String {
        val targetDir = File(PathManager.getTempPath(), "crystal-debugger")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, "crystal_formatters.py")

        // Always overwrite with the latest version from plugin resources.
        // The file is small (~700 lines) so the write cost is negligible,
        // and it ensures updates to formatters take effect immediately.
        val resource = javaClass.getResourceAsStream("/debugger/crystal_formatters.py")
            ?: throw ExecutionException("Crystal debug formatters not found in plugin resources")
        targetFile.writeBytes(resource.readBytes())

        return targetFile.absolutePath
    }
}
