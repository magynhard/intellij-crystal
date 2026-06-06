package de.magynhard.crystal.formatting

import junit.framework.TestCase

class CrystalFormattingServiceTest : TestCase() {

    private val service = CrystalFormattingService()

    fun testParseFormatErrorWithValidSyntaxError() {
        val stderr = "syntax error in 'STDIN:642:11': invalid regex: PCRE2 does not support \\F, \\L, \\l, \\N{name}, \\U, or \\u at 22"
        val result = service.parseFormatError(stderr, "playground.cr")

        assertTrue("Should mention file name", result.contains("playground.cr"))
        assertTrue("Should mention line", result.contains("line 642"))
        assertTrue("Should mention column", result.contains("column 11"))
        assertTrue("Should include description", result.contains("invalid regex"))
        assertTrue("Should include fix hint", result.contains("fix the syntax error"))
        assertFalse("Should not contain STDIN", result.contains("STDIN"))
    }

    fun testParseFormatErrorWithBlankStderr() {
        val result = service.parseFormatError("", "test.cr")
        assertTrue("Should mention Crystal compiler", result.contains("Crystal compiler"))
        assertTrue("Should mention installed", result.contains("installed"))
    }

    fun testParseFormatErrorWithUnparsableStderr() {
        val stderr = "Some unexpected error occurred"
        val result = service.parseFormatError(stderr, "test.cr")
        assertEquals("Should return raw stderr", stderr, result)
    }

    fun testParseFormatErrorReplacesStdinWithFileName() {
        val stderr = "syntax error in 'STDIN:10:5': unexpected token: end"
        val result = service.parseFormatError(stderr, "foo.cr")
        assertTrue("Should contain real file name", result.contains("foo.cr"))
        assertFalse("Should not contain STDIN", result.contains("STDIN"))
    }
}
