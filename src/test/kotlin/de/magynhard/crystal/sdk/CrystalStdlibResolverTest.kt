package de.magynhard.crystal.sdk

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalStdlibResolverTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
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
}
