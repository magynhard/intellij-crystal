package de.magynhard.crystal.formatting

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.psi.PsiFile
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.sdk.CrystalSettings
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
                        request.onError(
                            "Crystal Format Error",
                            output.stderr.ifBlank { "Formatting failed with exit code ${output.exitCode}" }
                        )
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
}
