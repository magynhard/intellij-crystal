package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Resolves the Crystal standard library path by running `crystal env CRYSTAL_PATH`.
 * Returns the VirtualFile pointing to the stdlib src/ directory, or null if unavailable.
 *
 * The resolved path is cached project-scoped (via a `UserData` key on the
 * `Project` instance) so repeated calls — e.g. once per completion invocation
 * inside `require "..."` — do not spawn a `crystal env` subprocess each time.
 * The cache is invalidated lazily: a `null` result is never cached (so a
 * transient failure to run `crystal` is retried on the next call); a cached
 * `VirtualFile` that no longer exists is dropped. The cache is reset whenever
 * the user changes the Crystal SDK path in settings (via
 * [clearCachedStdlibPath]).
 */
object CrystalStdlibResolver {

    private val STDLIB_PATH_KEY = Key.create<VirtualFile>("crystal.stdlib.path.cache")
    private val DISCOVERY_STATE_KEY = Key.create<DiscoveryState>("crystal.stdlib.discovery.state")
    private val TEST_DISCOVERY_KEY = Key.create<DiscoveryOverride>("crystal.stdlib.test.discovery")
    private val TEST_VERSION_KEY = Key.create<VersionOverride>("crystal.stdlib.test.version")
    private val TEST_OVERRIDE_LOCK = Any()

    private class DiscoveryState(var generation: Long = 0)

    private data class DiscoveryAttempt(
        val generation: Long,
        val crystalPath: String,
        val override: DiscoveryOverride?,
    )

    private class DiscoveryOverride(
        val callback: () -> VirtualFile?,
        val previous: DiscoveryOverride?,
        var active: Boolean = true,
    )

    private class VersionOverride(
        val callback: () -> String?,
        val previous: VersionOverride?,
        var active: Boolean = true,
    )

    internal fun cachedStdlibPath(project: Project): VirtualFile? = synchronized(TEST_OVERRIDE_LOCK) {
        val cached = project.getUserData(STDLIB_PATH_KEY) ?: return@synchronized null
        if (cached.isValid) return@synchronized cached
        project.putUserData(STDLIB_PATH_KEY, null)
        discoveryState(project).generation++
        null
    }

    fun resolveStdlibPath(project: Project): VirtualFile? {
        // Fast path: cached value, validated for existence.
        cachedStdlibPath(project)?.let { return it }

        val attempt = synchronized(TEST_OVERRIDE_LOCK) {
            DiscoveryAttempt(
                discoveryState(project).generation,
                CrystalSettings.getInstance(project).getEffectiveCrystalPath(),
                project.getUserData(TEST_DISCOVERY_KEY),
            )
        }
        attempt.override?.let { override ->
            val discovered = override.callback() ?: return null
            return publishDiscoveredStdlibPath(project, attempt, discovered)
        }

        val crystalEnv = runCrystalEnv(attempt.crystalPath) ?: return null

        // CRYSTAL_PATH is colon-separated: "lib:/usr/lib/crystal"
        // We want the stdlib entry (starts with "/" on Unix, drive letter on Windows)
        val stdlibEntry = crystalEnv.split(":")
            .firstOrNull { it.startsWith("/") || it.matches(Regex("^[A-Z]:.*")) }
            ?: return null

        val stdlibDir = File(stdlibEntry)
        if (!stdlibDir.isDirectory) return null

        // Crystal stdlib has src/ subdirectory with .cr files
        val srcDir = File(stdlibDir, "src")
        val root = if (srcDir.isDirectory) srcDir else stdlibDir

        val resolved = LocalFileSystem.getInstance().findFileByPath(root.absolutePath)
        return resolved?.let { publishDiscoveredStdlibPath(project, attempt, it) }
    }

    /**
     * Clears the cached stdlib path. Call when the configured Crystal SDK
     * path changes (e.g. from `CrystalSettingsConfigurable.apply`), so the
     * next call to [resolveStdlibPath] re-runs `crystal env CRYSTAL_PATH`.
     */
    fun clearCachedStdlibPath(project: Project) {
        synchronized(TEST_OVERRIDE_LOCK) {
            project.putUserData(STDLIB_PATH_KEY, null)
            discoveryState(project).generation++
        }
    }

    internal fun publishStdlibPath(project: Project, root: VirtualFile): VirtualFile {
        synchronized(TEST_OVERRIDE_LOCK) {
            discoveryState(project).generation++
            project.putUserData(STDLIB_PATH_KEY, root)
        }
        return root
    }

    private fun publishDiscoveredStdlibPath(
        project: Project,
        attempt: DiscoveryAttempt,
        root: VirtualFile,
    ): VirtualFile? = synchronized(TEST_OVERRIDE_LOCK) {
        val state = discoveryState(project)
        if (
            project.isDisposed ||
            state.generation != attempt.generation ||
            project.getUserData(TEST_DISCOVERY_KEY) !== attempt.override ||
            attempt.override?.active == false ||
            CrystalSettings.getInstance(project).getEffectiveCrystalPath() != attempt.crystalPath
        ) {
            return@synchronized null
        }
        project.putUserData(STDLIB_PATH_KEY, root)
        root
    }

    internal fun installDiscoveryForTests(
        project: Project,
        parentDisposable: Disposable,
        discovery: () -> VirtualFile?,
    ) {
        val frame = synchronized(TEST_OVERRIDE_LOCK) {
            DiscoveryOverride(discovery, project.getUserData(TEST_DISCOVERY_KEY)).also {
                project.putUserData(TEST_DISCOVERY_KEY, it)
                discoveryState(project).generation++
            }
        }
        Disposer.register(parentDisposable) {
            synchronized(TEST_OVERRIDE_LOCK) {
                frame.active = false
                if (project.getUserData(TEST_DISCOVERY_KEY) === frame) {
                    project.putUserData(TEST_DISCOVERY_KEY, generateSequence(frame.previous) { it.previous }.firstOrNull { it.active })
                }
                discoveryState(project).generation++
            }
        }
    }

    internal fun installVersionForTests(
        project: Project,
        parentDisposable: Disposable,
        version: () -> String?,
    ) {
        val frame = synchronized(TEST_OVERRIDE_LOCK) {
            VersionOverride(version, project.getUserData(TEST_VERSION_KEY)).also {
                project.putUserData(TEST_VERSION_KEY, it)
            }
        }
        Disposer.register(parentDisposable) {
            synchronized(TEST_OVERRIDE_LOCK) {
                frame.active = false
                if (project.getUserData(TEST_VERSION_KEY) === frame) {
                    project.putUserData(TEST_VERSION_KEY, generateSequence(frame.previous) { it.previous }.firstOrNull { it.active })
                }
            }
        }
    }

    fun resolveCrystalVersion(project: Project): String? {
        synchronized(TEST_OVERRIDE_LOCK) { project.getUserData(TEST_VERSION_KEY)?.callback }?.let { return it() }
        val crystalPath = CrystalSettings.getInstance(project).getEffectiveCrystalPath()
        return runCrystalVersion(crystalPath)
    }

    private fun runCrystalVersion(crystalPath: String): String? {
        return try {
            val process = ProcessBuilder(crystalPath, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) output else null
        } catch (_: Exception) {
            null
        }
    }

    private fun runCrystalEnv(crystalPath: String): String? {
        return try {
            val process = ProcessBuilder(crystalPath, "env", "CRYSTAL_PATH")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank()) output else null
        } catch (_: Exception) {
            null
        }
    }

    private fun discoveryState(project: Project): DiscoveryState {
        project.getUserData(DISCOVERY_STATE_KEY)?.let { return it }
        return DiscoveryState().also { state ->
            project.putUserData(DISCOVERY_STATE_KEY, state)
            project.messageBus.connect(project).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    clearCachedStdlibPath(project)
                }
            })
        }
    }
}
