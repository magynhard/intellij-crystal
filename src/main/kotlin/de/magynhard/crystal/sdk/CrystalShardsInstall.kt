package de.magynhard.crystal.sdk

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared `shards install` execution for the balloon, the editor banner,
 * and the shard.yml quickfix.
 *
 * The binary resolves as a sibling of the configured `crystal` executable
 * with a `PATH` fallback; a missing binary only ever produces an error
 * message. Runs execute once per project as cancellable background tasks;
 * success refreshes the project tree (banners and markers re-evaluate),
 * failure reports truncated process output.
 */
internal object CrystalShardsInstall {

    fun shardsExecutable(project: Project): File? =
        findShardsExecutable(
            CrystalSettings.getInstance(project).getEffectiveCrystalPath(),
            ::findOnPath
        )

    private fun findOnPath(name: String): File? {
        return try {
            val cmd = if (SystemInfo.isWindows) arrayOf("where", name) else arrayOf("which", name)
            val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && output.isNotBlank()) {
                File(output.lines().first().trim()).takeIf { it.canExecute() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    internal fun findShardsExecutable(crystalPath: String, pathLookup: (String) -> File?): File? {
        val names = if (SystemInfo.isWindows) listOf("shards.exe", "shards") else listOf("shards")
        val siblingDir = File(crystalPath).parentFile?.takeIf { it.isDirectory }
        for (name in names) {
            siblingDir?.let { File(it, name) }
                ?.takeIf { it.isFile && it.canExecute() }
                ?.let { return it }
        }
        for (name in names) {
            val found = try {
                pathLookup(name)
            } catch (_: Exception) {
                null
            }
            found?.takeIf { it.isFile && it.canExecute() }?.let { return it }
        }
        return null
    }

    fun runInstall(project: Project) {
        if (project.isDisposed) return
        val running = synchronized(RUNNING_KEY) {
            project.getUserData(RUNNING_KEY) ?: AtomicBoolean(false).also { project.putUserData(RUNNING_KEY, it) }
        }
        if (!running.compareAndSet(false, true)) return
        object : Task.Backgroundable(project, "Installing Crystal shards", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    runInstallCommand(project, indicator)
                } finally {
                    running.set(false)
                }
            }
        }.queue()
    }

    private fun runInstallCommand(project: Project, indicator: ProgressIndicator) {
        val executable = shardsExecutable(project)
        if (executable == null) {
            notify(
                project,
                NotificationType.ERROR,
                "Cannot run shards install",
                "'shards' executable not found. Install Shards or configure the Crystal SDK path."
            )
            return
        }
        val basePath = project.basePath
        if (basePath == null) {
            notify(project, NotificationType.ERROR, "Cannot run shards install", "Project has no base path.")
            return
        }
        val commandLine = GeneralCommandLine(executable.absolutePath, "install")
            .withWorkDirectory(basePath)
        val result = try {
            CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
        } catch (_: Exception) {
            notify(project, NotificationType.ERROR, "Cannot run shards install", "Failed to start '${executable.absolutePath}'.")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            VfsUtil.markDirtyAndRefresh(false, true, true, File(basePath))
            if (result.exitCode == 0) {
                notify(project, NotificationType.INFORMATION, "Shards installed", "Dependencies installed successfully.")
            } else {
                val output = (result.stderr + result.stdout).trim().take(2000)
                notify(
                    project,
                    NotificationType.ERROR,
                    "shards install failed",
                    output.ifBlank { "Exit code ${result.exitCode}." }
                )
            }
        }
    }

    fun notify(
        project: Project,
        type: NotificationType,
        title: String,
        content: String,
        action: AnAction? = null
    ) {
        if (project.isDisposed) return
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Crystal Shards")
            .createNotification(title, content, type)
        action?.let(notification::addAction)
        notification.notify(project)
    }

    private val RUNNING_KEY = Key.create<AtomicBoolean>("crystal.shards.install.running")
}
