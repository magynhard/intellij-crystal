package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides the Crystal standard library as an indexed synthetic library.
 * This allows Go to Definition, Parameter Info, and Code Completion
 * to work for stdlib symbols like ENV.fetch, Array#push, HTTP::Client, etc.
 */
class CrystalStdlibLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        // Only provide stdlib if this looks like a Crystal project
        if (!isCrystalProject(project)) return emptyList()

        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: return emptyList()
        val version = CrystalStdlibResolver.resolveCrystalVersion(project) ?: "unknown"
        return listOf(CrystalStdlibLibrary(stdlibRoot, version))
    }

    private fun isCrystalProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false

        // Check 1: shard.yml on real filesystem
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
        if (baseDir?.findChild("shard.yml") != null) return true

        // Check 2: .cr files on real filesystem
        if (baseDir?.children?.any { it.extension == "cr" } == true) return true

        // Check 3: .cr files in any module's content roots (works with light virtual files in tests)
        val modules = com.intellij.openapi.module.ModuleManager.getInstance(project).modules
        for (module in modules) {
            val moduleManager = com.intellij.openapi.roots.ModuleRootManager.getInstance(module)
            for (contentRoot in moduleManager.contentRoots) {
                if (contentRoot.children?.any { it.extension == "cr" } == true) return true
            }
        }

        return false
    }
}

private class CrystalStdlibLibrary(
    private val root: VirtualFile,
    private val crystalVersion: String
) : SyntheticLibrary() {

    override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)

    override fun getBinaryRoots(): Collection<VirtualFile> = emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrystalStdlibLibrary) return false
        return root == other.root && crystalVersion == other.crystalVersion
    }

    override fun hashCode(): Int = root.hashCode() * 31 + crystalVersion.hashCode()

    override fun toString(): String = "CrystalStdlibLibrary(${root.path}, $crystalVersion)"
}
