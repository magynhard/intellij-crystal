package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
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

        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: run {
            LOG.info("No Crystal stdlib could be resolved for '${project.name}'")
            return emptyList()
        }
        val stdlibRoots = CrystalStdlibRoots.enumerate(stdlibRoot)
        LOG.info("Publishing Crystal stdlib library for '${project.name}': ${stdlibRoot.path} as ${stdlibRoots.size} source roots")
        return listOf(CrystalStdlibLibrary(stdlibRoots))
    }

    private companion object {
        val LOG = Logger.getInstance(CrystalStdlibLibraryProvider::class.java)
    }
}

private class CrystalStdlibLibrary(
    private val roots: List<VirtualFile>
) : SyntheticLibrary() {

    override fun getSourceRoots(): Collection<VirtualFile> = roots

    override fun getBinaryRoots(): Collection<VirtualFile> = emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrystalStdlibLibrary) return false
        return roots == other.roots
    }

    override fun hashCode(): Int = roots.hashCode()

    override fun toString(): String = "CrystalStdlibLibrary(${roots.map { it.path }})"
}
