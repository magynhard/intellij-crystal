package de.magynhard.crystal.ameba

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.AmebaVersion
import de.magynhard.crystal.sdk.AmebaNotifications
import de.magynhard.crystal.sdk.CrystalSettings
import java.util.function.Function
import javax.swing.JComponent

/**
 * Editor banner over the project-root `shard.yml` while it declares the
 * Ameba dev-dependency but no usable `bin/ameba` exists, with a direct
 * opt-in build action. Mirrors [de.magynhard.crystal.sdk.CrystalShardBannerProvider]:
 * silent for manifests elsewhere, undeclared Ameba, or present binaries;
 * VFS reads only, so safe during indexing.
 */
class AmebaBuildBannerProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent>? {
        return when (val state = bannerState(project, file)) {
            BannerState.None -> null
            BannerState.BinaryMissing -> Function {
                EditorNotificationPanel().apply {
                    text("Ameba is declared but bin/ameba is missing.")
                    createActionLabel("Build Ameba") { AmebaBuild.runBuild(project) }
                }
            }
            is BannerState.PinnedOld -> Function {
                EditorNotificationPanel().apply {
                    text(
                        "shard.yml pins Ameba to '${state.requirement}', which is older " +
                            "than the required ${AmebaVersion.MINIMUM}."
                    )
                }
            }
        }
    }

    internal sealed interface BannerState {
        data object None : BannerState
        data object BinaryMissing : BannerState
        data class PinnedOld(val requirement: String) : BannerState
    }

    internal fun bannerState(project: Project, file: VirtualFile): BannerState {
        if (file.name != "shard.yml" || project.isDisposed) return BannerState.None
        val basePath = project.basePath ?: return BannerState.None
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return BannerState.None
        if (file.parent != baseDir) return BannerState.None
        if (AmebaBinary.projectBinaryMissing(project)) return BannerState.BinaryMissing
        // A missing binary is actionable (build it); an old pin is reported
        // only when the binary question is settled. Only evaluable `version:`
        // requirements warn — branch/commit/tag pins cannot be judged.
        val requirement = AmebaBinary.amebaRequirement(project) ?: return BannerState.None
        return if (AmebaVersion.pinAllowsMinimum(requirement) == false) {
            BannerState.PinnedOld(requirement)
        } else {
            BannerState.None
        }
    }

    internal fun bannerNeeded(project: Project, file: VirtualFile): Boolean {
        return bannerState(project, file) == BannerState.BinaryMissing
    }
}

/**
 * Shows a one-time balloon when a project opens with an Ameba declaration
 * but no usable `bin/ameba`, offering the explicit opt-in build. Silent
 * otherwise. Detection is file stats and tiny manifest reads only.
 */
class AmebaBuildStatusActivity : ProjectActivity, DumbAware {

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            if (AmebaBinary.projectBinaryMissing(project)) {
                notifyWithBuildAction(project)
                return@executeOnPooledThread
            }
            // Version warning only when linting is switched on: nobody is
            // nagged about an old binary they never enabled.
            if (!CrystalSettings.getInstance(project).state.amebaEnabled) {
                return@executeOnPooledThread
            }
            when (val problem = AmebaBinary.versionProblem(project)) {
                null -> {}
                else -> AmebaNotifications.errorOnce(project, AmebaVersion.warningText(problem))
            }
        }
    }

    private fun notifyWithBuildAction(project: Project) {
        if (project.isDisposed) return
        val notification = com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup(AmebaNotifications.GROUP_ID)
            .createNotification(
                "Ameba binary missing",
                "shard.yml declares Ameba but bin/ameba is not built.",
                NotificationType.INFORMATION
            )
        notification.addAction(object : NotificationAction("Build Ameba") {
            override fun actionPerformed(event: AnActionEvent, notification: com.intellij.notification.Notification) {
                notification.expire()
                event.project?.let(AmebaBuild::runBuild)
            }
        })
        notification.notify(project)
    }
}
