package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File

/**
 * Effective Ameba binary resolution.
 *
 * Order (highest priority first):
 * 1. Manual `amebaPath` from settings when set and valid. Project-local and
 *    `PATH` candidates are ignored; a broken manual path resolves to nothing
 *    so the misconfiguration surfaces instead of silently using another binary.
 * 2. Project-local `<project>/bin/ameba` when the project-root `shard.yml`
 *    declares an `ameba` dependency and the binary is executable and valid.
 * 3. System `PATH` and known install locations via [AmebaDetector].
 *
 * Every level additionally requires the minimum version ([AmebaVersion]):
 * outdated manual binaries resolve to nothing (fail loud), outdated project
 * binaries fall through to system detection (like broken ones), and
 * [versionProblem] reports the reason so warnings can target it while the
 * integration stays disabled and built-ins remain the fallback.
 *
 * There is deliberately no fallback that executes `lib/ameba/bin/ameba.cr`
 * through the Crystal compiler (slowest documented Ameba mode).
 *
 * Only `java.io` stats and processes are used — no VFS access — so resolution
 * is safe from background threads (e.g. `ExternalAnnotator.doAnnotate`).
 * Positive and negative results are cached per project; the cache key covers
 * the settings path, the candidate files' existence and modification stamps,
 * and the manifest stamp. [clearCache] runs on settings apply; anything else
 * (rebuilds, edits) is picked up through the stamp checks.
 */
object AmebaBinary {

    enum class Source { SETTINGS, PROJECT, SYSTEM }

    data class Resolved(val path: String, val version: String, val source: Source)

    /**
     * Why no binary resolves despite Ameba being relevant: the candidate
     * exists but is too old ([TooOld]), or no parseable version could be
     * determined ([Undetermined]). Null when a binary resolves or nothing
     * Ameba-related is configured. Drives targeted warnings (balloon, shard
     * banner, settings text); the integration stays disabled in both cases
     * so the built-in inspections remain the fallback.
     */
    sealed interface VersionProblem {
        data class TooOld(val path: String, val version: String) : VersionProblem
        data class Undetermined(val path: String) : VersionProblem
    }

    fun resolve(project: Project): Resolved? = resolve(project) { AmebaDetector.detect() }

    internal fun resolve(project: Project, detectSystem: () -> String?): Resolved? {
        val signals = signals(project)
        cached(project)?.takeIf { it.first == signals }?.let { return it.second }
        val resolved = resolveUncached(project, detectSystem)
        project.putUserData(CACHE_KEY, signals to resolved)
        return resolved
    }

    fun clearCache(project: Project) {
        project.putUserData(CACHE_KEY, null)
    }

    private fun cached(project: Project): Pair<Signals, Resolved?>? = project.getUserData(CACHE_KEY)

    /**
     * True when the project-root `shard.yml` declares an `ameba` dependency
     * (regular or development section — both install the same binary).
     * Malformed or missing manifests yield false, never diagnostics.
     */
    fun declaresAmeba(project: Project): Boolean {
        val text = shardYmlText(project) ?: return false
        val manifest = try {
            CrystalShardManifest.parse(text)
        } catch (_: Exception) {
            null
        } ?: return false
        return manifest.dependencies.any { it.name == "ameba" }
    }

    /**
     * The `version:` requirement of the `ameba` dependency in the
     * project-root `shard.yml`, or null when absent, unevaluated
     * (`branch`/`commit`/`tag` pins have no requirement), or unreadable.
     */
    internal fun amebaRequirement(project: Project): String? {
        val text = shardYmlText(project) ?: return null
        val manifest = try {
            CrystalShardManifest.parse(text)
        } catch (_: Exception) {
            null
        } ?: return null
        return manifest.dependencies.firstOrNull { it.name == "ameba" }?.requirement
    }

    /**
     * True when Ameba is declared but no usable project-local binary exists.
     * Pure file stats, no process — cheap enough for editor banners.
     */
    fun projectBinaryMissing(project: Project): Boolean {
        if (!declaresAmeba(project)) return false
        return projectBinaryFile(project)?.canExecuteFile() != true
    }

    private fun resolveUncached(project: Project, detectSystem: () -> String?): Resolved? {
        val manual = CrystalSettings.getInstance(project).state.amebaPath.trim()
        if (manual.isNotEmpty()) {
            // Explicit configuration wins and fails loudly: no silent fallback.
            val version = AmebaDetector.validate(manual) ?: return null
            if (!AmebaVersion.meetsMinimum(version)) return null
            return Resolved(manual, version, Source.SETTINGS)
        }
        usableProjectBinary(project)?.let { (path, version) -> return Resolved(path, version, Source.PROJECT) }
        val detected = try {
            detectSystem()
        } catch (_: Exception) {
            null
        } ?: return null
        val version = AmebaDetector.validate(detected) ?: return null
        if (!AmebaVersion.meetsMinimum(version)) return null
        return Resolved(detected, version, Source.SYSTEM)
    }

    /**
     * The version problem behind a null [resolve], or null when a binary
     * resolves (or nothing Ameba-related is configured). Mirrors the
     * [resolveUncached] cascade: manual problems win outright, a stale
     * project binary is remembered across the system fallthrough but only
     * surfaces when nothing usable resolves. Invariant: `resolve() != null`
     * implies `versionProblem() == null`.
     */
    fun versionProblem(project: Project): VersionProblem? =
        versionProblem(project) { AmebaDetector.detect() }

    internal fun versionProblem(project: Project, detectSystem: () -> String?): VersionProblem? {
        val manual = CrystalSettings.getInstance(project).state.amebaPath.trim()
        if (manual.isNotEmpty()) {
            val raw = AmebaDetector.validate(manual) ?: return null
            return problemOf(manual, raw)
        }
        var remembered: VersionProblem? = null
        projectBinaryCandidate(project)?.let { (path, raw) ->
            val problem = problemOf(path, raw)
            if (problem == null) return null
            remembered = problem
        }
        val detected = try {
            detectSystem()
        } catch (_: Exception) {
            null
        } ?: return remembered
        val raw = AmebaDetector.validate(detected) ?: return remembered
        val systemProblem = problemOf(detected, raw) ?: return null
        return remembered ?: systemProblem
    }

    internal fun problemOf(path: String, rawVersion: String): VersionProblem? {
        val parsed = AmebaVersion.parse(rawVersion) ?: return VersionProblem.Undetermined(path)
        if (AmebaVersion.meetsMinimum(rawVersion)) return null
        return VersionProblem.TooOld(path, parsed)
    }

    /**
     * Validated project-local binary meeting the minimum version, or null
     * (undeclared, missing, broken, or outdated — all fall through to system
     * detection, mirroring the pre-version-gate behavior for broken binaries).
     */
    private fun usableProjectBinary(project: Project): Pair<String, String>? {
        val (path, raw) = projectBinaryCandidate(project) ?: return null
        if (!AmebaVersion.meetsMinimum(raw)) return null
        return path to raw
    }

    private fun projectBinaryCandidate(project: Project): Pair<String, String>? {
        val basePath = project.basePath ?: return null
        if (!declaresAmeba(project)) return null
        val binary = File(basePath, "bin/ameba")
        if (!binary.canExecuteFile()) return null
        return AmebaDetector.validate(binary.absolutePath)?.let { binary.absolutePath to it }
    }

    private fun projectBinaryFile(project: Project): File? {
        val basePath = project.basePath ?: return null
        return File(basePath, "bin/ameba")
    }

    private fun shardYmlText(project: Project): String? {
        val basePath = project.basePath ?: return null
        return try {
            File(basePath, "shard.yml").takeIf { it.isFile }?.readText()
        } catch (_: Exception) {
            null
        }
    }

    private fun File.canExecuteFile(): Boolean = isFile && canExecute()

    private data class Signals(
        val settingsPath: String,
        val settingsFileStamp: Long,
        val projectBinaryState: String,
        val manifestStamp: Long
    )

    private fun signals(project: Project): Signals {
        val settingsPath = CrystalSettings.getInstance(project).state.amebaPath.trim()
        val settingsStamp = if (settingsPath.isNotEmpty()) stampOf(File(settingsPath)) else -1L
        val basePath = project.basePath
        val binaryState = if (basePath == null) {
            "none"
        } else {
            val binary = File(basePath, "bin/ameba")
            if (binary.canExecuteFile()) "ok@${binary.lastModified()}" else "missing"
        }
        val manifestStamp = if (basePath == null) -1L else stampOf(File(basePath, "shard.yml"))
        return Signals(settingsPath, settingsStamp, binaryState, manifestStamp)
    }

    private fun stampOf(file: File): Long {
        return try {
            if (file.isFile) file.lastModified() else -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private val CACHE_KEY = Key.create<Pair<Signals, Resolved?>>("crystal.ameba.binary.cache")
}
