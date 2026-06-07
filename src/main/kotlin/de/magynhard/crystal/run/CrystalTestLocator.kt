package de.magynhard.crystal.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope

/**
 * Locates Crystal spec test sources from file:line references in test output.
 * Enables clicking on failure locations or double-clicking on any test to navigate to source.
 *
 * URL format: crystal_spec://file_path:line_number
 */
class CrystalTestLocator : SMTestLocator {

    companion object {
        const val PROTOCOL = "crystal_spec"
        val INSTANCE = CrystalTestLocator()
    }

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope
    ): List<Location<*>> {
        if (protocol != PROTOCOL) return emptyList()

        // Path format: "file_path:line_number"
        val lastColon = path.lastIndexOf(':')
        if (lastColon < 0) return emptyList()

        val filePath = path.substring(0, lastColon)
        val line = path.substring(lastColon + 1).toIntOrNull() ?: 0

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return emptyList()
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return emptyList()

        // Navigate to the specific line (1-based line number → 0-based offset)
        if (line > 0) {
            val document: Document? = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                val offset = document.getLineStartOffset(line - 1)
                val element = psiFile.findElementAt(offset)
                if (element != null) {
                    return listOf(PsiLocation(element))
                }
            }
        }

        return listOf(PsiLocation(psiFile))
    }
}
