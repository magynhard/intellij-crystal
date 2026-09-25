package de.magynhard.crystal.ameba

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.AmebaNotifications
import de.magynhard.crystal.sdk.CrystalProjectDetector
import de.magynhard.crystal.sdk.CrystalSettings
import de.magynhard.crystal.sdk.CrystalShardsInstall
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in Ameba installation as a shard dev-dependency.
 *
 * Default install offer (conditional): when linting is enabled but no usable
 * binary resolves anywhere and no version problem owns the case, the project
 * open balloon (and the shard.yml banner) offer a one-click install. Without
 * a `shard.yml` at all only the global-install docs link is offered — no
 * `shards init` automation. Never silent, never partial without notice.
 *
 * The install chain (background, cancellable): ensure the manifest entries
 * (`development_dependencies` + `targets`), `shards install`,
 * `shards build ameba`, tree refresh. Manifest edits are minimal textual
 * insertions (valid YAML in, valid YAML out — never a re-dump), executed as
 * an undoable write command.
 */
object AmebaInstall {

    const val GITHUB_REPO = "crystal-ameba/ameba"
    const val VERSION_PIN = "~> 1.7.0"
    const val TARGET_MAIN = "lib/ameba/bin/ameba.cr"
    const val INSTALL_DOCS_URL = "https://github.com/crystal-ameba/ameba#installation"

    private const val DISMISS_KEY = "crystal.ameba.install.dismissed"

    fun isDismissed(project: Project): Boolean =
        PropertiesComponent.getInstance(project).getBoolean(DISMISS_KEY, false)

    fun dismiss(project: Project) {
        PropertiesComponent.getInstance(project).setValue(DISMISS_KEY, true)
    }

    /**
     * True when the install offer applies: linting enabled, nothing usable
     * resolves, no version problem owns the case (old binaries get the
     * version warning instead), a Crystal project, and not dismissed.
     * Background threads: processes run outside any read action; only the
     * final project check takes a brief read.
     */
    fun shouldOfferInstall(project: Project): Boolean {
        if (project.isDisposed || isDismissed(project)) return false
        val state = CrystalSettings.getInstance(project).state
        if (!state.amebaEnabled) return false
        // An explicit (if broken) manual path is owned by the settings and
        // runner surfaces, never by the install offer.
        if (state.amebaPath.isNotBlank()) return false
        if (AmebaBinary.resolve(project) != null) return false
        if (AmebaBinary.versionProblem(project) != null) return false
        return try {
            com.intellij.openapi.application.ReadAction.compute<Boolean, RuntimeException> {
                CrystalProjectDetector.isCrystalProject(project)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun hasShardYml(project: Project): Boolean {
        val basePath = project.basePath ?: return false
        return File(basePath, "shard.yml").isFile
    }

    fun docsLinkAction(): NotificationAction {
        return object : NotificationAction("How to install Ameba") {
            override fun actionPerformed(event: AnActionEvent, notification: com.intellij.notification.Notification) {
                notification.expire()
                BrowserUtil.browse(INSTALL_DOCS_URL)
            }
        }
    }

    fun dismissAction(project: Project): NotificationAction {
        return object : NotificationAction("Don't ask again") {
            override fun actionPerformed(event: AnActionEvent, notification: com.intellij.notification.Notification) {
                notification.expire()
                dismiss(project)
            }
        }
    }

    /**
     * Adds the Ameba dev-dependency (and `ameba` target) to manifest text
     * when absent. Returns the new text, or null when nothing changes.
     * Pure textual insertion — formatting, comments, and order of everything
     * else are preserved byte-for-byte.
     */
    fun ensureDevDependency(text: String): String? {
        if (hasDependencyEntry(text)) return null
        val lines = text.split('\n').toMutableList()
        val section = lines.indexOfFirst { it.trim() == "development_dependencies:" }
        val entry = listOf("  ameba:", "    github: $GITHUB_REPO", "    version: $VERSION_PIN")
        if (section >= 0) {
            lines.addAll(section + 1, entry)
        } else {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("development_dependencies:")
            lines.addAll(entry)
        }
        return lines.joinToString("\n")
    }

    /**
     * Adds the `ameba` build target to manifest text when absent (needed for
     * `shards build ameba`). Returns the new text, or null when an `ameba`
     * target already exists (a custom `main` is never overwritten).
     */
    fun ensureAmebaTarget(text: String): String? {
        if (hasTargetEntry(text)) return null
        val lines = text.split('\n').toMutableList()
        val section = lines.indexOfFirst { it.trim() == "targets:" }
        val entry = listOf("  ameba:", "    main: $TARGET_MAIN")
        if (section >= 0) {
            lines.addAll(section + 1, entry)
        } else {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("targets:")
            lines.addAll(entry)
        }
        return lines.joinToString("\n")
    }

    private fun hasDependencyEntry(text: String): Boolean {
        return hasSectionEntry(text, setOf("dependencies", "development_dependencies"), "ameba")
    }

    private fun hasTargetEntry(text: String): Boolean {
        return hasSectionEntry(text, setOf("targets"), "ameba")
    }

    /**
     * True when a two-space `name:` key sits inside one of the top-level
     * [sections] (shards writes dependencies and targets at exactly two
     * spaces; attribute lines sit deeper and never match).
     */
    private fun hasSectionEntry(text: String, sections: Set<String>, name: String): Boolean {
        var inSection = false
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (!line.startsWith(" ") && !line.startsWith("\t") && trimmed.endsWith(":")) {
                inSection = trimmed.removeSuffix(":") in sections
                continue
            }
            if (inSection && line.matches(Regex("""^  $name\s*:"""))) return true
        }
        return false
    }

    fun installAsDevDependency(project: Project) {
        if (project.isDisposed) return
        val running = synchronized(RUNNING_KEY) {
            project.getUserData(RUNNING_KEY) ?: AtomicBoolean(false).also { project.putUserData(RUNNING_KEY, it) }
        }
        if (!running.compareAndSet(false, true)) return
        object : Task.Backgroundable(project, "Installing Ameba as dev-dependency", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    runInstallChain(project, indicator)
                } finally {
                    running.set(false)
                }
            }
        }.queue()
    }

    private fun runInstallChain(project: Project, indicator: ProgressIndicator) {
        val basePath = project.basePath
        if (basePath == null) {
            AmebaNotifications.error(project, "Cannot install Ameba", "Project has no base path.")
            return
        }
        indicator.text = "Updating shard.yml"
        if (!ensureManifestEntries(project, basePath)) return
        indicator.text = "Running shards install"
        if (!runShards(project, basePath, listOf("install"), indicator)) return
        indicator.text = "Building bin/ameba"
        val shards = CrystalShardsInstall.shardsExecutable(project)?.absolutePath
        val crystal = CrystalSettings.getInstance(project).getEffectiveCrystalPath()
        val libMain = File(basePath, "lib/ameba/bin/ameba.cr")
            .takeIf { it.isFile }?.absolutePath ?: ""
        val shardYml = try {
            File(basePath, "shard.yml").takeIf { it.isFile }?.readText()
        } catch (_: Exception) {
            null
        }
        val plan = AmebaBuild.selectBuild(shardYml, shards, crystal, libMain)
        if (plan == null) {
            AmebaNotifications.error(
                project, "Cannot build Ameba", "No toolchain available after install."
            )
            return
        }
        if (!runShardsLike(project, basePath, plan.executable, plan.args, indicator)) return
        ApplicationManager.getApplication().invokeLater {
            AmebaBinary.clearCache(project)
            AmebaNotifications.info(project, "Ameba installed", "bin/ameba is ready for linting.")
        }
    }

    private fun ensureManifestEntries(project: Project, basePath: String): Boolean {
        val file = LocalFileSystem.getInstance().findFileByPath("$basePath/shard.yml") ?: run {
            AmebaNotifications.error(project, "Cannot install Ameba", "No shard.yml in the project root.")
            return false
        }
        val current = readManifestText(file) ?: run {
            AmebaNotifications.error(project, "Cannot install Ameba", "Cannot read shard.yml.")
            return false
        }
        var updated = ensureDevDependency(current) ?: current
        updated = ensureAmebaTarget(updated) ?: updated
        if (updated == current) return true
        val written = try {
            WriteCommandAction.runWriteCommandAction(project, "Add Ameba dev-dependency", null, {
                VfsUtil.saveText(file, updated)
            })
            true
        } catch (_: Exception) {
            AmebaNotifications.error(project, "Cannot install Ameba", "Cannot write shard.yml.")
            false
        }
        if (!written) return false
        ApplicationManager.getApplication().invokeAndWait {
            VfsUtil.markDirtyAndRefresh(false, true, true, File(basePath))
        }
        return true
    }

    private fun readManifestText(file: VirtualFile): String? {
        return try {
            ApplicationManager.getApplication().runReadAction<String?> {
                VfsUtilCore.loadText(file)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun runShards(project: Project, basePath: String, args: List<String>, indicator: ProgressIndicator): Boolean {
        val shards = CrystalShardsInstall.shardsExecutable(project)?.absolutePath
        if (shards == null) {
            AmebaNotifications.error(
                project, "Cannot install Ameba", "'shards' executable not found. Install Shards first."
            )
            return false
        }
        return runShardsLike(project, basePath, shards, args, indicator)
    }

    private fun runShardsLike(
        project: Project,
        basePath: String,
        executable: String,
        args: List<String>,
        indicator: ProgressIndicator
    ): Boolean {
        val commandLine = GeneralCommandLine(listOf(executable) + args)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(basePath)
        val result = try {
            CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
        } catch (_: Exception) {
            AmebaNotifications.error(
                project, "Cannot install Ameba", "Failed to start '$executable'."
            )
            return false
        }
        ApplicationManager.getApplication().invokeLater {
            VfsUtil.markDirtyAndRefresh(false, true, true, File(basePath))
        }
        if (result.exitCode != 0) {
            val output = (result.stderr + result.stdout).trim().take(2000)
            AmebaNotifications.error(
                project,
                "Installing Ameba failed",
                output.ifBlank { "Exit code ${result.exitCode}." }
            )
            return false
        }
        return true
    }

    private val RUNNING_KEY = Key.create<AtomicBoolean>("crystal.ameba.install.running")
}
