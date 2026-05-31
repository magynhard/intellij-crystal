package de.magynhard.crystal

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * Marks the "spec" directory as a Test Source Root when a Crystal project is opened.
 * This gives the folder the green coloring in the project tree (like RubyMine).
 */
class CrystalSpecSourceRootConfigurator : ProjectActivity {

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return
        val specDir = LocalFileSystem.getInstance().findFileByPath("$basePath/spec") ?: return

        // Only proceed if this looks like a Crystal project (has shard.yml or .cr files)
        val shardYml = LocalFileSystem.getInstance().findFileByPath("$basePath/shard.yml")
        if (shardYml == null) return

        val modules = ModuleManager.getInstance(project).modules
        if (modules.isEmpty()) return

        val module = modules[0]

        ModuleRootModificationUtil.updateModel(module) { model ->
            val contentEntries = model.contentEntries
            for (entry in contentEntries) {
                // Check if spec is already marked as a source root
                val existingRoots = entry.sourceFolders
                val alreadyMarked = existingRoots.any { it.file == specDir }
                if (alreadyMarked) return@updateModel

                // Check if the spec dir is under this content entry
                val contentRoot = entry.file ?: continue
                if (specDir.path.startsWith(contentRoot.path)) {
                    entry.addSourceFolder(specDir, JavaSourceRootType.TEST_SOURCE)
                    return@updateModel
                }
            }
        }
    }
}
