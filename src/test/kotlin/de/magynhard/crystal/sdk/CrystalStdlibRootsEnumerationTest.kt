package de.magynhard.crystal.sdk

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import java.nio.file.Files

/**
 * Root registration for the builtin macro-method API: the compiler/ tree is
 * excluded from stdlib indexing everywhere else, but
 * compiler/crystal/macros.cr must be a registered root so macro-context calls
 * resolve (run, system, puts, flag?, ...).
 *
 * Uses a temp-dir layout that mimics the Crystal 1.20+ distribution. The
 * compiler/ subtree is deliberately NOT pre-refreshed into the VFS for the
 * findFileByRelativePath query — production relies on a synchronous refresh
 * fallback in that case, which is disabled under unit tests (VFS root-access
 * assertions), so the test pre-registers the file itself and validates the
 * root registration given a VFS-present file.
 */
class CrystalStdlibRootsEnumerationTest : BasePlatformTestCase() {

    fun testEnumerationIncludesCompilerMacrosCr() {
        val tempRoot = Files.createTempDirectory("crystal-stdlib-enumeration").toFile()
        val macrosFile = java.io.File(tempRoot, "compiler/crystal/macros.cr")
        macrosFile.parentFile.mkdirs()
        macrosFile.writeText("module Crystal::Macros\n  def run(filename, *args) : MacroId\n  end\nend\n")
        java.io.File(tempRoot, "prelude.cr").writeText("// prelude")
        Files.createDirectory(java.io.File(tempRoot, "llvm").toPath())

        de.magynhard.crystal.CrystalTestVfsRoots.ensureStdlibRootAllowed()
        VfsRootAccess.allowRootAccess(testRootDisposable, tempRoot.absolutePath)
        val rootVFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByIoFile(tempRoot) ?: return

        // Bring the macro file into the VFS the way production's refresh
        // fallback would (unit tests skip that fallback).
        com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByIoFile(macrosFile) ?: return

        runInEdtAndWait {
            val roots = CrystalStdlibRoots.enumerate(rootVFile)
            val paths = roots.map { it.path }
            assertTrue(
                "enumerate must register compiler/crystal/macros.cr, got: $paths",
                paths.any { it.endsWith("compiler/crystal/macros.cr") })
            assertFalse("llvm/ must stay excluded", paths.any { it.endsWith("/llvm") })
            assertTrue("prelude.cr must be registered as an individual root",
                paths.any { it.endsWith("prelude.cr") })
        }
    }
}
