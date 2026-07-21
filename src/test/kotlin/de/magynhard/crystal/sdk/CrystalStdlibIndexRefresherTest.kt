package de.magynhard.crystal.sdk

import com.intellij.openapi.module.ModuleManager
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

        CrystalStdlibIndexRefresher.refresh(project, emptyList(), roots, true)

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
}
