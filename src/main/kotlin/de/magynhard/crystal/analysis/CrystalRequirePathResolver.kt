package de.magynhard.crystal.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import de.magynhard.crystal.sdk.CrystalStdlibResolver

internal enum class CrystalWildcardMode {
    DIRECT,
    RECURSIVE,
}

private enum class CrystalRequireRootMode {
    PROJECT_THEN_STDLIB,
    STDLIB_ONLY,
}

internal data class CrystalWildcardWatch(
    val watchedDirectory: VirtualFile,
    val targetPath: String,
    val mode: CrystalWildcardMode,
)

internal data class CrystalRequireResolution(
    val files: List<VirtualFile>,
    val watchedDirectories: Set<VirtualFile>,
    val exactCandidatePaths: Set<String> = emptySet(),
    val wildcardWatches: Set<CrystalWildcardWatch> = emptySet(),
)

internal class CrystalRequirePathResolver private constructor(
    private val project: Project,
    private val stdlibRoot: () -> VirtualFile?,
    private val projectRoot: () -> VirtualFile?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    constructor(
        project: Project,
        stdlibRoot: () -> VirtualFile? = { CrystalStdlibResolver.resolveStdlibPath(project) },
    ) : this(project, stdlibRoot, { resolveProjectRoot(project) }, Unit)

    internal constructor(
        project: Project,
        stdlibRoot: () -> VirtualFile?,
        projectRoot: () -> VirtualFile?,
    ) : this(project, stdlibRoot, projectRoot, Unit)

    private data class ExactRoot(
        val directory: VirtualFile?,
        val path: String,
        val allowShardSrc: Boolean,
    )

    private data class ExactCandidate(
        val path: String,
        val file: VirtualFile?,
    )

    fun resolve(requiringFile: VirtualFile, requirePath: String): CrystalRequireResolution =
        resolve(requiringFile, requirePath, stdlibRoot, CrystalRequireRootMode.PROJECT_THEN_STDLIB)

    internal fun resolve(
        requiringFile: VirtualFile,
        requirePath: String,
        capturedStdlibRoot: VirtualFile?,
    ): CrystalRequireResolution = resolve(
        requiringFile,
        requirePath,
        { capturedStdlibRoot },
        CrystalRequireRootMode.PROJECT_THEN_STDLIB,
    )

    internal fun resolveFromStdlib(
        requiringFile: VirtualFile,
        requirePath: String,
        capturedStdlibRoot: VirtualFile,
    ): CrystalRequireResolution = resolve(
        requiringFile,
        requirePath,
        { capturedStdlibRoot },
        CrystalRequireRootMode.STDLIB_ONLY,
    )

    private fun resolve(
        requiringFile: VirtualFile,
        requirePath: String,
        stdlibRoot: () -> VirtualFile?,
        mode: CrystalRequireRootMode,
    ): CrystalRequireResolution {
        wildcard(requirePath)?.let { (path, recursive) ->
            return resolveWildcard(requiringFile, path, recursive, stdlibRoot, mode)
        }

        val roots = if (isRelativePath(requirePath)) {
            listOfNotNull(requiringFile.parent?.let { ExactRoot(it, it.path, false) })
        } else {
            val stdlib = stdlibRoot()
            if (mode == CrystalRequireRootMode.STDLIB_ONLY) {
                listOfNotNull(stdlib?.let { ExactRoot(it, it.path, true) })
            } else {
                val projectRoot = projectRoot()
                listOfNotNull(
                    projectRoot?.let { ExactRoot(it.findChild("lib"), path(it.path, "lib"), true) },
                    stdlib?.let { ExactRoot(it, it.path, true) },
                )
            }
        }

        val exactCandidatePaths = linkedSetOf<String>()
        for (root in roots) {
            for (candidate in candidates(root, requirePath)) {
                exactCandidatePaths += candidate.path
                if (isCrystalFile(candidate.file)) {
                    return CrystalRequireResolution(
                        listOf(requireNotNull(candidate.file)),
                        emptySet(),
                        exactCandidatePaths,
                    )
                }
            }
        }
        return CrystalRequireResolution(emptyList(), emptySet(), exactCandidatePaths)
    }

    fun resolvePrelude(): VirtualFile? =
        stdlibRoot()?.findChild("prelude.cr")?.takeIf(::isCrystalFile)

    internal fun resolvePrelude(capturedStdlibRoot: VirtualFile?): VirtualFile? =
        capturedStdlibRoot?.findChild("prelude.cr")?.takeIf(::isCrystalFile)

    internal fun currentStdlibRoot(): VirtualFile? = stdlibRoot()?.takeIf(VirtualFile::isValid)

    private fun resolveWildcard(
        requiringFile: VirtualFile,
        path: String,
        recursive: Boolean,
        stdlibRoot: () -> VirtualFile?,
        rootMode: CrystalRequireRootMode,
    ): CrystalRequireResolution {
        val locations = if (isRelativePath(path)) {
            listOfNotNull(requiringFile.parent?.let { it to path })
        } else {
            val stdlib = stdlibRoot()
            if (rootMode == CrystalRequireRootMode.STDLIB_ONLY) {
                listOfNotNull(stdlib?.let { it to path })
            } else {
                listOfNotNull(
                    projectRoot()?.let { it to "lib/$path" },
                    stdlib?.let { it to path },
                )
            }
        }
        val mode = if (recursive) CrystalWildcardMode.RECURSIVE else CrystalWildcardMode.DIRECT
        val wildcardWatches = linkedSetOf<CrystalWildcardWatch>()
        for ((root, relativePath) in locations) {
            val (target, nearestDirectory) = findDirectoryOrNearest(root, relativePath)
            if (nearestDirectory != null) {
                wildcardWatches += CrystalWildcardWatch(
                    nearestDirectory,
                    path(root.path, relativePath),
                    mode,
                )
            }
            if (target != null) {
                return CrystalRequireResolution(
                    gatherCrystalFiles(target, recursive),
                    wildcardWatches.mapTo(linkedSetOf(), CrystalWildcardWatch::watchedDirectory),
                    wildcardWatches = wildcardWatches,
                )
            }
        }
        return CrystalRequireResolution(
            emptyList(),
            wildcardWatches.mapTo(linkedSetOf(), CrystalWildcardWatch::watchedDirectory),
            wildcardWatches = wildcardWatches,
        )
    }

    private fun wildcard(path: String): Pair<String, Boolean>? = when {
        path.endsWith("/**") -> path.removeSuffix("/**") to true
        path.endsWith("/*") -> path.removeSuffix("/*") to false
        else -> null
    }

    private fun isRelativePath(path: String): Boolean = path.startsWith('.')

    private fun findDirectoryOrNearest(
        root: VirtualFile,
        relativePath: String,
    ): Pair<VirtualFile?, VirtualFile?> {
        if (!root.isValid || !root.isDirectory) return null to null
        var current = root
        for (part in relativePath.split('/').filter(String::isNotEmpty)) {
            if (part == ".") continue
            if (part == "..") {
                current = current.parent ?: return null to current
                continue
            }
            val child = current.findChild(part) ?: return null to current
            if (!child.isValid || !child.isDirectory) return null to current
            current = child
        }
        return current to current
    }

    private fun gatherCrystalFiles(directory: VirtualFile, recursive: Boolean): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val pending = ArrayDeque<VirtualFile>()
        val visited = mutableSetOf<String>()
        pending.add(directory)
        while (pending.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val (current, identity) = canonicalDirectory(pending.removeLast()) ?: continue
            if (containsCanonicalProjectRoot(identity)) continue
            if (!visited.add(identity)) continue
            val children = current.children.sortedBy(VirtualFile::getPath)
            for (child in children) {
                ProgressManager.checkCanceled()
                if (isCrystalFile(child)) result += child
            }
            if (recursive) {
                children.asReversed().asSequence()
                    .filter { it.isValid && it.isDirectory }
                    .forEach(pending::addLast)
            }
        }
        return result
    }

    private fun candidates(root: ExactRoot, requirePath: String): List<ExactCandidate> {
        val parts = requirePath.split('/').filter(String::isNotEmpty)
        val basename = parts.lastOrNull()?.removeSuffix(".cr") ?: return emptyList()
        val relativeFilename = requirePath
        val result = mutableListOf(candidate(root, ensureCrystalSuffix(relativeFilename)))
        val filenameIsRelative = isRelativePath(requirePath)
        val shardName = parts.firstOrNull()
        val shardPath = parts.drop(1).joinToString("/").ifEmpty { null }
        if (root.allowShardSrc && !filenameIsRelative && shardName != null && shardPath != null) {
            val shardSrc = "$shardName/src"
            val shardPathStem = shardPath.removeSuffix(".cr")
            result += candidate(root, "$shardSrc/$shardPathStem.cr")
            result += candidate(root, "$shardSrc/$shardName/$shardPathStem.cr")
            result += candidate(root, "$relativeFilename/$basename.cr")
            result += candidate(root, "$shardSrc/$shardPath/$shardPathStem.cr")
            result += candidate(root, "$shardSrc/$shardName/$shardPath/$shardPathStem.cr")
        } else {
            result += candidate(root, "$relativeFilename/$basename.cr")
            if (root.allowShardSrc && !filenameIsRelative) {
                result += candidate(root, "$relativeFilename/src/$basename.cr")
            }
        }
        return result
    }

    private fun ensureCrystalSuffix(path: String): String = if (path.endsWith(".cr")) path else "$path.cr"

    private fun candidate(root: ExactRoot, relativePath: String): ExactCandidate =
        ExactCandidate(path(root.path, relativePath), root.directory?.findFileByRelativePath(relativePath))

    private fun path(root: String, relativePath: String): String =
        FileUtil.toCanonicalPath("${root.trimEnd('/')}/$relativePath", '/')

    private fun canonicalDirectory(directory: VirtualFile): Pair<VirtualFile, String>? {
        if (!directory.isValid || !directory.isDirectory) return null
        val identity = directory.canonicalPath ?: FileUtil.toCanonicalPath(directory.path)
        val canonical = LocalFileSystem.getInstance().findFileByPath(identity)
            ?.takeIf { it.isValid && it.isDirectory }
            ?: directory
        return canonical to identity
    }

    private fun containsCanonicalProjectRoot(directoryPath: String): Boolean {
        val root = projectRoot()
            ?: project.basePath?.let(LocalFileSystem.getInstance()::findFileByPath)
            ?: return false
        val projectPath = root.canonicalPath ?: FileUtil.toCanonicalPath(root.path)
        return FileUtil.isAncestor(directoryPath, projectPath, false)
    }

    private fun isCrystalFile(file: VirtualFile?): Boolean =
        file != null && file.isValid && !file.isDirectory && file.extension == "cr"

    private companion object {
        fun resolveProjectRoot(project: Project): VirtualFile? {
            val basePath = project.basePath ?: return null
            return LocalFileSystem.getInstance().findFileByPath(basePath)
        }
    }
}
