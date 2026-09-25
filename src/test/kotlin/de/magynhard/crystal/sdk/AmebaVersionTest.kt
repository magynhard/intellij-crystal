package de.magynhard.crystal.sdk

import junit.framework.TestCase

class AmebaVersionTest : TestCase() {

    fun testParsesPlainVersionOutput() {
        assertEquals("1.7.0", AmebaVersion.parse("ameba 1.7.0"))
        assertEquals("1.6.4", AmebaVersion.parse("ameba 1.6.4\n"))
    }

    fun testParsesVersionWithGitSha() {
        // 1.7.0 prints the commit SHA after the version.
        assertEquals("1.7.0", AmebaVersion.parse("ameba 1.7.0 (9a19f5f)"))
    }

    fun testParseRejectsGarbage() {
        assertNull(AmebaVersion.parse(null))
        assertNull(AmebaVersion.parse(""))
        assertNull(AmebaVersion.parse("   "))
        assertNull(AmebaVersion.parse("no version here"))
    }

    fun testMeetsMinimum() {
        assertTrue(AmebaVersion.meetsMinimum("ameba 1.7.0"))
        assertTrue(AmebaVersion.meetsMinimum("ameba 1.7.0 (9a19f5f)"))
        assertTrue(AmebaVersion.meetsMinimum("ameba 1.8.0"))
        assertFalse(AmebaVersion.meetsMinimum("ameba 1.6.4"))
        assertFalse(AmebaVersion.meetsMinimum("ameba 0.14.0"))
    }

    fun testPreReleasesDoNotMeetMinimum() {
        assertFalse(AmebaVersion.meetsMinimum("ameba 1.7.0-dev"))
    }

    fun testUnparsableFailsClosed() {
        assertFalse(AmebaVersion.meetsMinimum(null))
        assertFalse(AmebaVersion.meetsMinimum(""))
        assertFalse(AmebaVersion.meetsMinimum("ameba"))
    }

    fun testPinAllowsMinimum() {
        assertNull(AmebaVersion.pinAllowsMinimum(null))
        assertNull(AmebaVersion.pinAllowsMinimum(""))
        assertEquals(true, AmebaVersion.pinAllowsMinimum(">= 1.5.0"))
        assertEquals(true, AmebaVersion.pinAllowsMinimum("~> 1.7.0"))
        assertEquals(false, AmebaVersion.pinAllowsMinimum("1.6.0"))
        assertEquals(false, AmebaVersion.pinAllowsMinimum("~> 1.6.0"))
        assertEquals(false, AmebaVersion.pinAllowsMinimum("< 1.7.0"))
    }

    fun testWarningForRawVersion() {
        assertNull(AmebaVersion.warningForRawVersion("/bin/ameba", null))
        assertNull(AmebaVersion.warningForRawVersion("/bin/ameba", ""))
        assertNull(AmebaVersion.warningForRawVersion("/bin/ameba", "ameba 1.7.0"))
        assertNull(AmebaVersion.warningForRawVersion("/bin/ameba", "ameba 1.9.2 (abc1234)"))
        val old = AmebaVersion.warningForRawVersion("/bin/ameba", "ameba 1.6.4")
        assertNotNull(old)
        assertTrue(old!!.contains("1.6.4") && old.contains("1.7.0"))
        val unknown = AmebaVersion.warningForRawVersion("/bin/ameba", "ameba")
        assertNotNull(unknown)
        assertTrue(unknown!!.contains("/bin/ameba"))
    }
}
