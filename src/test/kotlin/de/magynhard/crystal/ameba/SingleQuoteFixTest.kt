package de.magynhard.crystal.ameba

import junit.framework.TestCase

class SingleQuoteFixTest : TestCase() {

    fun testConvertsMultiCharLiteral() {
        val fix = findSingleQuoteFix("x = 'ab'", 4)
        assertNotNull(fix)
        assertEquals(4, fix!!.range.startOffset)
        assertEquals(8, fix.range.endOffset)
        assertEquals("\"ab\"", fix.replacement)
    }

    fun testLeavesValidSingleCharLiteral() {
        assertNull(findSingleQuoteFix("x = 'a'", 4))
    }

    fun testLeavesValidEscapeLiteral() {
        assertNull(findSingleQuoteFix("x = '\\n'", 4))
    }

    fun testConvertsEmptyLiteral() {
        val fix = findSingleQuoteFix("x = ''", 4)
        assertNotNull(fix)
        assertEquals("\"\"", fix!!.replacement)
    }

    fun testConvertsUnterminatedAtEndOfLine() {
        val fix = findSingleQuoteFix("x = 'ab", 4)
        assertNotNull(fix)
        assertEquals("\"ab\"", fix!!.replacement)
    }

    fun testSkipsUnterminatedWithCodeAfter() {
        // Intent unknowable — code must never be swallowed into a string.
        assertNull(findSingleQuoteFix("x = 'ab + y", 4))
    }

    fun testRequiresQuoteAtOffset() {
        assertNull(findSingleQuoteFix("x = 'ab'", 0))
        assertNull(findSingleQuoteFix("x = 'ab'", 5))
        assertNull(findSingleQuoteFix("", 0))
        assertNull(findSingleQuoteFix("x = 'ab'", 100))
    }

    fun testEscapesInnerDoubleQuotes() {
        val fix = findSingleQuoteFix("x = 'say \"hi\"'", 4)
        assertNotNull(fix)
        assertEquals("\"say \\\"hi\\\"\"", fix!!.replacement)
    }

    fun testEscapesInterpolation() {
        // #{...} would activate in a double-quoted string.
        val fix = findSingleQuoteFix("x = '#{y}'", 4)
        assertNotNull(fix)
        assertEquals("\"\\#{y}\"", fix!!.replacement)
    }

    fun testPreservesExistingEscapes() {
        val fix = findSingleQuoteFix("x = 'a\\nb'", 4)
        assertNotNull(fix)
        assertEquals("\"a\\nb\"", fix!!.replacement)
    }

    fun testDropsTrailingLoneBackslash() {
        val fix = findSingleQuoteFix("x = 'ab\\", 4)
        assertNotNull(fix)
        assertEquals("\"ab\"", fix!!.replacement)
    }

    fun testShouldOfferQuoteFix() {
        val syntax = AmebaIssue("Lint/Syntax", AmebaSeverity.ERROR, "m", 1, 5, null, null)
        assertTrue(shouldOfferQuoteFix(syntax, '\''))
        assertTrue(shouldOfferQuoteFix(syntax.copy(ruleName = "Syntax"), '\''))
        assertTrue(shouldOfferQuoteFix(syntax.copy(ruleName = "LINT/SYNTAX"), '\''))
        assertFalse(shouldOfferQuoteFix(syntax, 'x'))
        assertFalse(shouldOfferQuoteFix(syntax, null))
        assertFalse(
            shouldOfferQuoteFix(syntax.copy(ruleName = "Style/RedundantReturn"), '\'')
        )
    }
}
