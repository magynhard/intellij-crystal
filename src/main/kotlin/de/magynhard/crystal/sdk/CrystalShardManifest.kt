package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * Reader for a project's `shard.yml` manifest and `shard.lock` file.
 *
 * Only the dependency-relevant subset is modeled: project name/version,
 * `dependencies`/`development_dependencies` with their resolver sources
 * (`github`/`gitlab`/`bitbucket`/`codeberg`/`git`/`hg`/`fossil`/`path`)
 * plus `version` requirements and `tag`/`branch`/`commit`/`bookmark` refs,
 * and the lock's resolved per-dependency versions. Everything else is
 * ignored. Parsing is safe-constructed (shard files are untrusted
 * third-party content); malformed input yields data, never exceptions.
 */
internal object CrystalShardManifest {

    data class Dependency(
        val name: String,
        val source: Source,
        val requirement: String?,
        val ref: String?,
        val development: Boolean
    )

    sealed interface Source {
        /** `github:`/`gitlab:`/`bitbucket:`/`codeberg:` with `user/repo`. */
        data class Hosted(val host: String, val repo: String) : Source

        /** `git:`/`hg:`/`fossil:` with a repository URL. */
        data class Repository(val kind: String, val url: String) : Source

        /** `path:` with a local path. */
        data class Path(val path: String) : Source

        /** Dependency entry without a recognized resolver. */
        data object Unknown : Source
    }

    data class Manifest(
        val name: String?,
        val version: String?,
        val dependencies: List<Dependency>
    )

    sealed interface LoadResult {
        data object Missing : LoadResult
        data class Malformed(val reason: String) : LoadResult
        data class Found(val manifest: Manifest) : LoadResult
    }

    /** Parses manifest text; null when it is not a usable mapping. */
    fun parse(text: String): Manifest? {
        val root = loadMapping(text) ?: return null
        val dependencies = mutableListOf<Dependency>()
        dependencies.addAll(parseDependencyMap(root["dependencies"], development = false))
        dependencies.addAll(parseDependencyMap(root["development_dependencies"], development = true))
        return Manifest(
            name = root["name"] as? String,
            version = root["version"]?.toString(),
            dependencies = dependencies
        )
    }

    /**
     * Resolved per-dependency versions from `shard.lock` text
     * (`shards: <name>: version: x.y.z`, lock format 2.x as written by
     * `shards install`). Null when there is no usable lock content —
     * callers then fall back to requirement checks; a missing lock is
     * never an error.
     */
    fun parseLock(text: String): Map<String, String?>? {
        val root = loadMapping(text) ?: return null
        val shards = root["shards"] as? Map<*, *> ?: return null
        return shards.entries.mapNotNull { (name, entry) ->
            val key = name?.toString() ?: return@mapNotNull null
            val version = (entry as? Map<*, *>)?.get("version")?.toString()
            key to version
        }.toMap()
    }

    /** Loads the project-root `shard.yml`, if any. */
    fun load(project: Project): LoadResult {
        val basePath = project.basePath ?: return LoadResult.Missing
        val file = LocalFileSystem.getInstance().findFileByPath("$basePath/shard.yml")
            ?: return LoadResult.Missing
        val text = try {
            VfsUtilCore.loadText(file)
        } catch (_: Exception) {
            return LoadResult.Malformed("Cannot read shard.yml")
        }
        return parse(text)?.let(LoadResult::Found) ?: LoadResult.Malformed("Invalid shard.yml mapping")
    }

    private fun yaml(): Yaml = Yaml(SafeConstructor(LoaderOptions()))

    @Suppress("UNCHECKED_CAST")
    private fun loadMapping(text: String): Map<String, Any?>? {
        val parsed = try {
            yaml().load<Any?>(text)
        } catch (_: Exception) {
            return null
        }
        return parsed as? Map<String, Any?>
    }

    private fun parseDependencyMap(value: Any?, development: Boolean): List<Dependency> {
        val map = value as? Map<*, *> ?: return emptyList()
        return map.entries.mapNotNull { (name, entry) ->
            val depName = name?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val attributes = entry as? Map<*, *> ?: return@mapNotNull null
            Dependency(
                name = depName,
                source = parseSource(attributes),
                requirement = attributes["version"]?.toString(),
                ref = listOf("branch", "commit", "tag", "bookmark")
                    .firstNotNullOfOrNull { attributes[it]?.toString() },
                development = development
            )
        }
    }

    private fun parseSource(attributes: Map<*, *>): Source {
        for (host in listOf("github", "gitlab", "bitbucket", "codeberg")) {
            val repo = attributes[host]?.toString()
            if (repo != null) return Source.Hosted(host, repo)
        }
        for (kind in listOf("git", "hg", "fossil")) {
            val url = attributes[kind]?.toString()
            if (url != null) return Source.Repository(kind, url)
        }
        val path = attributes["path"]?.toString()
        if (path != null) return Source.Path(path)
        return Source.Unknown
    }
}
