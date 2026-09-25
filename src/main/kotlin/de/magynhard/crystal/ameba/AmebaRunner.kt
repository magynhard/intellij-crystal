package de.magynhard.crystal.ameba

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.progress.ProgressManager
import java.nio.charset.StandardCharsets

/**
 * Executes the Ameba binary for a single (possibly unsaved) file and parses
 * the reported issues.
 *
 * Invocation mirrors the editor-integration pattern from the Ameba project
 * (`ameba --format json --stdin-filename <path> <file>` with the document
 * text on STDIN): the positional file argument keeps project-glob scanning
 * off, `--stdin-filename` makes issues addressable by the real path, and
 * STDIN carries the current buffer. Issues for any other path are dropped by
 * the parser. Blocking — call only from background threads (`doAnnotate`).
 * Never throws: failures surface as [Result.error].
 */
object AmebaRunner {

    const val TIMEOUT_MS = 30_000L

    data class Request(
        val binaryPath: String,
        val configPath: String?,
        val workDirectory: String?,
        val stdinFileName: String,
        val targetFilePath: String,
        val documentText: String,
        val timeoutMs: Long = TIMEOUT_MS
    )

    data class Result(
        val issues: List<AmebaIssue>,
        /** Human-readable failure (binary, config, timeout), null on success. */
        val error: String?
    )

    fun run(request: Request): Result {
        ProgressManager.checkCanceled()
        val commandLine = buildCommand(request, "json")
        val output = try {
            execute(commandLine, request)
        } catch (e: Exception) {
            if (e is com.intellij.openapi.progress.ProcessCanceledException) throw e
            return Result(emptyList(), "Cannot run Ameba '${request.binaryPath}': ${e.message ?: "unknown error"}")
        } ?: run {
            return Result(emptyList(), "Ameba timed out after ${request.timeoutMs}ms.")
        }
        ProgressManager.checkCanceled()
        if (looksLikeUnknownFormatter(output.stderr)) {
            // Binary predates JSON support: one retry with flycheck output.
            val fallback = try {
                execute(buildCommand(request, "flycheck"), request)
            } catch (e: Exception) {
                if (e is com.intellij.openapi.progress.ProcessCanceledException) throw e
                return Result(emptyList(), "Cannot run Ameba '${request.binaryPath}': ${e.message ?: "unknown error"}")
            } ?: return Result(emptyList(), "Ameba timed out after ${request.timeoutMs}ms.")
            ProgressManager.checkCanceled()
            return Result(
                AmebaOutputParser.parseFlycheck(fallback.stdout, request.stdinFileName),
                errorForExit(fallback.exitCode, fallback.stderr, request)
            )
        }
        return Result(
            AmebaOutputParser.parseJson(output.stdout, request.stdinFileName),
            errorForExit(output.exitCode, output.stderr, request)
        )
    }

    private data class Output(val stdout: String, val stderr: String, val exitCode: Int)

    private fun buildCommand(request: Request, format: String): GeneralCommandLine {
        val args = mutableListOf(
            request.binaryPath,
            "--format", format,
            "--stdin-filename", request.stdinFileName
        )
        if (!request.configPath.isNullOrBlank()) {
            args.add("--config")
            args.add(request.configPath)
        }
        args.add(request.targetFilePath)
        return GeneralCommandLine(args)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(request.workDirectory)
    }

    private fun execute(commandLine: GeneralCommandLine, request: Request): Output? {
        val handler = CapturingProcessHandler(commandLine)
        handler.processInput.use { stream ->
            stream.write(request.documentText.toByteArray(StandardCharsets.UTF_8))
        }
        val result = handler.runProcess(request.timeoutMs.toInt(), true)
        if (result.isTimeout) return null
        return Output(result.stdout, result.stderr, result.exitCode)
    }

    private fun looksLikeUnknownFormatter(stderr: String): Boolean {
        val lowered = stderr.lowercase()
        return "unknown formatter" in lowered && "json" in lowered
    }

    /**
     * Ameba exits non-zero when issues were found — that is success with
     * diagnostics, not a failure. Only real errors (no JSON produced at all
     * or explicit error text) surface.
     */
    private fun errorForExit(exitCode: Int, stderr: String, request: Request): String? {
        if (exitCode == 0) return null
        val trimmed = stripAnsi(stderr).trim().take(2000)
        if (trimmed.isEmpty()) return null
        if (looksLikeUnknownFormatter(trimmed)) return null
        return "Ameba '${request.binaryPath}' reported an error: $trimmed"
    }
}
