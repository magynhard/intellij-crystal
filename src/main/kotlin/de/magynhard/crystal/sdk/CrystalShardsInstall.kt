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

    /**
     * Targeted `shards update <dependency>`: re-resolves one locked revision
     * (e.g. a branch-pinned `ameba` whose locked commit predates an upstream
     * fix) without touching other pins. Shares the install guard, so install
     * and update never run concurrently. [onUpdateSuccess] runs once after a
     * successful update — the single chaining point for automatic rebuilds;
     * a chained failure only re-offers the action, so no retry loop exists.
     */
    fun runUpdate(project: Project, dependency: String, onUpdateSuccess: (() -> Unit)? = null) {
        if (project.isDisposed) return
        val running = synchronized(RUNNING_KEY) {
            project.getUserData(RUNNING_KEY) ?: AtomicBoolean(false).also { project.putUserData(RUNNING_KEY, it) }
        }
        if (!running.compareAndSet(false, true)) return
        object : Task.Backgroundable(project, "Updating Crystal shard '$dependency'", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    runUpdateCommand(project, indicator, dependency, onUpdateSuccess)
                } finally {
                    running.set(false)
                }
            }
        }.queue()
    }

    private fun runInstallCommand(project: Project, indicator: ProgressIndicator) {
        executeShards(
            project,
            indicator,
            args = listOf("install"),
            startFailureTitle = "Cannot run shards install",
            successTitle = "Shards installed",
            successMessage = "Dependencies installed successfully.",
            failureTitle = "shards install failed"
        )
    }

    private fun runUpdateCommand(
        project: Project,
        indicator: ProgressIndicator,
        dependency: String,
        onUpdateSuccess: (() -> Unit)?
    ) {
        executeShards(
            project,
            indicator,
            args = listOf("update", dependency),
            startFailureTitle = "Cannot run shards update",
            successTitle = "Shard '$dependency' updated",
            successMessage = "Lock updated to the newest matching revision.",
            failureTitle = "shards update failed",
            onSuccess = onUpdateSuccess
        )
    }

    /**
     * Shared `shards` execution behind install and update: binary lookup,
     * cancellable run, tree refresh, truncated failure output. Success
     * invokes [onSuccess] (once, on the EDT path) — the only automatic
     * chaining point; failures only notify. After success, remaining
     * mismatches with unchanged fields get one informational balloon
     * (proven stale upstream fields, nothing actionable).
     */
    private fun executeShards(
        project: Project,
        indicator: ProgressIndicator,
        args: List<String>,
        startFailureTitle: String,
        successTitle: String,
        successMessage: String,
        failureTitle: String,
        onSuccess: (() -> Unit)? = null
    ) {
        val executable = shardsExecutable(project)
        if (executable == null) {
            notify(
                project,
                NotificationType.ERROR,
                startFailureTitle,
                "'shards' executable not found. Install Shards or configure the Crystal SDK path."
            )
            return
        }
        val basePath = project.basePath
        if (basePath == null) {
            notify(project, NotificationType.ERROR, startFailureTitle, "Project has no base path.")
            return
        }
        val before = CrystalShardStatus.snapshotVersions(project)
        val commandLine = GeneralCommandLine(listOf(executable.absolutePath) + args)
            .withWorkDirectory(basePath)
        val result = try {
            CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
        } catch (_: Exception) {
            notify(project, NotificationType.ERROR, startFailureTitle, "Failed to start '${executable.absolutePath}'.")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            VfsUtil.markDirtyAndRefresh(false, true, true, File(basePath))
            if (result.exitCode == 0) {
                notify(project, NotificationType.INFORMATION, successTitle, successMessage)
                showStaleFieldNotes(project, before)
                onSuccess?.invoke()
            } else {
                val output = (result.stderr + result.stdout).trim().take(2000)
                notify(
                    project,
                    NotificationType.ERROR,
                    failureTitle,
                    output.ifBlank { "Exit code ${result.exitCode}." }
                )
            }
        }
    }

    /**
     * One informational balloon for mismatches that survived a successful
     * install/update with an unchanged installed field: shards guarantees
     * code==lock on success, so the field itself must be stale upstream.
     * Never a marker or banner — there is nothing actionable left.
     */
    private fun showStaleFieldNotes(project: Project, before: Map<String, String?>) {
        if (project.isDisposed) return
        val entries = CrystalShardStatus.dependencies(project)
        val after = CrystalShardStatus.snapshotVersions(project)
        val lines = CrystalShardStatus.staleFieldNotes(before, after, entries).mapNotNull { entry ->
            val mismatch = entry.state as? CrystalShardStatus.DependencyState.VersionMismatch
                ?: return@mapNotNull null
            "'${entry.dependency.name}' was just installed successfully, but its own shard.yml " +
                "still reports ${mismatch.actual} instead of the expected ${mismatch.expected} — " +
                "the version was never bumped upstream. Nothing for you to do."
        }
        if (lines.isEmpty()) return
        notify(
            project,
            NotificationType.INFORMATION,
            "Shard version fields are outdated upstream",
            lines.joinToString("<br/>")
        )
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
