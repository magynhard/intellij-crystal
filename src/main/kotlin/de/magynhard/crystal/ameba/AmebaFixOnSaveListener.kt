package de.magynhard.crystal.ameba

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.inspections.CrystalInspectionScope
import de.magynhard.crystal.sdk.CrystalSettings

/**
 * Opt-in `ameba --fix` on save, armed by the "Run ameba --fix on save"
 * checkbox in the Crystal settings (default off).
 *
 * `afterDocumentSaved` fires once the save has landed, so the fix always
 * runs on saved content — never on a stale buffer, never blocking the save
 * itself (the EDT handler only gates; the fix runs as a background task).
 * Guards, in order: settings switch, local `.cr` file, clean buffer (the
 * user may have kept typing — their unsaved buffer wins and the next save
 * retries), project-source scope. `.ecr` is excluded: `--fix` through
 * generated-code positions proved unreliable on templates (verified against
 * 1.7.0), while read-only diagnostics still flow for them.
 *
 * No loops by construction: the fix writes the file on disk, whose refresh
 * reloads the document without producing another save event — and a second
 * run finds nothing left to correct anyway.
 */
class AmebaFixOnSaveListener : FileDocumentManagerListener {

    override fun afterDocumentSaved(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return
        if (!shouldFixOnSave(project, file)) return
        fixAfterSave(project, file)
    }

    internal fun shouldFixOnSave(project: Project, file: VirtualFile): Boolean {
        if (project.isDisposed) return false
        val state = CrystalSettings.getInstance(project).state
        if (!state.amebaEnabled || !state.amebaFixOnSave) return false
        if (!file.isInLocalFileSystem || file.isDirectory) return false
        return file.fileType == CrystalFileType
    }

    private fun fixAfterSave(project: Project, file: VirtualFile) {
        if (!shouldFixOnSave(project, file)) return
        val manager = FileDocumentManager.getInstance()
        val current = manager.getDocument(file) ?: return
        if (manager.isDocumentUnsaved(current)) return
        val psiFile: PsiFile? = ApplicationManager.getApplication().runReadAction<PsiFile?> {
            PsiManager.getInstance(project).findFile(file)
        }
        if (psiFile == null || !CrystalInspectionScope.isProjectSource(psiFile)) return
        AmebaFix.runOnFile(project, file)
    }
}
