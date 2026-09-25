package de.magynhard.crystal.ameba

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.AmebaNotifications
import de.magynhard.crystal.sdk.CrystalSettings
import de.magynhard.crystal.sdk.CrystalShardManifest
import de.magynhard.crystal.sdk.CrystalShardsInstall
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit opt-in `bin/ameba` build for projects declaring the Ameba
 * dev-dependency without a usable binary. Mirrors [CrystalShardsInstall]:
 * runs execute once per project as cancellable background tasks, only on
 * explicit user action (editor banner, project-open balloon) — never
 * silently. Success refreshes the project tree (banners re-evaluate).
 *
 * Command selection (`selectBuild`, pure and tested): `shards build ameba`
 * when the manifest declares an `ameba` target, otherwise the documented
 * `crystal build -o bin/ameba lib/ameba/bin/ameba.cr` equivalent.
 */
object AmebaBuild {

    data class BuildPlan(val executable: String, val args: List<String>)

    fun runBuild(project: Project) {
        if (project.isDisposed) return
        val running = synchronized(RUNNING_KEY) {
            project.getUserData(RUNNING_KEY) ?: AtomicBoolean(false).also { project.putUserData(RUNNING_KEY, it) }
        }
        if (!running.compareAndSet(false, true)) return
        object : Task.Backgroundable(project, "Building Ameba", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    runBuildCommand(project, indicator)
                } finally {
                    running.set(false)
                }
            }
        }.queue()
    }

    /**
     * Selects the build command for [shardYmlText] (null when unreadable).
     * Null when no build is possible (no shards binary and no crystal path,
     * or `lib/ameba` sources absent for the direct crystal build).
     */
    fun selectBuild(
        shardYmlText: String?,
        shardsExecutable: String?,
        crystalPath: String,
        libAmebaMain: String
    ): BuildPlan? {
        val targets = shardYmlText?.let {
            try {
                CrystalShardManifest.parseTargetNames(it)
            } catch (_: Exception) {
                emptySet()
            }
        } ?: emptySet()
        if ("ameba" in targets && shardsExecutable != null) {
            return BuildPlan(shardsExecutable, listOf("build", "ameba"))
        }
        if (crystalPath.isNotBlank() && libAmebaMain.isNotBlank()) {
            return BuildPlan(crystalPath, listOf("build", "-o", "bin/ameba", libAmebaMain))
        }
        return null
    }

    private fun runBuildCommand(project: Project, indicator: ProgressIndicator) {
        val basePath = project.basePath
        if (basePath == null) {
            AmebaNotifications.error(project, "Cannot build Ameba", "Project has no base path.")
            return
        }
        val shardYml = try {
            File(basePath, "shard.yml").takeIf { it.isFile }?.readText()
        } catch (_: Exception) {
            null
        }
        val shards = CrystalShardsInstall.shardsExecutable(project)?.absolutePath
        val crystal = CrystalSettings.getInstance(project).getEffectiveCrystalPath()
        val libMain = File(basePath, "lib/ameba/bin/ameba.cr")
            .takeIf { it.isFile }?.absolutePath ?: ""
        val plan = selectBuild(shardYml, shards, crystal, libMain)
        if (plan == null) {
            AmebaNotifications.error(
                project,
                "Cannot build Ameba",
                "Neither 'shards' nor the Crystal compiler is available, or 'lib/ameba' is not installed."
            )
            return
        }
        val commandLine = GeneralCommandLine(listOf(plan.executable) + plan.args)
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(basePath)
        val result = try {
            CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator)
        } catch (_: Exception) {
            AmebaNotifications.error(project, "Cannot build Ameba", "Failed to start '${plan.executable}'.")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            VfsUtil.markDirtyAndRefresh(false, true, true, File(basePath))
            AmebaBinary.clearCache(project)
            if (result.exitCode == 0) {
                AmebaNotifications.info(project, "Ameba built", "bin/ameba is ready for linting.")
            } else {
                val output = (result.stderr + result.stdout).trim().take(2000)
                AmebaNotifications.error(
                    project,
                    "Building Ameba failed",
                    output.ifBlank { "Exit code ${result.exitCode}." }
                )
            }
        }
    }

    private val RUNNING_KEY = Key.create<AtomicBoolean>("crystal.ameba.build.running")
}
