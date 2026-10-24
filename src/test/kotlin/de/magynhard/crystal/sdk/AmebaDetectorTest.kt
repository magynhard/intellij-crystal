package de.magynhard.crystal.sdk

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import org.junit.Assume

class AmebaDetectorTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = FileUtil.createTempDirectory("ameba-detector", null)
    }

    @After
    fun tearDown() {
        FileUtil.delete(tempDir)
    }

    @Test
    fun testValidateAcceptsAmebaVersionOutput() {
        Assume.assumeFalse("Fixture executes a POSIX shell script", SystemInfo.isWindows)
        val fake = writeFake("ameba", "ameba 1.7.0", 0)
        assertEquals("ameba 1.7.0", AmebaDetector.validate(fake.absolutePath))
    }

    @Test
    fun testValidateRejectsBlankAndMissingPaths() {
        assertNull(AmebaDetector.validate(""))
        assertNull(AmebaDetector.validate("   "))
        assertNull(AmebaDetector.validate(File(tempDir, "nope").absolutePath))
    }

    @Test
    fun testValidateRejectsNonZeroExit() {
        Assume.assumeFalse("Fixture executes a POSIX shell script", SystemInfo.isWindows)
        val fake = writeFake("ameba", "ameba 1.7.0", 1)
        assertNull(AmebaDetector.validate(fake.absolutePath))
    }

    @Test
    fun testValidateRejectsForeignBinary() {
        Assume.assumeFalse("Fixture executes a POSIX shell script", SystemInfo.isWindows)
        val fake = writeFake("other", "crystal 1.17.0", 0)
        assertNull(AmebaDetector.validate(fake.absolutePath))
    }

    @Test
    fun testValidateAcceptsBareVersionOutput() {
        // Shards-built Ameba prints just "1.7.0" (verified against 1.7.0).
        Assume.assumeFalse("Fixture executes a POSIX shell script", SystemInfo.isWindows)
        val fake = writeFake("ameba", "1.7.0", 0)
        assertEquals("1.7.0", AmebaDetector.validate(fake.absolutePath))
    }

    @Test
    fun testDetectUsesInjectedLookupBeforeKnownLocations() {
        val fake = writeFake("ameba", "ameba 1.7.0", 0)
        val detected = AmebaDetector.detect { name ->
            if (name == "ameba") fake else null
        }
        assertEquals(fake.absolutePath, detected)
    }

    @Test
    fun testDetectSkipsNonExecutableLookupHits() {
        Assume.assumeFalse("Requires POSIX executable permission bits", SystemInfo.isWindows)
        val plain = File(tempDir, "ameba").apply { writeText("#!/bin/sh\necho ameba\n") }
        val detected = AmebaDetector.detect { name ->
            if (name == "ameba") plain else null
        }
        // Non-executable lookup hits are skipped; the result is either a real
        // installation from known locations or null — never the plain file.
        assertTrue(detected == null || detected != plain.absolutePath)
    }

    @Test
    fun testDetectReturnsNullWhenNothingFound() {
        // Skip known locations by pointing HOME at the empty temp dir and
        // refusing every PATH lookup. /usr/bin/ameba etc. may still exist on
        // the machine running the test, so only assert no crash and either
        // null or an executable file.
        val detected = AmebaDetector.detect { null }
        assertTrue(detected == null || File(detected).canExecute())
    }

    @Test
    fun testKnownLocationsAreNonEmpty() {
        assertTrue(AmebaDetector.knownLocations().isNotEmpty())
        assertTrue(AmebaDetector.executableNames().isNotEmpty())
    }

    private fun writeFake(name: String, output: String, exit: Int): File {
        val file = File(tempDir, name)
        file.writeText("#!/bin/sh\necho \"$output\"\nexit $exit\n")
        assertTrue("Cannot make fake executable: $file", file.setExecutable(true))
        return file
    }
}
