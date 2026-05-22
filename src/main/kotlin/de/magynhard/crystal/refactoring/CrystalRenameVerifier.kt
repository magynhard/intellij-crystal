package de.magynhard.crystal.refactoring

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import de.magynhard.crystal.CrystalFileType
import java.nio.charset.StandardCharsets

class CrystalRenameVerifier : RefactoringEventListener {

    override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {
        if (refactoringId != "refactoring.rename") return

        val element = afterData?.getUserData(RefactoringEventData.PSI_ELEMENT_KEY) ?: return
        val file = element.containingFile ?: return
        if (file.fileType != CrystalFileType) return

        val project = element.project
        val basePath = project.basePath ?: return
        val virtualFile = file.virtualFile ?: return

        // Run compiler check in background
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            verifyWithCompiler(project, basePath, virtualFile)
        }
    }

    private fun verifyWithCompiler(project: Project, basePath: String, file: VirtualFile) {
        try {
            val commandLine = GeneralCommandLine("crystal", "build", "--no-codegen", file.path)
                .withCharset(StandardCharsets.UTF_8)
                .withWorkDirectory(basePath)

            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(15000)

            if (output.exitCode != 0) {
                val errorMsg = output.stderr.lines().take(5).joinToString("\n")
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Crystal Rename Verification")
                        .createNotification(
                            "Rename may have introduced errors",
                            errorMsg,
                            NotificationType.WARNING
                        )
                        .notify(project)
                }
            }
        } catch (_: Exception) {
            // crystal not available or timeout — silently ignore
        }
    }

    override fun conflictsDetected(refactoringId: String, conflictsData: RefactoringEventData) {}
    override fun undoRefactoring(refactoringId: String) {}
    override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {}
}
