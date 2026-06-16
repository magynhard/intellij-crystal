package de.magynhard.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalStdlibResolverTest : BasePlatformTestCase() {

    fun testResolveStdlibPath() {
        val path = CrystalStdlibResolver.resolveStdlibPath(project)
        assertNotNull("Should resolve stdlib path when Crystal is installed", path)
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
}
