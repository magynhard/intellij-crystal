package de.magynhard.crystal.sdk

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import java.nio.file.Files

/**
 * Root registration for the compiler source tree: shards such as ameba
 * require the compiler syntax tree via wildcard requires and reopen
 * `Crystal::Location`, so the tree must be a registered root — the CLI/C
 * ABI/LLVM bindings subtrees stay excluded.
 *
 * Uses a temp-dir layout that mimics the Crystal 1.20+ distribution.
 */
class CrystalStdlibRootsEnumerationTest : BasePlatformTestCase() {

    fun testEnumerationIncludesCompilerTree() {
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

        runInEdtAndWait {
            val roots = CrystalStdlibRoots.enumerate(rootVFile)
            val paths = roots.map { it.path }
            assertTrue(
                "enumerate must register the compiler tree, got: $paths",
                paths.any { it.endsWith("/compiler") })
            assertTrue(
                "prelude.cr must be registered as an individual root",
                paths.any { it.endsWith("prelude.cr") })
            assertFalse("llvm/ must stay excluded", paths.any { it.endsWith("/llvm") })
        }
    }
}
