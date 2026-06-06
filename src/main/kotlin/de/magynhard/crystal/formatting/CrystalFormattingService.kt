package de.magynhard.crystal.formatting

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.psi.PsiFile
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.sdk.CrystalSettings
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.EnumSet

class CrystalFormattingService : AsyncDocumentFormattingService() {

    override fun getFeatures(): Set<FormattingService.Feature> = EnumSet.noneOf(FormattingService.Feature::class.java)

    override fun canFormat(file: PsiFile): Boolean =
        file.fileType == CrystalFileType

    override fun getNotificationGroupId(): String = "Crystal Formatting"

    override fun getName(): String = "crystal tool format"

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask {
        val ioFile = request.ioFile ?: return object : FormattingTask {
            override fun run() { request.onTextReady(null) }
            override fun cancel(): Boolean = true
        }

        return object : FormattingTask {
            @Volatile
            private var processHandler: CapturingProcessHandler? = null

            override fun run() {
                try {
                    val project = request.context.project
                    val crystalPath = CrystalSettings.getInstance(project).getEffectiveCrystalPath()

                    val commandLine = GeneralCommandLine(crystalPath, "tool", "format", "-")
                        .withCharset(StandardCharsets.UTF_8)
                        .withWorkDirectory(ioFile.parent)

                    val handler = CapturingProcessHandler(commandLine)
                    processHandler = handler

                    val input = request.documentText
                    handler.processInput.use { stream ->
                        stream.write(input.toByteArray(StandardCharsets.UTF_8))
                    }

                    val output = handler.runProcess(5000)

                    if (output.exitCode == 0) {
                        request.onTextReady(output.stdout)
                    } else {
                        val errorMessage = parseFormatError(output.stderr, ioFile.name)
                        request.onError("Crystal Format Error", errorMessage)
                    }
                } catch (e: Exception) {
                    request.onError("Crystal Format Error", e.message ?: "Unknown error")
                }
            }

            override fun cancel(): Boolean {
                processHandler?.destroyProcess()
                return true
            }
        }
    }

    /**
     * Parse stderr from `crystal tool format` into a clear, actionable error message.
     *
     * Expected format: syntax error in 'FILE:LINE:COL': DESCRIPTION
     * The compiler sees stdin, so FILE is always "STDIN".
     */
    internal fun parseFormatError(stderr: String, fileName: String): String {
        if (stderr.isBlank()) {
            return "Formatting failed. Please check that the Crystal compiler is installed and accessible."
        }

        // Match: syntax error in 'STDIN:642:11': invalid regex: ...
        val pattern = Regex("""syntax error in 'STDIN:(\d+):(\d+)':\s*(.+)""")
        val match = pattern.find(stderr)

        return if (match != null) {
            val line = match.groupValues[1]
            val col = match.groupValues[2]
            val description = match.groupValues[3].trim()
            buildString {
                appendLine("Syntax error in $fileName at line $line, column $col")
                appendLine()
                appendLine(description)
                appendLine()
                appendLine("Please fix the syntax error and try formatting again.")
            }.trimEnd()
        } else {
            // Fallback: show the raw stderr if we can't parse it
            stderr.trimEnd()
        }
    }
}
