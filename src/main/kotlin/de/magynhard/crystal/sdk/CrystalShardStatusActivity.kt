package de.magynhard.crystal.sdk

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Shows a one-time balloon when a project opens with missing or outdated
 * shard dependencies, offering a direct `shards install` run. Silent when
 * everything is installed; VFS reads only, so safe during indexing.
 */
class CrystalShardStatusActivity : ProjectActivity, DumbAware {

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            val problems = ReadAction.compute<List<CrystalShardStatus.Entry>, RuntimeException> {
                if (project.isDisposed) emptyList() else CrystalShardStatus.problems(project)
            }
            if (problems.isEmpty()) return@executeOnPooledThread
            val names = problems.take(3).joinToString(", ") { it.dependency.name }
            val suffix = if (problems.size > 3) " and ${problems.size - 3} more" else ""
            CrystalShardsInstall.notify(
                project,
                NotificationType.WARNING,
                "Shard dependencies need attention",
                "Missing or outdated dependencies: $names$suffix.",
                object : NotificationAction("Run shards install") {
                    override fun actionPerformed(event: AnActionEvent, notification: com.intellij.notification.Notification) {
                        notification.expire()
                        event.project?.let(CrystalShardsInstall::runInstall)
                    }
                }
            )
        }
    }
}
