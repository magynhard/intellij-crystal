package de.magynhard.crystal.sdk

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files

class CrystalShardsInstallTest : BasePlatformTestCase() {

    private val tempDirs = mutableListOf<File>()

    override fun tearDown() {
        try {
            tempDirs.forEach { it.deleteRecursively() }
        } finally {
            super.tearDown()
        }
    }

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

    fun testMissingEverywhereYieldsNull() {
        assertNull(CrystalShardsInstall.findShardsExecutable("/nonexistent/crystal") { null })
    }

    fun testSkipsNonExecutableSibling() {
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
