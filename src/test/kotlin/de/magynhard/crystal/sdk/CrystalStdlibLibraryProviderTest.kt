package de.magynhard.crystal.sdk

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class CrystalStdlibLibraryProviderTest : BasePlatformTestCase() {

    /**
     * Regression test for the workspace-model save loop.
     *
     * The provider is invoked by the platform under the write-intent lock while the
     * workspace model is being recomputed. It must NOT read the workspace model
     * (ModuleManager / ModuleRootManager) — doing so re-enters the model and causes an
     * infinite "workspace model save" loop (write-intent lock).
     *
     * If the provider accidentally touches the model again, this test will either deadlock
     * (timeout) or throw a re-entrant model-access error instead of returning cleanly.
     *
     * NOTE: isCrystalProject reads the on-disk project via LocalFileSystem, so the markers
     * must be real files on disk (not light/in-memory fixtures).
     */
    fun testProviderLoadsStdlibForCrystalProject() {
        CrystalStdlibLibraryProvider.clearCache()
        writeProjectFile("shard.yml", "name: test\nversion: 0.1.0\n")
        writeProjectFile("main.cr", "puts 1\n")

        val libs = CrystalStdlibLibraryProvider().getAdditionalProjectLibraries(project)
        assertEquals("Exactly one stdlib library for a Crystal project", 1, libs.size)

        val stdlibPath = CrystalStdlibResolver.resolveStdlibPath(project)
        assertNotNull("Stdlib path should resolve", stdlibPath)

        val roots = libs.first().sourceRoots
        assertTrue(
            "Stdlib path must be a source root of the provided library",
            roots.any { it.url == stdlibPath!!.url }
        )
    }

    fun testProviderReturnsEmptyForNonCrystalProject() {
        CrystalStdlibLibraryProvider.clearCache()
        // BasePlatformTestCase reuses one project across methods; the positive test may
        // have left shard.yml / .cr markers on disk. Remove them through the VFS (a plain
        // File.delete can fail while the VFS holds the file open).
        val baseVfs = LocalFileSystem.getInstance().findFileByPath(project.basePath!!)
        WriteCommandAction.runWriteCommandAction(project) {
            baseVfs?.findChild("shard.yml")?.delete(this)
            baseVfs?.children?.filter { it.extension == "cr" }?.forEach { it.delete(this) }
        }

        val libs = CrystalStdlibLibraryProvider().getAdditionalProjectLibraries(project)
        assertTrue("No stdlib library for a non-Crystal project", libs.isEmpty())
    }

    private fun writeProjectFile(name: String, text: String) {
        val file = File(project.basePath!!, name)
        file.parentFile?.mkdirs()
        file.writeText(text)
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    }
}
