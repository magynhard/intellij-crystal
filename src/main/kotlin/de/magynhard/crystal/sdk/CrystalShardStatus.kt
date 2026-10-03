package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore

/**
 * Dependency state of a project's `shard.yml`: which declared dependencies
 * are missing from `lib/` and which installed ones disagree with the
 * manifest or lock. One function, no persistent state — manifest, lock,
 * and `lib/` entries are re-read per call (all tiny), so results can never
 * go stale behind a cache.
 */
internal object CrystalShardStatus {

    sealed interface DependencyState {
        data object Ok : DependencyState
        data object Missing : DependencyState

        /** Installed, but neither lock nor requirement matches. */
        data class VersionMismatch(val expected: String, val actual: String) : DependencyState
    }

    data class Entry(
        val dependency: CrystalShardManifest.Dependency,
        val state: DependencyState
    )

    /**
     * Empty when the project has no `shard.yml` (not a shards-managed
     * project) or declares no dependencies. Malformed manifests yield no
     * entries — a broken manifest must not manufacture diagnostics.
     */
    fun dependencies(project: Project): List<Entry> {
        val manifest = when (val loaded = CrystalShardManifest.load(project)) {
            is CrystalShardManifest.LoadResult.Found -> loaded.manifest
            else -> return emptyList()
        }
        if (manifest.dependencies.isEmpty()) return emptyList()
        val basePath = project.basePath ?: return emptyList()
        val fileSystem = LocalFileSystem.getInstance()
        val lock = fileSystem.findFileByPath("$basePath/shard.lock")?.let { lockFile ->
            try {
                CrystalShardManifest.parseLock(VfsUtilCore.loadText(lockFile))
            } catch (_: Exception) {
                null
            }
        }
        return manifest.dependencies.map { dependency ->
            Entry(dependency, stateOf(basePath, dependency, lock))
        }
    }

    /** Dependencies that need attention, in manifest order. */
    fun problems(project: Project): List<Entry> =
        dependencies(project).filter { it.state != DependencyState.Ok }

    /**
     * Installed `version:` fields per declared dependency (null when absent
     * or unreadable). Snapshot before running shards, compare after: a field
     * that still disagrees although install succeeded proves a stale upstream
     * field, not a stale checkout.
     */
    internal fun snapshotVersions(project: Project): Map<String, String?> {
        val basePath = project.basePath ?: return emptyMap()
        val manifest = when (val loaded = CrystalShardManifest.load(project)) {
            is CrystalShardManifest.LoadResult.Found -> loaded.manifest
            else -> return emptyMap()
        }
        return manifest.dependencies.associate { it.name to installedVersion(basePath, it.name) }
    }

    /**
     * Entries whose mismatch provably comes from a stale upstream version
     * field: still `VersionMismatch` after a successful install/update
     * although the installed field did not change (and the dependency already
     * existed). Genuinely fixed, new, or never-installed entries never match.
     */
    internal fun staleFieldNotes(
        before: Map<String, String?>,
        after: Map<String, String?>,
        entries: List<Entry>
    ): List<Entry> = entries.filter { entry ->
        entry.state is DependencyState.VersionMismatch &&
            before[entry.dependency.name] != null &&
            before[entry.dependency.name] == after[entry.dependency.name]
    }

    private fun stateOf(
        basePath: String,
        dependency: CrystalShardManifest.Dependency,
        lock: Map<String, String?>?
    ): DependencyState {
        val installDir = LocalFileSystem.getInstance()
            .findFileByPath("$basePath/lib/${dependency.name}")
        if (installDir == null || !installDir.isDirectory) return DependencyState.Missing
        val installedVersion = installedVersion(basePath, dependency.name) ?: return DependencyState.Ok
        lock?.get(dependency.name)?.let { locked ->
            // A lock entry means shards owns the truth: a successful install
            // guarantees the checked-out code matches the locked revision,
            // while the dependency's own `version:` field is author-maintained
            // and may be stale (e.g. sentry tags v0.5.0 while its manifest
            // still says 0.3.2). Comparing the field against the lock can
            // therefore never be fixed by installing — so a pinned dependency
            // is Ok without consulting the field.
            if (locked != null) return DependencyState.Ok
        }
        val requirement = dependency.requirement ?: return DependencyState.Ok
        return when (CrystalVersionRequirement.satisfies(requirement, installedVersion)) {
            true -> DependencyState.Ok
            false -> DependencyState.VersionMismatch(requirement, installedVersion)
            null -> DependencyState.Ok
        }
    }

    private fun installedVersion(basePath: String, name: String): String? {
        val manifestFile = LocalFileSystem.getInstance().findFileByPath("$basePath/lib/$name/shard.yml")
            ?: return null
        return try {
            CrystalShardManifest.parse(VfsUtilCore.loadText(manifestFile))?.version
        } catch (_: Exception) {
            null
        }
    }
}
