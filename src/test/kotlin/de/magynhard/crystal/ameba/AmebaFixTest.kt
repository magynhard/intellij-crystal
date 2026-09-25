package de.magynhard.crystal.ameba

import junit.framework.TestCase

class AmebaFixTest : TestCase() {

    fun testCleanWhenExitZeroAndUnchanged() {
        assertEquals(AmebaFix.FixOutcome.CLEAN, AmebaFix.classifyFixOutcome(0, false, false))
        assertEquals(AmebaFix.FixOutcome.CLEAN, AmebaFix.classifyFixOutcome(0, false, true))
    }

    fun testFixedWhenFileChanged() {
        // ameba --fix re-reports corrected issues and exits non-zero even
        // when it fixed everything: file change is the success signal.
        assertEquals(AmebaFix.FixOutcome.FIXED, AmebaFix.classifyFixOutcome(1, true, false))
        assertEquals(AmebaFix.FixOutcome.FIXED, AmebaFix.classifyFixOutcome(0, true, false))
        assertEquals(AmebaFix.FixOutcome.FIXED, AmebaFix.classifyFixOutcome(1, true, true))
    }

    fun testFailedWhenErrorOutputAndUnchanged() {
        assertEquals(AmebaFix.FixOutcome.FAILED, AmebaFix.classifyFixOutcome(1, false, true))
        assertEquals(AmebaFix.FixOutcome.FAILED, AmebaFix.classifyFixOutcome(2, false, true))
    }

    fun testUnchangedWhenNoErrorOutputAndUnchanged() {
        // Ran fine, nothing correctable (e.g. syntax errors): general
        // info instead of a progress dump.
        assertEquals(AmebaFix.FixOutcome.UNCHANGED, AmebaFix.classifyFixOutcome(1, false, false))
    }

    fun testStripAnsiRemovesColorEscapes() {
        assertEquals(
            "F test2.cr:1:1 [Corrected]",
            stripAnsi("\u001B[31mF\u001B[39m \u001B[36mtest2.cr:1:1\u001B[39m \u001B[32m[Corrected]\u001B[39m")
        )
        assertEquals("plain text", stripAnsi("plain text"))
        assertEquals("", stripAnsi(""))
    }
}
