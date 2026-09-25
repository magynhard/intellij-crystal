package de.magynhard.crystal.sdk

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * User-facing Ameba notifications on the dedicated "Crystal Ameba" group.
 *
 * Runner-level failures (missing binary, broken config) balloon at most once
 * per project and error text so typing never spams: every balloon links to
 * the Crystal settings. [reset] clears the throttle (settings apply).
 * Explicit action outcomes (e.g. `--fix` results) always notify.
 */
object AmebaNotifications {

    const val GROUP_ID = "Crystal Ameba"
    const val SETTINGS_ID = "de.magynhard.crystal.settings"

    fun errorOnce(project: Project, error: String) {
        if (project.isDisposed) return
        val key = error.trim().take(200)
        val shown = synchronized(SHOWN_KEY) {
            project.getUserData(SHOWN_KEY) ?: mutableSetOf<String>().also { project.putUserData(SHOWN_KEY, it) }
        }
        synchronized(shown) {
            if (!shown.add(key)) return
        }
        notify(
            project,
            NotificationType.WARNING,
            "Ameba linter",
            key.ifBlank { "Ameba reported an error." },
            openSettingsAction()
        )
    }

    fun reset(project: Project) {
        project.putUserData(SHOWN_KEY, null)
    }

    fun info(project: Project, title: String, content: String) {
        notify(project, NotificationType.INFORMATION, title, content, null)
    }

    fun error(project: Project, title: String, content: String) {
        notify(project, NotificationType.ERROR, title, content, openSettingsAction())
    }

    fun openSettingsAction(): NotificationAction {
        return object : NotificationAction("Open Crystal settings") {
            override fun actionPerformed(event: AnActionEvent, notification: com.intellij.notification.Notification) {
                val project = event.project ?: return
                notification.expire()
                ShowSettingsUtil.getInstance().showSettingsDialog(project, SETTINGS_ID)
            }
        }
    }

    private fun notify(
        project: Project,
        type: NotificationType,
        title: String,
        content: String,
        action: NotificationAction?
    ) {
        if (project.isDisposed) return
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
        action?.let(notification::addAction)
        notification.notify(project)
    }

    private val SHOWN_KEY = Key.create<MutableSet<String>>("crystal.ameba.notifications.shown")
}
