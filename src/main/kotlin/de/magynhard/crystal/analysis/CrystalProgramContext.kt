package de.magynhard.crystal.analysis

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import de.magynhard.crystal.run.CrystalRunConfiguration
import java.nio.file.InvalidPathException

/** Bounded entry candidates; the require graph must prove membership before using one. */
internal object CrystalProgramContext {
    fun selectedEntry(project: Project): VirtualFile? {
        val configuration = RunManager.getInstance(project).selectedConfiguration?.configuration
            as? CrystalRunConfiguration ?: return null
        if (configuration.filePath.isBlank()) return null
        return try {
            val configured = FileUtil.toSystemIndependentName(configuration.filePath)
            val path = FileUtil.toCanonicalPath(
                if (FileUtil.isAbsolute(configured)) configured
                else "${FileUtil.toSystemIndependentName(configuration.workingDirectory).trimEnd('/')}/$configured"
            )
            if ('\u0000' in path) return null
            // Resolve against the project's VFS as well as native files. This also
            // preserves the configured identity in non-local project file systems.
            val fileSystems = ProjectRootManager.getInstance(project).contentRoots.map { it.fileSystem }
                .plus(LocalFileSystem.getInstance()).distinct()
            fileSystems.asSequence().mapNotNull { it.findFileByPath(path) }
                .firstOrNull { it.isValid && !it.isDirectory && it.extension == "cr" }
        } catch (_: InvalidPathException) {
            null
        }
    }

    /** e.g. src/benchmark/ips.cr -> src/benchmark.cr, nearest library first. */
    fun libraryEntries(project: Project, file: VirtualFile, stdlibRoot: VirtualFile?): Sequence<VirtualFile> = sequence {
        val boundary = ProjectFileIndex.getInstance(project).getContentRootForFile(file)
            ?: stdlibRoot?.takeIf { VfsUtilCore.isAncestor(it, file, false) }
            ?: return@sequence
        var directory = file.parent
        while (directory != null && directory != boundary && VfsUtilCore.isAncestor(boundary, directory, false)) {
            val parent = directory.parent ?: break
            val entry = parent.findChild("${directory.name}.cr")
            if (entry != null && entry.isValid && !entry.isDirectory && entry != file) yield(entry)
            directory = parent
        }
    }
}
