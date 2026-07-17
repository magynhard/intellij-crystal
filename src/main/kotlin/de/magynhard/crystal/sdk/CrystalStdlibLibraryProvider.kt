package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

class CrystalStdlibLibraryProvider : AdditionalLibraryRootsProvider() {
    companion object {
        // Per-project cache of the resolved stdlib library. Keyed by Project; the
        // reference is held strongly, which is acceptable for a dev plugin (one entry
        // per opened project). Exposed for tests.
        private val libCache = java.util.concurrent.ConcurrentHashMap<Project, CrystalStdlibLibrary>()
        private val lock = Any()

        @Suppress("unused")
        internal fun clearCache() = libCache.clear()
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        // Computed at most once per project. MUST NOT touch the workspace model
        // (ModuleManager / ModuleRootManager): this callback is invoked by the platform
        // under the write-intent lock while the model is being (re)computed, and reading
        // module roots from inside it re-enters the model, causing an infinite
        // "workspace model save" loop. Detection stays limited to lightweight, model-free
        // checks (shard.yml / a .cr child in the project base path).
        val cached = libCache[project]
        if (cached != null) return listOf(cached)
        val lib = synchronized(lock) {
            libCache[project] ?: run {
                if (!isCrystalProject(project)) return@run null
                val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: return@run null
                val version = CrystalStdlibResolver.resolveCrystalVersion(project) ?: "unknown"
                CrystalStdlibLibrary(stdlibRoot, version).also { libCache[project] = it }
            }
        }
        return listOfNotNull(lib)
    }

    private fun isCrystalProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return false
        if (baseDir.findChild("shard.yml") != null) return true
        if (baseDir.children?.any { it.extension == "cr" } == true) return true
        return false
    }
}

private class CrystalStdlibLibrary(
    private val root: VirtualFile,
    private val crystalVersion: String
) : SyntheticLibrary() {

    override fun getSourceRoots(): Collection<VirtualFile> = listOf(root)

    override fun getBinaryRoots(): Collection<VirtualFile> = emptyList()

    override fun getExcludedRoots(): Set<VirtualFile> = emptySet()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrystalStdlibLibrary) return false
        // Compare by URL (stable string), not VirtualFile identity, so the platform never
        // sees the library as "changed" between model recomputations.
        return root.url == other.root.url && crystalVersion == other.crystalVersion
    }

    override fun hashCode(): Int = root.url.hashCode() * 31 + crystalVersion.hashCode()

    override fun toString(): String = "CrystalStdlibLibrary(${root.url}, $crystalVersion)"
}
