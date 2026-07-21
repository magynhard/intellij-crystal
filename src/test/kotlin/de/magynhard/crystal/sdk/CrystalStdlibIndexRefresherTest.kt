package de.magynhard.crystal.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalStdlibIndexRefresherTest : BasePlatformTestCase() {

    fun testRefreshNotifiesSyntheticLibraryRootsWithoutCreatingModuleLibrary() {
        myFixture.addFileToProject("main.cr", "puts 1")
        val stdlibRoot = myFixture.tempDirFixture.findOrCreateDir("stdlib")
        val arrayFile = myFixture.tempDirFixture.createFile("stdlib/array.cr", "class Array; end")
        myFixture.tempDirFixture.createFile("stdlib/compiler/crystal/config.cr", "SOURCE_DATE_EPOCH = 0")
        val roots = CrystalStdlibRoots.enumerate(stdlibRoot)
        var notifiedRoots: Collection<VirtualFile>? = null
        project.messageBus.connect(testRootDisposable).subscribe(
            AdditionalLibraryRootsListener.TOPIC,
            AdditionalLibraryRootsListener { _, _, newRoots, _ -> notifiedRoots = newRoots }
        )

        CrystalStdlibIndexRefresher.refresh(project, emptyList(), roots, ProgressIndicatorBase())

        assertEquals(roots, notifiedRoots)
        assertTrue(notifiedRoots!!.contains(arrayFile))
        val module = ModuleManager.getInstance(project).modules.single()
        assertFalse(
            ModuleRootManager.getInstance(module).orderEntries.any { it.presentableName == "Crystal StdLib" }
        )
    }

    fun testCollectCrystalFilesIgnoresNonCrystalFiles() {
        val root = myFixture.tempDirFixture.findOrCreateDir("filtered-root")
        val crystalFile = myFixture.tempDirFixture.createFile("filtered-root/array.cr", "class Array; end")
        myFixture.tempDirFixture.createFile("filtered-root/README.md", "docs")

        assertEquals(listOf(crystalFile), CrystalStdlibIndexRefresher.collectCrystalFiles(listOf(root)))
    }

    fun testCancellationStopsRecursiveCollection() {
        val root = myFixture.tempDirFixture.findOrCreateDir("cancelled-root")
        myFixture.tempDirFixture.createFile("cancelled-root/nested/array.cr", "class Array; end")
        val indicator = CountingProgressIndicator(cancelAtCheck = 1)

        assertCanceled {
            CrystalStdlibIndexRefresher.collectCrystalFiles(listOf(root), indicator)
        }

        assertEquals(1, indicator.checkCount)
    }

    fun testCancellationIsCheckedBeforeEachReindexRequest() {
        myFixture.addFileToProject("main.cr", "puts 1")
        val file = myFixture.tempDirFixture.createFile("cancel-before-request/array.cr", "class Array; end")
        val indicator = CountingProgressIndicator(cancelAtCheck = 2)

        assertCanceled {
            CrystalStdlibIndexRefresher.refresh(project, emptyList(), listOf(file), indicator)
        }

        assertEquals("One traversal check and one pre-request check", 2, indicator.checkCount)
    }

    private fun assertCanceled(action: () -> Unit) {
        try {
            action()
            fail("Expected ProcessCanceledException")
        } catch (_: ProcessCanceledException) {
        }
    }

    private class CountingProgressIndicator(
        private val cancelAtCheck: Int,
    ) : ProgressIndicator by ProgressIndicatorBase() {
        var checkCount = 0
            private set

        override fun checkCanceled() {
            checkCount++
            if (checkCount >= cancelAtCheck) throw ProcessCanceledException()
        }
    }
}
