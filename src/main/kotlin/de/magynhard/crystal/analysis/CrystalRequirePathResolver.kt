package de.magynhard.crystal.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import de.magynhard.crystal.sdk.CrystalStdlibResolver

internal enum class CrystalWildcardMode {
    DIRECT,
    RECURSIVE,
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

internal class CrystalRequirePathResolver(
    private val project: Project,
    private val stdlibRoot: () -> VirtualFile? = { CrystalStdlibResolver.resolveStdlibPath(project) },
) {
    private data class ExactRoot(
        val directory: VirtualFile?,
        val path: String,
        val allowShardSrc: Boolean,
    )

    private data class ExactCandidate(
        val path: String,
        val file: VirtualFile?,
    )

    fun resolve(requiringFile: VirtualFile, requirePath: String): CrystalRequireResolution {
        wildcard(requirePath)?.let { (path, recursive) ->
            return resolveWildcard(requiringFile, path, recursive)
        }

        val roots = if (requirePath.startsWith("./") || requirePath.startsWith("../")) {
            listOfNotNull(requiringFile.parent?.let { ExactRoot(it, it.path, false) })
        } else {
            val projectRoot = projectRoot()
            listOfNotNull(
                projectRoot?.let { ExactRoot(it.findChild("lib"), path(it.path, "lib"), true) },
                stdlibRoot()?.let { ExactRoot(it, it.path, false) },
            )
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

    private fun resolveWildcard(
        requiringFile: VirtualFile,
        path: String,
        recursive: Boolean,
    ): CrystalRequireResolution {
        val locations = if (path.startsWith("./") || path.startsWith("../")) {
            listOfNotNull(requiringFile.parent?.let { it to path })
        } else {
            listOfNotNull(
                projectRoot()?.let { it to "lib/$path" },
                stdlibRoot()?.let { it to path },
            )
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
        val children = directory.children
        val result = children.filter(::isCrystalFile).sortedBy(VirtualFile::getPath).toMutableList()
        if (recursive) {
            children.asSequence()
                .filter { it.isValid && it.isDirectory }
                .sortedBy(VirtualFile::getPath)
                .forEach { result += gatherCrystalFiles(it, recursive = true) }
        }
        return result
    }

    private fun candidates(root: ExactRoot, requirePath: String): List<ExactCandidate> {
        val parts = requirePath.split('/').filter(String::isNotEmpty)
        val basename = parts.lastOrNull() ?: return emptyList()
        val result = mutableListOf(
            candidate(root, "$requirePath.cr"),
            candidate(root, "$requirePath/$basename.cr"),
        )
        if (root.allowShardSrc && parts.isNotEmpty()) {
            val nested = parts.drop(1).joinToString("/")
            val shardPath = if (nested.isEmpty()) parts.first() else nested
            val shardRoot = "${parts.first()}/src"
            result += candidate(root, "$shardRoot/$shardPath.cr")
            result += candidate(root, "$shardRoot/$shardPath/$basename.cr")
        }
        return result
    }

    private fun candidate(root: ExactRoot, relativePath: String): ExactCandidate =
        ExactCandidate(path(root.path, relativePath), root.directory?.findFileByRelativePath(relativePath))

    private fun path(root: String, relativePath: String): String =
        FileUtil.toCanonicalPath("${root.trimEnd('/')}/$relativePath", '/')

    private fun projectRoot(): VirtualFile? {
        val basePath = project.basePath ?: return null
        return LocalFileSystem.getInstance().findFileByPath(basePath)
    }

    private fun isCrystalFile(file: VirtualFile?): Boolean =
        file != null && file.isValid && !file.isDirectory && file.extension == "cr"
}
