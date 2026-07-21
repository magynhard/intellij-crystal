package de.magynhard.crystal.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.startup.ProjectActivity

class CrystalLegacyStdlibExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {

    override fun getExcludeUrlsForProject(): Array<String> {
        return ModuleManager.getInstance(project).modules
            .asSequence()
            .flatMap { ModuleRootManager.getInstance(it).orderEntries.asSequence() }
            .filterIsInstance<LibraryOrderEntry>()
            .filter { it.isModuleLevel && it.libraryName == LEGACY_LIBRARY_NAME }
            .flatMap { it.library?.getFiles(OrderRootType.SOURCES)?.asSequence() ?: emptySequence() }
            .flatMap { CrystalStdlibRoots.legacyExclusions(it).asSequence() }
            .distinctBy { it.url }
            .map { it.url }
            .toList()
            .toTypedArray()
    }
}

class CrystalLegacyStdlibCleanupActivity : ProjectActivity, DumbAware {

    override suspend fun execute(project: Project) {
        for (module in ModuleManager.getInstance(project).modules) {
            val hasLegacyLibrary = ModuleRootManager.getInstance(module).orderEntries.any { entry ->
                entry is LibraryOrderEntry && entry.isModuleLevel && entry.libraryName == LEGACY_LIBRARY_NAME
            }
            if (!hasLegacyLibrary) continue

            ModuleRootModificationUtil.updateModel(module) { model ->
                model.moduleLibraryTable.libraries
                    .filter { it.name == LEGACY_LIBRARY_NAME }
                    .forEach(model.moduleLibraryTable::removeLibrary)
            }
        }
    }
}

private const val LEGACY_LIBRARY_NAME = "Crystal StdLib"
