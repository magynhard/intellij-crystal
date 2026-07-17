package de.magynhard.crystal

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import de.magynhard.crystal.psi.CrystalReference
import de.magynhard.crystal.sdk.CrystalStdlibResolver

/**
 * Builds the stdlib symbol cache in the background after the initial indexing pass,
 * so the first stdlib Ctrl+Click is instant instead of blocking ~2 minutes on a
 * one-time VFS scan of ~2154 stdlib files.
 *
 * Runs only on Crystal projects and only after "smart" mode (initial indexing done),
 * on a dedicated low-priority daemon thread so it never starves the indexer or
 * freezes the UI. Each stdlib file is parsed inside its own short read action
 * (see CrystalReference.buildStdlibData), so no long read lock is held.
 */
class CrystalStdlibCacheWarmup : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!isCrystalProject(project)) return

        DumbService.getInstance(project).runWhenSmart {
            val thread = Thread({
                try {
                    CrystalReference.warmStdlibCache()
                } catch (_: Throwable) {
                    // Best-effort; resolution falls back to a lazy build on first use.
                }
            }, "CrystalStdlibCacheWarmup")
            thread.priority = Thread.MIN_PRIORITY
            thread.isDaemon = true
            thread.start()
        }
    }

    private fun isCrystalProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val baseDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(basePath) ?: return false
        if (baseDir.findChild("shard.yml") != null) return true
        if (baseDir.children?.any { it.extension == "cr" } == true) return true
        if (CrystalStdlibResolver.resolveStdlibPath(project) == null) return false
        return baseDir.children?.any { it.isDirectory && it.name == "src" } == true
    }
}
