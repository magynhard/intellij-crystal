package de.magynhard.crystal.sdk

import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CrystalSettingsConfigurableTest : BasePlatformTestCase() {

    fun testForceReindexDoesNothingOutsideCrystalProject() {
        val tempDir = Files.createTempDirectory("crystal-settings-reindex-test")
        val settings = CrystalSettings.getInstance(project)
        val originalCrystalPath = settings.state.crystalPath
        try {
            val stdlib = Files.createDirectory(tempDir.resolve("stdlib"))
            Files.writeString(stdlib.resolve("array.cr"), "class Array; end")
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
            val rootsChanged = CountDownLatch(1)
            project.messageBus.connect(testRootDisposable).subscribe(
                AdditionalLibraryRootsListener.TOPIC,
                AdditionalLibraryRootsListener { _, _, _, _ -> rootsChanged.countDown() }
            )
            val configurable = CrystalSettingsConfigurable(project)
            configurable.createComponent()

            configurable.javaClass.getDeclaredMethod("forceReindex").apply { isAccessible = true }.invoke(configurable)

            assertFalse("Non-Crystal project must not broadcast stdlib roots", rootsChanged.await(2, TimeUnit.SECONDS))
        } finally {
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            File(tempDir.toString()).deleteRecursively()
        }
    }
}
