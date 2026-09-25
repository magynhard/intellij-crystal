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
 */
object AmebaFix {

    fun runOnFile(project: Project, file: VirtualFile) {
        if (project.isDisposed || !file.isInLocalFileSystem || file.isDirectory) return
        object : Task.Backgroundable(project, "Running ameba --fix on ${file.name}", true) {
            override fun run(indicator: ProgressIndicator) {
                runFixCommand(project, file, indicator)
            }
        }.queue()
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
        val result = try {
            CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
        } catch (_: Exception) {
            AmebaNotifications.error(project, "Cannot run ameba --fix", "Failed to start '${binary.path}'.")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            VfsUtil.markDirtyAndRefresh(false, true, true, File(file.path))
            DaemonCodeAnalyzer.getInstance(project).restart()
            if (result.exitCode != 0) {
                val output = (result.stderr + result.stdout).trim().take(2000)
                AmebaNotifications.error(
                    project,
                    "ameba --fix failed",
                    output.ifBlank { "Exit code ${result.exitCode}." }
                )
            }
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
 */
class AmebaFileFix : LocalQuickFix, IntentionAction {

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
