package de.magynhard.crystal.sdk

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProgressIndicator
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
        reindexIndicator: ProgressIndicator? = null,
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

        if (reindexIndicator != null) {
            for (file in collectCrystalFiles(newRoots, reindexIndicator)) {
                reindexIndicator.checkCanceled()
                FileBasedIndex.getInstance().requestReindex(file)
            }
        }
    }

    internal fun collectCrystalFiles(
        roots: List<VirtualFile>,
        indicator: ProgressIndicator? = null,
    ): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val stack = ArrayDeque<VirtualFile>()
        roots.forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            indicator?.checkCanceled()
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
