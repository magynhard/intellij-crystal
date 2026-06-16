package de.magynhard.crystal.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Adds Crystal stdlib as a library with source roots when a Crystal project is opened.
 * This triggers the platform to index the stdlib files automatically.
 *
 * Uses the same pattern as CrystalSpecSourceRootConfigurator (which marks spec/ as test root).
 */
class CrystalStdlibSourceRootConfigurator : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Only proceed if this looks like a Crystal project
        if (!isCrystalProject(project)) return

        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: return

        val modules = ModuleManager.getInstance(project).modules
        if (modules.isEmpty()) return

        val module = modules[0]

        ModuleRootModificationUtil.updateModel(module) { model ->
            // Check if stdlib library already exists
            val existingLibraries = model.moduleLibraryTable.libraries
            val alreadyAdded = existingLibraries.any { it.name == LIBRARY_NAME }
            if (alreadyAdded) return@updateModel

            // Add stdlib as a module library with source roots
            val library = model.moduleLibraryTable.createLibrary(LIBRARY_NAME)
            val libraryModel = library.modifiableModel
            libraryModel.addRoot(stdlibRoot, OrderRootType.SOURCES)
            libraryModel.commit()
        }
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
        private const val LIBRARY_NAME = "Crystal StdLib"
    }
}
