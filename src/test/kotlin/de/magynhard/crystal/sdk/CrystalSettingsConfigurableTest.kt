package de.magynhard.crystal.sdk

import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CrystalSettingsConfigurableTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean = false

    override fun setUp() {
        super.setUp()
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { null }
        CrystalRequireGraphService.installForTests(
            project,
            CrystalRequirePathResolver(project) { CrystalStdlibResolver.cachedStdlibPath(project) },
            testRootDisposable,
        )
    }

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
        val rootA = fixtureStdlib("transition-sdk-a")
        val rootB = fixtureStdlib("transition-sdk-b")
        val enteredDiscovery = CountDownLatch(1)
        val releaseDiscovery = CountDownLatch(1)
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) {
            enteredDiscovery.countDown()
            assertTrue(releaseDiscovery.await(10, TimeUnit.SECONDS))
            rootB
        }
        val context = myFixture.addFileToProject("transition-main.cr", "require \"feature\"")
        CrystalStdlibResolver.publishStdlibPath(project, rootA)
        val service = CrystalRequireGraphService.getInstance(project)
        val initialSources = service.effectiveSources(context).files
        assertTrue("Expected ${rootA.path} in $initialSources", initialSources.contains(requireNotNull(rootA.findChild("prelude.cr"))))
        val before = service.cacheStats()

        val apply = CompletableFuture.runAsync(configurable("sdk-b")::apply, AppExecutorUtil.getAppExecutorService())
        assertTrue(enteredDiscovery.await(10, TimeUnit.SECONDS))

        assertEquals(before.fullInvalidations + 1, service.cacheStats().fullInvalidations)
        assertFalse(service.effectiveSources(context).files.any { it.path.startsWith(rootA.path) })

        releaseDiscovery.countDown()
        apply.get(10, TimeUnit.SECONDS)
        val afterApply = service.effectiveSources(context)
        assertFalse(afterApply.files.any { it.path.startsWith(rootA.path) })
        assertTrue(afterApply.files.any { it.path.startsWith(rootB.path) && it.name == "prelude.cr" })
        assertTrue(service.cacheStats().fullInvalidations >= before.fullInvalidations + 3)
    }

    fun testApplyPublishesSdkBBeforeCallbackQueryAndInvalidatesAgainAfterwards() {
        val rootA = fixtureStdlib("publication-sdk-a")
        val rootB = fixtureStdlib("publication-sdk-b")
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { rootB }
            val context = myFixture.addFileToProject("publication-main.cr", "require \"feature\"")
            CrystalStdlibResolver.publishStdlibPath(project, rootA)
            val service = CrystalRequireGraphService.getInstance(project)
            val initialSources = service.effectiveSources(context).files
            assertTrue("Expected ${rootA.path} in $initialSources", initialSources.contains(requireNotNull(rootA.findChild("prelude.cr"))))
            val before = service.cacheStats()
            var invalidationsBeforeCallbackQuery = -1L
            var callbackContainsA = true
            var callbackContainsB = false
            project.messageBus.connect(testRootDisposable).subscribe(
                AdditionalLibraryRootsListener.TOPIC,
                AdditionalLibraryRootsListener { _, _, _, _ ->
                    invalidationsBeforeCallbackQuery = service.cacheStats().fullInvalidations
                    val sources = service.effectiveSources(context)
                    callbackContainsA = sources.files.any { it.path.startsWith(rootA.path) }
                    callbackContainsB = sources.files.any {
                        it.path.startsWith(rootB.path) && it.name == "prelude.cr"
                    }
                },
            )

            configurable("sdk-b").apply()

            assertEquals(before.fullInvalidations + 1, invalidationsBeforeCallbackQuery)
            assertFalse(callbackContainsA)
            assertTrue(callbackContainsB)
            assertTrue(service.cacheStats().fullInvalidations >= invalidationsBeforeCallbackQuery + 2)
    }

    fun testApplyFailedSdkBDiscoveryDoesNotRestoreSdkA() {
        val rootA = fixtureStdlib("failed-sdk-a")
        CrystalStdlibResolver.publishStdlibPath(project, rootA)
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { null }
        val context = myFixture.addFileToProject("failed-main.cr", "require \"feature\"")
        val service = CrystalRequireGraphService.getInstance(project)
        assertTrue(service.effectiveSources(context).files.any { it.path.startsWith(rootA.path) })
        val before = service.cacheStats()
        var publishedRoots: List<com.intellij.openapi.vfs.VirtualFile>? = null
        val configurable = CrystalSettingsConfigurable.forTest(project) { _, newRoots -> publishedRoots = newRoots }
        setCrystalPath(configurable, "missing-sdk-b")

        configurable.apply()

        assertNull(CrystalStdlibResolver.cachedStdlibPath(project))
        assertEquals(emptyList<com.intellij.openapi.vfs.VirtualFile>(), publishedRoots)
        assertFalse(service.effectiveSources(context).files.any { it.path.startsWith(rootA.path) })
        assertTrue(service.cacheStats().fullInvalidations >= before.fullInvalidations + 2)
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
            val stdlib = fixtureStdlib("force-reindex")
            CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { stdlib }
            val rootsChanged = CountDownLatch(1)
            project.messageBus.connect(testRootDisposable).subscribe(
                AdditionalLibraryRootsListener.TOPIC,
                AdditionalLibraryRootsListener { _, _, _, _ -> rootsChanged.countDown() }
            )
            val configurable = CrystalSettingsConfigurable(project)

            configurable.javaClass.getDeclaredMethod("forceReindex").apply { isAccessible = true }.invoke(configurable)

            assertFalse("Non-Crystal project must not broadcast stdlib roots", rootsChanged.await(2, TimeUnit.SECONDS))
    }

    private fun configurable(crystalPath: String): CrystalSettingsConfigurable {
        val configurable = CrystalSettingsConfigurable(project)
        setCrystalPath(configurable, crystalPath)
        return configurable
    }

    private fun setCrystalPath(configurable: CrystalSettingsConfigurable, crystalPath: String) {
        val field = TextFieldWithBrowseButton().apply { text = crystalPath }
        configurable.javaClass.getDeclaredField("crystalPathField").apply {
            isAccessible = true
            set(configurable, field)
        }
    }

    private fun fixtureStdlib(name: String): com.intellij.openapi.vfs.VirtualFile {
        val prelude = myFixture.addFileToProject("$name/prelude.cr", "").virtualFile
        myFixture.addFileToProject("$name/feature.cr", "")
        return requireNotNull(prelude.parent)
    }
}
