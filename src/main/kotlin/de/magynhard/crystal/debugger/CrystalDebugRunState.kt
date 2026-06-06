package de.magynhard.crystal.debugger

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.PathManager
import com.intellij.platform.dap.DapLaunchArgumentsProvider
import com.intellij.platform.dap.DapStartRequest
import com.intellij.platform.dap.DebugAdapterId
import de.magynhard.crystal.run.CrystalCommand
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

    override fun startProcess(): ProcessHandler {
        // Build the Crystal program with debug info first
        buildWithDebugInfo()

        // The DAP framework needs a process handler, but lldb-dap will launch the actual program.
        // We create a handler for the built binary that won't actually be started by us.
        val commandLine = GeneralCommandLine(outputBinary.absolutePath)
        commandLine.workDirectory = File(configuration.workingDirectory)
        val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    override val adapterId: DebugAdapterId get() = CrystalDebugAdapterId

    override val request: DapStartRequest get() = DapStartRequest.Launch

    override fun arguments(): Map<String, Any> {
        buildWithDebugInfo()

        val args = mutableMapOf<String, Any>(
            "program" to outputBinary.absolutePath,
            "cwd" to configuration.workingDirectory
        )

        // Pass program arguments
        if (configuration.arguments.isNotBlank()) {
            args["args"] = configuration.arguments.split(" ").filter { it.isNotBlank() }
        }

        // Environment variables
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

        // Load Crystal formatters for readable variable display
        val formattersPath = extractFormattersScript()
        args["initCommands"] = listOf(
            "command script import $formattersPath"
        )

        return args
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
