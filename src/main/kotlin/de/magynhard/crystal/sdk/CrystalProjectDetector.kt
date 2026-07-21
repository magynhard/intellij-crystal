package de.magynhard.crystal.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem

internal object CrystalProjectDetector {

    fun isCrystalProject(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)
        if (baseDir?.findChild("shard.yml") != null) return true
        if (baseDir?.children?.any { it.extension == "cr" } == true) return true

        return ModuleManager.getInstance(project).modules.any { module ->
            ModuleRootManager.getInstance(module).contentRoots.any { contentRoot ->
                contentRoot.children.any { it.extension == "cr" }
            }
        }
    }
}
