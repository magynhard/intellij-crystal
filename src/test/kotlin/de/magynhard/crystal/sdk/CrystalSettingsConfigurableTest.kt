package de.magynhard.crystal.sdk

import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CrystalSettingsConfigurableTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean = false

    fun testApplyInvalidatesRequireGraphAfterPublishingNewRoots() {
        val context = myFixture.addFileToProject("src/main.cr", "")
        val service = CrystalRequireGraphService.getInstance(project)
        val before = service.cacheStats()
        var invalidationsAtRootPublication = -1L
        var nodeBuildsAtRootPublication = -1L
        project.messageBus.connect(testRootDisposable).subscribe(
            AdditionalLibraryRootsListener.TOPIC,
            AdditionalLibraryRootsListener { _, _, _, _ ->
                invalidationsAtRootPublication = service.cacheStats().fullInvalidations
                service.effectiveSources(context)
                nodeBuildsAtRootPublication = service.cacheStats().nodeBuilds
            },
        )
        val configurable = CrystalSettingsConfigurable(project)
        val field = TextFieldWithBrowseButton().apply {
            text = CrystalSettings.getInstance(project).state.crystalPath
        }
        configurable.javaClass.getDeclaredField("crystalPathField").apply {
            isAccessible = true
            set(configurable, field)
        }

        configurable.apply()

        assertEquals(before.fullInvalidations + 1, invalidationsAtRootPublication)
        assertEquals(before.fullInvalidations + 2, service.cacheStats().fullInvalidations)
        assertTrue(nodeBuildsAtRootPublication > before.nodeBuilds)
        val afterApply = service.cacheStats()
        service.effectiveSources(context)
        assertTrue(service.cacheStats().nodeBuilds > afterApply.nodeBuilds)
    }

    fun testApplyInvalidatesSdkABeforeDiscoveringAndPublishingSdkB() {
        val tempDirectory = Files.createTempDirectory("crystal-settings-sdk-transition")
        val settings = CrystalSettings.getInstance(project)
        val originalCrystalPath = settings.state.crystalPath
        val enteredDiscovery = tempDirectory.resolve("entered-discovery")
        val releaseDiscovery = tempDirectory.resolve("release-discovery")
        try {
            val sdkA = createFakeSdk(tempDirectory, "sdk-a")
            val sdkB = createFakeSdk(
                tempDirectory,
                "sdk-b",
                "touch \"$enteredDiscovery\"\nwhile [ ! -f \"$releaseDiscovery\" ]; do sleep 0.05; done",
            )
            val context = myFixture.addFileToProject("main.cr", "require \"feature\"")
            settings.state.crystalPath = sdkA.executable.toString()
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            val rootA = requireNotNull(CrystalStdlibResolver.resolveStdlibPath(project))
            val service = CrystalRequireGraphService.getInstance(project)
            assertTrue(service.effectiveSources(context).files.contains(requireNotNull(rootA.findChild("feature.cr"))))
            val before = service.cacheStats()
            val configurable = configurable(sdkB.executable.toString())

            val apply = CompletableFuture.runAsync(configurable::apply, AppExecutorUtil.getAppExecutorService())
            assertTrue(waitForFile(enteredDiscovery))

            assertEquals(before.fullInvalidations + 1, service.cacheStats().fullInvalidations)
            val duringDiscovery = service.effectiveSources(context)
            assertFalse(duringDiscovery.files.any { it.path.startsWith(sdkA.stdlib.toString()) })

            Files.createFile(releaseDiscovery)
            apply.get(10, TimeUnit.SECONDS)
            val afterApply = service.effectiveSources(context)
            assertFalse(afterApply.files.any { it.path.startsWith(sdkA.stdlib.toString()) })
            assertTrue(afterApply.files.any { it.path.startsWith(sdkB.stdlib.toString()) && it.name == "feature.cr" })
            assertTrue(service.cacheStats().fullInvalidations >= before.fullInvalidations + 3)
        } finally {
            if (!Files.exists(releaseDiscovery)) Files.createFile(releaseDiscovery)
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            tempDirectory.toFile().deleteRecursively()
        }
    }

    fun testApplyPublishesSdkBBeforeCallbackQueryAndInvalidatesAgainAfterwards() {
        val tempDirectory = Files.createTempDirectory("crystal-settings-sdk-publication")
        val settings = CrystalSettings.getInstance(project)
        val originalCrystalPath = settings.state.crystalPath
        try {
            val sdkA = createFakeSdk(tempDirectory, "sdk-a")
            val sdkB = createFakeSdk(tempDirectory, "sdk-b")
            val context = myFixture.addFileToProject("main.cr", "require \"feature\"")
            settings.state.crystalPath = sdkA.executable.toString()
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            val rootA = requireNotNull(CrystalStdlibResolver.resolveStdlibPath(project))
            val service = CrystalRequireGraphService.getInstance(project)
            assertTrue(service.effectiveSources(context).files.contains(requireNotNull(rootA.findChild("feature.cr"))))
            val before = service.cacheStats()
            var invalidationsBeforeCallbackQuery = -1L
            var callbackContainsA = true
            var callbackContainsB = false
            project.messageBus.connect(testRootDisposable).subscribe(
                AdditionalLibraryRootsListener.TOPIC,
                AdditionalLibraryRootsListener { _, _, _, _ ->
                    invalidationsBeforeCallbackQuery = service.cacheStats().fullInvalidations
                    val sources = service.effectiveSources(context)
                    callbackContainsA = sources.files.any { it.path.startsWith(sdkA.stdlib.toString()) }
                    callbackContainsB = sources.files.any {
                        it.path.startsWith(sdkB.stdlib.toString()) && it.name == "feature.cr"
                    }
                },
            )

            configurable(sdkB.executable.toString()).apply()

            assertEquals(before.fullInvalidations + 1, invalidationsBeforeCallbackQuery)
            assertFalse(callbackContainsA)
            assertTrue(callbackContainsB)
            assertTrue(service.cacheStats().fullInvalidations >= invalidationsBeforeCallbackQuery + 2)
        } finally {
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            tempDirectory.toFile().deleteRecursively()
        }
    }

    fun testApplyFinallyInvalidatesWhenRootPublicationListenerThrows() {
        val context = myFixture.addFileToProject("main.cr", "")
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        val service = CrystalRequireGraphService.getInstance(project)
        service.effectiveSources(context)
        val before = service.cacheStats()
        val configurable = CrystalSettingsConfigurable.forTest(
            project,
            { _, _ -> throw IllegalStateException("publication failed") },
        )

        var errorMessage: String? = null
        try {
            val field = TextFieldWithBrowseButton().apply {
                text = CrystalSettings.getInstance(project).state.crystalPath
            }
            configurable.javaClass.getDeclaredField("crystalPathField").apply {
                isAccessible = true
                set(configurable, field)
            }
            configurable.apply()
            fail("Publication failure must propagate")
        } catch (error: IllegalStateException) {
            errorMessage = error.message
        }

        assertEquals("publication failed", errorMessage)
        assertEquals(before.fullInvalidations + 2, service.cacheStats().fullInvalidations)
    }

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

    private fun configurable(crystalPath: String): CrystalSettingsConfigurable {
        val configurable = CrystalSettingsConfigurable(project)
        val field = TextFieldWithBrowseButton().apply { text = crystalPath }
        configurable.javaClass.getDeclaredField("crystalPathField").apply {
            isAccessible = true
            set(configurable, field)
        }
        return configurable
    }

    private fun createFakeSdk(parent: Path, name: String, beforeOutput: String = ""): FakeSdk {
        val directory = Files.createDirectory(parent.resolve(name))
        val stdlib = Files.createDirectory(directory.resolve("stdlib"))
        Files.writeString(stdlib.resolve("prelude.cr"), "")
        Files.writeString(stdlib.resolve("feature.cr"), "")
        val executable = Files.writeString(
            directory.resolve("crystal"),
            "#!/bin/sh\n$beforeOutput\necho \"$stdlib\"\n",
        )
        assertTrue(executable.toFile().setExecutable(true))
        return FakeSdk(executable, stdlib)
    }

    private fun waitForFile(path: Path): Boolean {
        repeat(200) {
            if (Files.exists(path)) return true
            Thread.sleep(25)
        }
        return false
    }

    private data class FakeSdk(val executable: Path, val stdlib: Path)
}
