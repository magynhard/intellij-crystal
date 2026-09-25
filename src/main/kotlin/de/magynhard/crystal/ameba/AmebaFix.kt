package de.magynhard.crystal.ameba

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.AmebaNotifications
import de.magynhard.crystal.sdk.CrystalSettings
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Explicit file-level `ameba --fix` action, shared by the editor intention
 * and batch-mode quickfixes.
 *
 * Never runs silently or on typing: it starts only from an explicit user
 * action, saves the editor buffer first so the fix applies to the current
 * content, then runs as a cancellable background task and refreshes the
 * file (diagnostics re-evaluate through the normal daemon pass).
 *
 * Success is judged by file change, not exit code: `ameba --fix` re-reports
 * corrected issues and exits non-zero even when it fixed everything.
 */
object AmebaFix {

    /** Outcome of a `--fix` run for notification purposes. */
    internal enum class FixOutcome {
        /** Exit 0, file untouched: nothing to correct. Silent. */
        CLEAN,
        /** File changed: corrections applied (remaining issues reappear as diagnostics). */
        FIXED,
        /** Non-zero exit with error output and no file change: real failure, show output. */
        FAILED,
        /** Non-zero exit, file untouched, no error output: ran fine, nothing
         * correctable (e.g. syntax errors). Inform without dumping progress. */
        UNCHANGED
    }

    fun runOnFile(project: Project, file: VirtualFile) {
        if (project.isDisposed || !file.isInLocalFileSystem || file.isDirectory) return
        object : Task.Backgroundable(project, "Running ameba --fix on ${file.name}", true) {
            override fun run(indicator: ProgressIndicator) {
                runFixCommand(project, file, indicator)
            }
        }.queue()
    }

    internal fun classifyFixOutcome(exitCode: Int, fileChanged: Boolean, hasErrorOutput: Boolean): FixOutcome {
        if (fileChanged) return FixOutcome.FIXED
        if (exitCode == 0) return FixOutcome.CLEAN
        if (hasErrorOutput) return FixOutcome.FAILED
        return FixOutcome.UNCHANGED
    }

    private fun runFixCommand(project: Project, file: VirtualFile, indicator: ProgressIndicator) {
        val binary = AmebaBinary.resolve(project)
        if (binary == null) {
            AmebaNotifications.error(project, "Cannot run ameba --fix", "No usable Ameba binary is configured.")
            return
        }
        ApplicationManager.getApplication().invokeAndWait {
            saveDocument(file)
        }
        val basePath = project.basePath
        val args = mutableListOf(binary.path, "--fix")
        val config = CrystalSettings.getInstance(project).state.amebaConfigPath
        if (config.isNotBlank()) {
            args.add("--config")
            args.add(config)
        }
        args.add(file.path)
        val commandLine = GeneralCommandLine(args)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(basePath)
        val before = readBytes(file)
        val result = try {
            CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
        } catch (_: Exception) {
            AmebaNotifications.error(project, "Cannot run ameba --fix", "Failed to start '${binary.path}'.")
            return
        }
        val after = readBytes(file)
        val changed = before != null && after != null && !before.contentEquals(after)
        // Progress output goes to stdout; only stderr carries real errors.
        // An unchanged file with empty stderr means the run worked but had
        // nothing correctable (e.g. syntax errors) — no output to dump.
        val errorOutput = stripAnsi(result.stderr).trim()
        ApplicationManager.getApplication().invokeLater {
            VfsUtil.markDirtyAndRefresh(false, true, true, File(file.path))
            DaemonCodeAnalyzer.getInstance(project).restart()
            when (classifyFixOutcome(result.exitCode, changed, errorOutput.isNotEmpty())) {
                FixOutcome.CLEAN -> {}
                FixOutcome.FIXED -> AmebaNotifications.info(
                    project,
                    "ameba --fix corrected ${file.name}",
                    "Remaining issues, if any, are reported as diagnostics."
                )
                FixOutcome.FAILED -> {
                    val output = errorOutput.take(2000)
                    AmebaNotifications.error(
                        project,
                        "ameba --fix failed",
                        output.ifBlank { "Exit code ${result.exitCode}." }
                    )
                }
                FixOutcome.UNCHANGED -> AmebaNotifications.info(
                    project,
                    "ameba --fix made no changes to ${file.name}",
                    "Uncorrectable issues need manual fixing – see the editor diagnostics."
                )
            }
        }
    }

    private fun readBytes(file: VirtualFile): ByteArray? {
        return try {
            File(file.path).takeIf { it.isFile }?.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    private fun saveDocument(file: VirtualFile) {
        val manager = FileDocumentManager.getInstance()
        val document = manager.getDocument(file) ?: return
        if (manager.isDocumentUnsaved(document)) {
            manager.saveDocument(document)
        }
    }
}

/**
 * Editor intention and batch quickfix wiring [AmebaFix.runOnFile] to a
 * problem. Attached to every Ameba annotation; the action itself stays
 * explicit (Alt+Enter / Inspect-Code fix click).
 */class AmebaFileFix : LocalQuickFix, IntentionAction {

    override fun getName(): String = "Run ameba --fix on this file"

    override fun getFamilyName(): String = "Ameba"

    override fun getText(): String = name

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: com.intellij.openapi.editor.Editor?, file: PsiFile): Boolean {
        // --fix rewrites the file through generated-code positions, which is
        // only sound for plain Crystal sources; ECR templates are excluded.
        return file.fileType == de.magynhard.crystal.CrystalFileType
    }

    override fun invoke(project: Project, editor: com.intellij.openapi.editor.Editor?, file: PsiFile) {
        val virtualFile = file.virtualFile ?: return
        AmebaFix.runOnFile(project, virtualFile)
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement?.containingFile ?: return
        if (file.fileType != de.magynhard.crystal.CrystalFileType) return
        val virtualFile = file.virtualFile ?: return
        AmebaFix.runOnFile(project, virtualFile)
    }
}

/**
 * Strips ANSI color escapes from tool output before it is shown in
 * notifications (Ameba colorizes even piped output).
 */
internal fun stripAnsi(text: String): String =
    text.replace(Regex("\u001B\\[[0-9;]*m"), "")
