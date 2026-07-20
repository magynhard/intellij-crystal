package de.magynhard.crystal.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import de.magynhard.crystal.completion.CrystalCompletionHelper

/**
 * Adds Crystal stdlib as a library with source roots when a Crystal project is opened.
 * This triggers the platform to index the stdlib files automatically.
 *
 * Uses the same pattern as CrystalSpecSourceRootConfigurator (which marks spec/ as test root).
 *
 * ## Auto-migration
 * When the stdlib root is `/usr/lib/crystal` (Crystal ≥ 1.20, flat layout),
 * the library uses [CrystalStdlibRoots] to enumerate only the user-facing
 * stdlib roots — NOT the `compiler/`, `lib_c/`, `llvm/`, etc. subtrees. An
 * existing "Crystal StdLib" library that still has the bare over-broad
 * `/usr/lib/crystal` root is migrated (removed + recreated with the filtered
 * set) on the next project open. This removes ~1150 irrelevant files from
 * the index on plugin update.
 */
class CrystalStdlibSourceRootConfigurator : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Only proceed if this looks like a Crystal project
        if (!isCrystalProject(project)) return

        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: return
        val stdlibRoots = CrystalStdlibRoots.enumerate(stdlibRoot)

        val modules = ModuleManager.getInstance(project).modules
        if (modules.isEmpty()) return

        val module = modules[0]

        ModuleRootModificationUtil.updateModel(module) { model ->
            val existingLibraries = model.moduleLibraryTable.libraries
            val existing = existingLibraries.firstOrNull { it.name == LIBRARY_NAME }

            if (existing == null) {
                // Fresh project — add the filtered multi-root library.
                val library = model.moduleLibraryTable.createLibrary(LIBRARY_NAME)
                val libraryModel = library.modifiableModel
                for (root in stdlibRoots) {
                    libraryModel.addRoot(root, OrderRootType.SOURCES)
                }
                libraryModel.commit()
                return@updateModel
            }

            // Migration: detect an over-broad single-root library (the
            // pre-extension behaviour). Replace it with the filtered set.
            val existingRoots = existing.getFiles(OrderRootType.SOURCES).toList()
            val needsMigration = isOverBroadLibrary(existingRoots, stdlibRoot, stdlibRoots)
            if (needsMigration) {
                model.moduleLibraryTable.removeLibrary(existing)
                val library = model.moduleLibraryTable.createLibrary(LIBRARY_NAME)
                val libraryModel = library.modifiableModel
                for (root in stdlibRoots) {
                    libraryModel.addRoot(root, OrderRootType.SOURCES)
                }
                libraryModel.commit()
            }
        }

        // Warm the top-level method index in the background so the first
        // free-text completion call doesn't trigger a synchronous StubIndex
        // build (which would block the EDT with an "Analyzing project..."
        // modal). `runReadActionInSmartMode` waits for the just-added
        // library to be indexed before reading the index keys.
        warmTopLevelMethodIndex(project)
    }

    /**
     * Triggers a synchronous read of `CrystalTopLevelMethodIndex` keys on a
     * background thread. The StubIndex is lazily built on first access — by
     * forcing that access here (after project open), the index is warm by
     * the time the user types a free-text completion character.
     */
    @Suppress("DEPRECATION") // runReadActionInSmartMode(Runnable) — see body
    private fun warmTopLevelMethodIndex(project: Project) {
        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // `runReadActionInSmartMode` blocks the pooled thread until
                // the just-added StdLib library finishes indexing, then reads
                // the StubIndex keys so the first free-text completion call
                // doesn't trigger a synchronous build (which would block the
                // EDT with an "Analyzing project..." modal). The deprecated
                // overload is safe here: we are on a pooled thread, not nested
                // inside another read action.
                DumbService.getInstance(project).runReadActionInSmartMode {
                    CrystalCompletionHelper.getAllTopLevelMethodNames(project)
                }
            } catch (_: Throwable) {
                // Best-effort warm-up; failure here does not affect the
                // completion flow (it would just delay the first query).
            }
        }
    }

    /**
     * Returns `true` iff [existingRoots] is the legacy over-broad
     * configuration: exactly one root equal to the bare stdlib root
     * (`/usr/lib/crystal` itself) while [filteredRoots] has more than one
     * entry (i.e. the enumerator split it into top-level files +
     * subdirectories).
     */
    private fun isOverBroadLibrary(
        existingRoots: List<VirtualFile>,
        stdlibRoot: VirtualFile,
        filteredRoots: List<VirtualFile>,
    ): Boolean {
        if (existingRoots.size != 1) return false
        if (filteredRoots.size <= 1) return false
        val onlyRoot = existingRoots.first()
        return onlyRoot == stdlibRoot
    }

    private fun isCrystalProject(project: Project): Boolean {
        // Check 1: shard.yml on real filesystem
        val basePath = project.basePath ?: return false
        val shardYml = LocalFileSystem.getInstance().findFileByPath("$basePath/shard.yml")
        if (shardYml != null) return true

        // Check 2: .cr files in any module's content roots (works with light virtual files in tests)
        val modules = ModuleManager.getInstance(project).modules
        for (module in modules) {
            val moduleManager = ModuleRootManager.getInstance(module)
            for (contentRoot in moduleManager.contentRoots) {
                if (contentRoot.children?.any { it.extension == "cr" } == true) return true
            }
        }

        // Check 3: .cr files on real filesystem (for real IDE)
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
        if (baseDir?.children?.any { it.extension == "cr" } == true) return true

        return false
    }

    companion object {
        const val LIBRARY_NAME = "Crystal StdLib"
    }
}
