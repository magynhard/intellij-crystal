package de.magynhard.crystal.sdk

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex

@Suppress("UnstableApiUsage")
internal object CrystalStdlibIndexRefresher {

    fun refresh(
        project: Project,
        oldRoots: List<VirtualFile>,
        newRoots: List<VirtualFile>,
        forceReindex: Boolean,
    ) {
        WriteAction.runAndWait<RuntimeException> {
            AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                project,
                "Crystal Stdlib",
                oldRoots,
                newRoots,
                CrystalStdlibLibraryProvider::class.java.name,
            )
        }

        if (forceReindex) {
            collectCrystalFiles(newRoots).forEach(FileBasedIndex.getInstance()::requestReindex)
        }
    }

    internal fun collectCrystalFiles(roots: List<VirtualFile>): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val stack = ArrayDeque<VirtualFile>()
        roots.forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            val file = stack.removeFirst()
            if (file.isDirectory) {
                file.children.forEach(stack::addLast)
            } else if (file.extension == "cr") {
                result.add(file)
            }
        }
        return result
    }
}
