package de.magynhard.crystal.sdk

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import junit.framework.TestCase
import java.io.File

class CrystalSdkDetectorTest : TestCase() {

    private lateinit var tempDir: File

    override fun setUp() {
        super.setUp()
        tempDir = FileUtil.createTempDirectory("crystal-sdk", null)
    }

    override fun tearDown() {
        try {
            FileUtil.delete(tempDir)
        } finally {
            super.tearDown()
        }
    }

    fun testSelectFirstWorkingSkipsBrokenVersionManagerShim() {
        if (SystemInfo.isWindows) return
        // asdf shims resolve without an installed version and fail only when
        // executed (exit 126, "No version is set"): accepting one silently
        // disables every stdlib-based feature downstream.
        val broken = writeFake("shim-crystal", "No version is set for command crystal", 126)
        val working = writeFake("crystal", "Crystal 1.21.0 (2026-07-23)", 0)
        assertEquals(
            working.absolutePath,
            CrystalSdkDetector.selectFirstWorking(listOf(broken.absolutePath, working.absolutePath))
        )
    }

    fun testSelectFirstWorkingPrefersFirstWorkingCandidate() {
        if (SystemInfo.isWindows) return
        val first = writeFake("crystal-a", "Crystal 1.20.0", 0)
        val second = writeFake("crystal-b", "Crystal 1.21.0", 0)
        assertEquals(
            first.absolutePath,
            CrystalSdkDetector.selectFirstWorking(listOf(first.absolutePath, second.absolutePath))
        )
    }

    fun testSelectFirstWorkingReturnsNullWhenNothingWorks() {
        if (SystemInfo.isWindows) return
        val broken = writeFake("broken", "No version is set for command crystal", 126)
        assertNull(
            CrystalSdkDetector.selectFirstWorking(
                listOf(broken.absolutePath, File(tempDir, "nope").absolutePath)
            )
        )
    }

    fun testValidateAcceptsCrystalVersionOutput() {
        if (SystemInfo.isWindows) return
        val fake = writeFake("crystal", "Crystal 1.21.0 (2026-07-23)", 0)
        assertEquals("Crystal 1.21.0 (2026-07-23)", CrystalSdkDetector.validate(fake.absolutePath))
    }

    fun testValidateRejectsFailingExecutable() {
        if (SystemInfo.isWindows) return
        val fake = writeFake("crystal", "No version is set for command crystal", 126)
        assertNull(CrystalSdkDetector.validate(fake.absolutePath))
    }

    private fun writeFake(name: String, output: String, exit: Int): File {
        val file = File(tempDir, name)
        file.writeText("#!/bin/sh\necho \"$output\"\nexit $exit\n")
        assertTrue("Cannot make fake executable: $file", file.setExecutable(true))
        return file
    }
}
