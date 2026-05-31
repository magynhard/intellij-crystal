package de.magynhard.crystal.run

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope

/**
 * Locates Crystal spec test sources from file:line references in test output.
 * Enables clicking on failure locations to navigate to source.
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
        val parts = path.split(":")
        val filePath = parts[0]
        val line = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return emptyList()
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return emptyList()

        return listOf(PsiLocation(psiFile))
    }
}
