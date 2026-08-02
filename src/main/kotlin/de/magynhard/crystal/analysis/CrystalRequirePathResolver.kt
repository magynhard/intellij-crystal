package de.magynhard.crystal.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import de.magynhard.crystal.sdk.CrystalStdlibResolver

internal data class CrystalRequireResolution(
    val files: List<VirtualFile>,
    val watchedDirectories: Set<VirtualFile>,
)

internal class CrystalRequirePathResolver(
    private val project: Project,
    private val stdlibRoot: () -> VirtualFile? = { CrystalStdlibResolver.resolveStdlibPath(project) },
) {
    fun resolve(requiringFile: VirtualFile, requirePath: String): CrystalRequireResolution {
        wildcard(requirePath)?.let { (path, recursive) ->
            return resolveWildcard(requiringFile, path, recursive)
        }

        val roots = if (requirePath.startsWith("./") || requirePath.startsWith("../")) {
            listOfNotNull(requiringFile.parent?.let { it to false })
        } else {
            listOfNotNull(
                projectLibRoot()?.let { it to true },
                stdlibRoot()?.let { it to false },
            )
        }

        for ((root, allowShardSrc) in roots) {
            val file = candidates(root, requirePath, allowShardSrc).firstOrNull(::isCrystalFile)
            if (file != null) return CrystalRequireResolution(listOf(file), emptySet())
        }
        return CrystalRequireResolution(emptyList(), emptySet())
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
        val watchedDirectories = linkedSetOf<VirtualFile>()
        for ((root, relativePath) in locations) {
            val (target, nearestDirectory) = findDirectoryOrNearest(root, relativePath)
            if (nearestDirectory != null) watchedDirectories += nearestDirectory
            if (target != null) {
                return CrystalRequireResolution(
                    gatherCrystalFiles(target, recursive),
                    watchedDirectories,
                )
            }
        }
        return CrystalRequireResolution(emptyList(), watchedDirectories)
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

    private fun candidates(root: VirtualFile, path: String, allowShardSrc: Boolean): List<VirtualFile?> {
        val parts = path.split('/').filter(String::isNotEmpty)
        val basename = parts.lastOrNull() ?: return emptyList()
        val direct = root.findFileByRelativePath(path)
        val result = mutableListOf(
            root.findFileByRelativePath("$path.cr"),
            direct?.findChild("$basename.cr"),
        )
        if (allowShardSrc && parts.isNotEmpty()) {
            val shard = root.findChild(parts.first())?.findChild("src")
            val nested = parts.drop(1).joinToString("/")
            val shardPath = if (nested.isEmpty()) parts.first() else nested
            val shardTarget = shard?.findFileByRelativePath(shardPath)
            result += shard?.findFileByRelativePath("$shardPath.cr")
            result += shardTarget?.findChild("$basename.cr")
        }
        return result
    }

    private fun projectLibRoot(): VirtualFile? {
        return projectRoot()?.findChild("lib")
    }

    private fun projectRoot(): VirtualFile? {
        val basePath = project.basePath ?: return null
        return LocalFileSystem.getInstance().findFileByPath(basePath)
    }

    private fun isCrystalFile(file: VirtualFile?): Boolean =
        file != null && file.isValid && !file.isDirectory && file.extension == "cr"
}
