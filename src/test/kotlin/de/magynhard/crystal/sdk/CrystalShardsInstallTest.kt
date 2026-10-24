package de.magynhard.crystal.sdk

import com.intellij.openapi.util.SystemInfo
import org.junit.Assume
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files

class CrystalShardsInstallTest {

    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun testFindsSiblingOfCrystalBinary() {
        val dir = newTempDir()
        File(dir, "crystal").writeText("#!/bin/sh\n")
        val shards = File(dir, "shards").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        val found = CrystalShardsInstall.findShardsExecutable(File(dir, "crystal").absolutePath) { null }
        assertEquals(shards.absolutePath, found?.absolutePath)
    }

    @Test
    fun testFallsBackToPathLookup() {
        val dir = newTempDir()
        val shards = File(dir, "shards").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        val found = CrystalShardsInstall.findShardsExecutable("/nonexistent/crystal") { name ->
            if (name == "shards") shards else null
        }
        assertEquals(shards.absolutePath, found?.absolutePath)
    }

    @Test
    fun testMissingEverywhereYieldsNull() {
        assertNull(CrystalShardsInstall.findShardsExecutable("/nonexistent/crystal") { null })
    }

    @Test
    fun testSkipsNonExecutableSibling() {
        Assume.assumeFalse("Requires POSIX executable permission bits", SystemInfo.isWindows)
        val dir = newTempDir()
        File(dir, "crystal").writeText("#!/bin/sh\n")
        File(dir, "shards").writeText("#!/bin/sh\n")
        assertNull(CrystalShardsInstall.findShardsExecutable(File(dir, "crystal").absolutePath) { null })
    }

    private fun newTempDir(): File {
        val dir = Files.createTempDirectory("shards-probe").toFile()
        tempDirs.add(dir)
        return dir
    }
}
