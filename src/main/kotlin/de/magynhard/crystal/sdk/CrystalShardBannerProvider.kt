package de.magynhard.crystal.sdk

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/**
 * Editor banner over the project-root `shard.yml` while dependencies are
 * missing or outdated, with a direct `shards install` action. Silent for
 * dependency manifests under `lib/` and when everything is installed. VFS reads
 * only, so safe during indexing.
 */
class CrystalShardBannerProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent>? {
        val problems = bannerProblems(project, file) ?: return null
        return Function {
            EditorNotificationPanel().apply {
                text(bannerText(problems))
                createActionLabel("Run shards install") { CrystalShardsInstall.runInstall(project) }
            }
        }
    }

    internal fun bannerProblems(project: Project, file: VirtualFile): List<CrystalShardStatus.Entry>? {
        if (file.name != "shard.yml" || project.isDisposed) return null
        val basePath = project.basePath ?: return null
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return null
        if (file.parent != baseDir) return null
        return CrystalShardStatus.problems(project).takeIf { it.isNotEmpty() }
    }

    private fun bannerText(problems: List<CrystalShardStatus.Entry>): String {
        val names = problems.take(3).joinToString(", ") { it.dependency.name }
        val suffix = if (problems.size > 3) " and ${problems.size - 3} more" else ""
        return "Shard dependencies are missing or outdated: $names$suffix."
    }
}
