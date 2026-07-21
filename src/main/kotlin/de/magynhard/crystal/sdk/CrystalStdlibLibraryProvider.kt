package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides the Crystal standard library as an indexed synthetic library.
 * This allows Go to Definition, Parameter Info, and Code Completion
 * to work for stdlib symbols like ENV.fetch, Array#push, HTTP::Client, etc.
 */
class CrystalStdlibLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        // Only provide stdlib if this looks like a Crystal project
        if (!CrystalProjectDetector.isCrystalProject(project)) return emptyList()

        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: return emptyList()
        val version = CrystalStdlibResolver.resolveCrystalVersion(project) ?: "unknown"
        val stdlibRoots = CrystalStdlibRoots.enumerate(stdlibRoot)
        return listOf(CrystalStdlibLibrary(stdlibRoots, version))
    }

}

private class CrystalStdlibLibrary(
    private val roots: List<VirtualFile>,
    private val crystalVersion: String
) : SyntheticLibrary() {

    override fun getSourceRoots(): Collection<VirtualFile> = roots

    override fun getBinaryRoots(): Collection<VirtualFile> = emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrystalStdlibLibrary) return false
        return roots == other.roots && crystalVersion == other.crystalVersion
    }

    override fun hashCode(): Int = roots.hashCode() * 31 + crystalVersion.hashCode()

    override fun toString(): String = "CrystalStdlibLibrary(${roots.map { it.path }}, $crystalVersion)"
}
