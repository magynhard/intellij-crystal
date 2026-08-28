package de.magynhard.crystal.sdk

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class CrystalStdlibResolverTest : BasePlatformTestCase() {


    override fun setUp() {
        super.setUp()
        de.magynhard.crystal.CrystalTestVfsRoots.ensureStdlibRootAllowed()
        val prelude = myFixture.addFileToProject("fixture-stdlib/prelude.cr", "").virtualFile
        myFixture.addFileToProject("fixture-stdlib/string.cr", "class String; end")
        val root = requireNotNull(prelude.parent)
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { root }
        CrystalStdlibResolver.installVersionForTests(project, testRootDisposable) { "Crystal 1.20.0" }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
    }

    fun testResolveStdlibPath() {
        val path = CrystalStdlibResolver.resolveStdlibPath(project)
        assertNotNull("Should resolve the injected stdlib path", path)
        assertTrue("Stdlib path should be a directory", path!!.isDirectory)
        assertTrue("Stdlib path should contain .cr files", path.children.any { it.extension == "cr" })
    }

    fun testResolveCrystalVersion() {
        val version = CrystalStdlibResolver.resolveCrystalVersion(project)
        assertNotNull("Should resolve Crystal version when Crystal is installed", version)
        assertTrue("Version should contain 'Crystal'", version!!.contains("Crystal"))
    }

    fun testIsCrystalProject() {
        // Test with shard.yml
        myFixture.addFileToProject("shard.yml", "name: test")
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project)
        assertNotNull("Should resolve stdlib for Crystal project", stdlibRoot)
    }

    fun testDiscoveryOverridesSkipDisposedFramesDuringOutOfOrderDisposal() {
        val outer = Disposer.newDisposable()
        val middle = Disposer.newDisposable()
        val inner = Disposer.newDisposable()
        val roots = listOf("outer", "middle", "inner").associateWith {
            myFixture.addFileToProject("overrides/$it/prelude.cr", "").virtualFile.parent
        }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        CrystalStdlibResolver.installDiscoveryForTests(project, outer) { roots.getValue("outer") }
        CrystalStdlibResolver.installDiscoveryForTests(project, middle) { roots.getValue("middle") }
        CrystalStdlibResolver.installDiscoveryForTests(project, inner) { roots.getValue("inner") }

        CrystalStdlibResolver.clearCachedStdlibPath(project)
        assertSame(roots.getValue("inner"), CrystalStdlibResolver.resolveStdlibPath(project))
        Disposer.dispose(middle)
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        assertSame(roots.getValue("inner"), CrystalStdlibResolver.resolveStdlibPath(project))
        Disposer.dispose(inner)
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        assertSame(roots.getValue("outer"), CrystalStdlibResolver.resolveStdlibPath(project))

        Disposer.dispose(outer)
    }

    fun testVersionOverridesSkipDisposedFramesDuringOutOfOrderDisposal() {
        val outer = Disposer.newDisposable()
        val middle = Disposer.newDisposable()
        val inner = Disposer.newDisposable()
        CrystalStdlibResolver.installVersionForTests(project, outer) { "outer" }
        CrystalStdlibResolver.installVersionForTests(project, middle) { "middle" }
        CrystalStdlibResolver.installVersionForTests(project, inner) { "inner" }

        assertEquals("inner", CrystalStdlibResolver.resolveCrystalVersion(project))
        Disposer.dispose(middle)
        assertEquals("inner", CrystalStdlibResolver.resolveCrystalVersion(project))
        Disposer.dispose(inner)
        assertEquals("outer", CrystalStdlibResolver.resolveCrystalVersion(project))

        Disposer.dispose(outer)
    }

    fun testDisposedBlockedDiscoveryCannotPublishOverActiveOverride() {
        val rootA = fixtureRoot("race/disposed-a")
        val rootB = fixtureRoot("race/active-b")
        val blocked = Disposer.newDisposable()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        CrystalStdlibResolver.installDiscoveryForTests(project, blocked) {
            entered.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            rootA
        }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        val stale = CompletableFuture.supplyAsync(
            { CrystalStdlibResolver.resolveStdlibPath(project) },
            AppExecutorUtil.getAppExecutorService(),
        )
        assertTrue(entered.await(10, TimeUnit.SECONDS))

        Disposer.dispose(blocked)
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { rootB }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        assertSame(rootB, CrystalStdlibResolver.resolveStdlibPath(project))
        release.countDown()

        assertNull(stale.get(10, TimeUnit.SECONDS))
        assertSame(rootB, CrystalStdlibResolver.cachedStdlibPath(project))
    }

    fun testStaleNullAndFailureTransitionsCannotReplaceActiveDiscovery() {
        assertStaleTransitionDoesNotReplaceActiveRoot("null") { null }
        assertStaleTransitionDoesNotReplaceActiveRoot("failure") { throw IllegalStateException("stale") }
    }

    private fun assertStaleTransitionDoesNotReplaceActiveRoot(
        name: String,
        result: () -> com.intellij.openapi.vfs.VirtualFile?,
    ) {
        val rootB = fixtureRoot("race/$name-b")
        val staleOverride = Disposer.newDisposable()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        CrystalStdlibResolver.installDiscoveryForTests(project, staleOverride) {
            entered.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            result()
        }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        val stale = CompletableFuture.supplyAsync(
            { CrystalStdlibResolver.resolveStdlibPath(project) },
            AppExecutorUtil.getAppExecutorService(),
        )
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        Disposer.dispose(staleOverride)
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { rootB }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        assertSame(rootB, CrystalStdlibResolver.resolveStdlibPath(project))

        release.countDown()
        try {
            assertNull(stale.get(10, TimeUnit.SECONDS))
        } catch (error: ExecutionException) {
            assertEquals("stale", error.cause?.message)
        }
        assertSame(rootB, CrystalStdlibResolver.cachedStdlibPath(project))
    }

    private fun fixtureRoot(path: String): com.intellij.openapi.vfs.VirtualFile =
        requireNotNull(myFixture.addFileToProject("$path/prelude.cr", "").virtualFile.parent)
}
