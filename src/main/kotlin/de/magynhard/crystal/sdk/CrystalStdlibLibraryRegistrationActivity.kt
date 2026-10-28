package de.magynhard.crystal.sdk

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile

/**
 * Attaches the Crystal stdlib synthetic library after the project opened when the
 * platform's own [AdditionalLibraryRootsProvider] query did not produce it.
 *
 * The platform queries the provider once while the project model is still being
 * built and caches an empty result for the session when the query yields
 * nothing. `CrystalStdlibResolver` spawns `crystal env CRYSTAL_PATH` for that
 * query, so any hiccup there (subprocess start failure, slow filesystem, SDK
 * path not resolvable yet) leaves the project without the stdlib for the rest of
 * the session: no stdlib completion, no navigation into stdlib sources, no stdlib
 * in the require closure, and the partial-pool gate in the argument-count
 * inspection cannot prove a pool incomplete because it never sees the stdlib
 * declarations. Re-resolving after startup is cheap (the resolver caches its
 * result) and the reindex is only requested when the roots are genuinely missing
 * from library scope.
 *
 * [execute] runs on a background dispatcher without an ambient read action,
 * while `ProjectFileIndex.isInLibrary` reads the workspace-model file index and
 * asserts read access — the probe therefore takes its own.
 */
class CrystalStdlibLibraryRegistrationActivity : ProjectActivity, DumbAware {

    override suspend fun execute(project: Project) {
        val roots = try {
            missingLibraryRoots(project)
        } catch (error: ProcessCanceledException) {
            throw error
        } catch (error: Throwable) {
            // An uncaught failure here used to abort the activity before it
            // attached anything, leaving the session without the stdlib and
            // without a trace beyond the platform's startup log.
            LOG.warn("Crystal stdlib library recovery failed for '${project.name}'", error)
            return
        } ?: return
        LOG.info(
            "Crystal stdlib library missing from '${project.name}': " +
                "re-attaching ${roots.size} source roots"
        )
        CrystalStdlibIndexRefresher.refresh(project, emptyList(), roots)
    }

    /**
     * The stdlib roots that still need to be attached to the project, or null
     * when there is nothing to do: not a Crystal project, no resolvable stdlib,
     * or the roots are already part of library scope.
     *
     * [inLibrary] is the library-scope probe, injectable because the light test
     * project reports every content file as library-scoped. The default probe
     * runs on a background thread during project startup and must supply its own
     * read access.
     */
    internal fun missingLibraryRoots(
        project: Project,
        inLibrary: (VirtualFile) -> Boolean = { file -> inLibraryScope(project, file) },
    ): List<VirtualFile>? {
        val roots = stdlibSourceRoots(project) ?: return null
        return if (roots.all { it.isValid && inLibrary(it) }) null else roots
    }

    /**
     * The stdlib source roots the provider would publish, or null when the
     * project is not a Crystal project or no stdlib is resolvable. Shared with
     * [CrystalStdlibLibraryProvider] so both paths agree on the root set.
     */
    internal fun stdlibSourceRoots(project: Project): List<VirtualFile>? {
        if (!CrystalProjectDetector.isCrystalProject(project)) return null
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: return null
        return CrystalStdlibRoots.enumerate(stdlibRoot)
    }

    private fun inLibraryScope(project: Project, file: VirtualFile): Boolean =
        ReadAction.computeBlocking<Boolean, RuntimeException> { ProjectFileIndex.getInstance(project).isInLibrary(file) }

    private companion object {
        private val LOG = Logger.getInstance(CrystalStdlibLibraryRegistrationActivity::class.java)
    }
}