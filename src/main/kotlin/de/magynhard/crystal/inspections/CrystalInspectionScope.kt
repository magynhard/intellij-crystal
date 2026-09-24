package de.magynhard.crystal.inspections

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * Project-source gate for inspections.
 *
 * Dependency sources under the project's shards-managed `lib/` directory
 * provide completion and navigation knowledge but carry no inspection
 * diagnostics — the RubyMine node_modules convention. The check is purely
 * path-based so it behaves identically whether or not `lib/` is indexed
 * as project content or library roots; index-based files (stdlib) are
 * excluded through the project file index. Non-physical and
 * location-less files keep their existing behavior so in-memory editors
 * and test fixtures are unaffected.
 */
internal object CrystalInspectionScope {

    fun isProjectSource(file: PsiFile): Boolean {
        if (!file.isPhysical) return true
        val virtualFile = file.virtualFile ?: return true
        if (isUnderShardLibDir(file.project, virtualFile)) return false
        val index = ProjectFileIndex.getInstance(file.project)
        return !index.isExcluded(virtualFile) && !index.isInLibrary(virtualFile)
    }

    private fun isUnderShardLibDir(project: Project, file: VirtualFile): Boolean {
        val basePath = project.basePath ?: return false
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return false
        if (baseDir.findChild("shard.yml") == null) return false
        val libDir = baseDir.findChild("lib") ?: return false
        return VfsUtil.isAncestor(libDir, file, false)
    }
}
