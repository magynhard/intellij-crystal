package de.magynhard.crystal

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * Marks the "spec" directory as a Test Source Root when a Crystal project is opened.
 * This gives the folder the green coloring in the project tree (like RubyMine).
 *
 * Must be idempotent and must not run while the initial indexing pass is in progress:
 * modifying module roots mid-index invalidates and restarts the pass, which (combined
 * with this activity running on every startup) previously livelocked project indexing.
 */
class CrystalSpecSourceRootConfigurator : ProjectActivity {

    override suspend fun execute(project: Project) {
        val basePath = project.basePath ?: return

        // Only proceed if this looks like a Crystal project (has shard.yml + spec/)
        LocalFileSystem.getInstance().findFileByPath("$basePath/shard.yml") ?: return
        val specDir = LocalFileSystem.getInstance().findFileByPath("$basePath/spec") ?: return

        // Defer the root modification until initial indexing has finished, so we never
        // invalidate an in-progress indexing pass.
        DumbService.getInstance(project).runWhenSmart {
            configureSpecRoot(project, specDir)
        }
    }

    private fun configureSpecRoot(project: Project, specDir: VirtualFile) {
        val modules = ModuleManager.getInstance(project).modules
        if (modules.isEmpty()) return

        for (module in modules) {
            ModuleRootModificationUtil.updateModel(module) { model ->
                for (entry in model.contentEntries) {
                    val contentRoot = entry.file ?: continue
                    if (!specDir.path.startsWith(contentRoot.path)) continue

                    // Idempotency: skip if spec is already a test source root.
                    // Compare by PATH (not VirtualFile identity) so the check survives
                    // restarts and re-indexing and we never re-add the same folder.
                    val alreadyMarked = entry.sourceFolders.any { folder ->
                        folder.file?.path == specDir.path &&
                        folder.rootType == JavaSourceRootType.TEST_SOURCE
                    }
                    if (alreadyMarked) return@updateModel

                    entry.addSourceFolder(specDir, JavaSourceRootType.TEST_SOURCE)
                    return@updateModel
                }
            }
        }
    }
}
