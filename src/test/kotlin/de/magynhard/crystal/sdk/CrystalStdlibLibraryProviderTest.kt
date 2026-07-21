package de.magynhard.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

class CrystalStdlibLibraryProviderTest : BasePlatformTestCase() {

    fun testProviderReturnsOnlyFilteredRootsForFlatStdlibLayout() {
        myFixture.addFileToProject("main.cr", "puts 1")
        val tempDir = Files.createTempDirectory("crystal-stdlib-provider-test")
        val settings = CrystalSettings.getInstance(project)
        val originalCrystalPath = settings.state.crystalPath
        try {
            val stdlib = Files.createDirectory(tempDir.resolve("crystal-stdlib"))
            Files.writeString(stdlib.resolve("array.cr"), "class Array; end")
            Files.createDirectories(stdlib.resolve("json"))
            Files.writeString(stdlib.resolve("json/parser.cr"), "module JSON; end")
            for (excluded in listOf("compiler", "crystal", "lib_c", "lib_z", "ll", "llvm", "gc", "samples")) {
                Files.createDirectories(stdlib.resolve(excluded))
                Files.writeString(stdlib.resolve("$excluded/excluded.cr"), "class Excluded; end")
            }
            val executable = Files.writeString(
                tempDir.resolve("fake-crystal"),
                """
                #!/bin/sh
                if [ "${'$'}1" = "env" ]; then
                  echo "$stdlib"
                else
                  echo "Crystal 1.20.0"
                fi
                """.trimIndent()
            )
            assertTrue(executable.toFile().setExecutable(true))
            settings.state.crystalPath = executable.toString()
            CrystalStdlibResolver.clearCachedStdlibPath(project)

            val library = CrystalStdlibLibraryProvider().getAdditionalProjectLibraries(project).single()
            val rootNames = library.sourceRoots.map { it.name }.toSet()

            assertEquals(setOf("array.cr", "json"), rootNames)
        } finally {
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            File(tempDir.toString()).deleteRecursively()
        }
    }
}
