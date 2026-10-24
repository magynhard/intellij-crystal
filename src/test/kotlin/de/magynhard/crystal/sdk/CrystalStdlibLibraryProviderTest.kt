package de.magynhard.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.nio.file.Files

class CrystalStdlibLibraryProviderTest : BasePlatformTestCase() {

    fun testProviderReturnsNoLibraryWhenStdlibIsUnavailable() {
        myFixture.addFileToProject("main.cr", "puts 1")
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { null }
        var versionQueries = 0
        CrystalStdlibResolver.installVersionForTests(project, testRootDisposable) {
            versionQueries++
            "Crystal 1.20.0"
        }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        assertEmpty(CrystalStdlibLibraryProvider().getAdditionalProjectLibraries(project))
        assertEquals("No version process is needed without a stdlib root", 0, versionQueries)
    }

    fun testProviderReturnsOnlyFilteredRootsForFlatStdlibLayout() {
        myFixture.addFileToProject("main.cr", "puts 1")
        val tempDir = Files.createTempDirectory("crystal-stdlib-provider-test")
        try {
            val stdlib = Files.createDirectory(tempDir.resolve("crystal-stdlib"))
            Files.writeString(stdlib.resolve("prelude.cr"), "")
            Files.writeString(stdlib.resolve("array.cr"), "class Array; end")
            Files.createDirectories(stdlib.resolve("json"))
            Files.writeString(stdlib.resolve("json/parser.cr"), "module JSON; end")
            // Compiler and runtime trees are indexed; only the C ABI/LLVM
            // bindings subtrees stay excluded.
            Files.createDirectories(stdlib.resolve("crystal"))
            for (excluded in listOf("lib_c", "lib_z", "ll", "llvm", "gc", "samples")) {
                Files.createDirectories(stdlib.resolve(excluded))
                Files.writeString(stdlib.resolve("$excluded/excluded.cr"), "class Excluded; end")
            }
            val root = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(stdlib))
            CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { root }
            CrystalStdlibResolver.installVersionForTests(project, testRootDisposable) { "Crystal 1.20.0" }
            CrystalStdlibResolver.clearCachedStdlibPath(project)

            val library = CrystalStdlibLibraryProvider().getAdditionalProjectLibraries(project).single()
            val rootNames = library.sourceRoots.map { it.name }.toSet()

            assertEquals(setOf("prelude.cr", "array.cr", "json", "crystal"), rootNames)
        } finally {
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            File(tempDir.toString()).deleteRecursively()
        }
    }
}
