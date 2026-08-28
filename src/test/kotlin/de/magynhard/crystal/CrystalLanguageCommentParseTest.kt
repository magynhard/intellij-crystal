package de.magynhard.crystal

import de.magynhard.crystal.injection.CrystalLanguageComment.parse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure unit tests for `# language=` comment parsing (see CrystalLanguageComment).
 * The PSI adjacency rules are covered by the platform test
 * CrystalLanguageCommentInjectionTest.
 */
class CrystalLanguageCommentParseTest {

    fun testSimpleLanguageAttribute() {
        assertEquals("SQL", parse("# language=SQL")!!.languageId)
        assertEquals("", parse("# language=SQL")!!.prefix)
        assertEquals("", parse("# language=SQL")!!.suffix)
    }

    fun testQuotedPrefix() {
        val parsed = parse("""# language=SQL prefix="select """")!!
        assertEquals("SQL", parsed.languageId)
        assertEquals("select ", parsed.prefix)
    }

    fun testBarePrefix() {
        assertEquals("select", parse("# language=SQL prefix=select")!!.prefix)
    }

    fun testEscapedQuotesInQuotedValue() {
        assertEquals("""a"b""", parse("""# language=SQL prefix="a\"b""")!!.prefix)
    }

    fun testControlEscapes() {
        assertEquals("a\nb", parse("""# language=SQL suffix="a\nb"""")!!.suffix)
    }

    fun testPrefixAndSuffixTogether() {
        val parsed = parse("""# language=SQL prefix="select " suffix=" limit 1"""")!!
        assertEquals("select ", parsed.prefix)
        assertEquals(" limit 1", parsed.suffix)
    }

    fun testUppercaseAttributeKeyIsAccepted() {
        assertEquals("SQL", parse("# LANGUAGE=SQL")!!.languageId)
    }

    fun testNoLanguageAttributeIsNull() {
        assertNull(parse("# just a comment"))
        assertNull(parse("#"))
        assertNull(parse("# prefix=x"))
    }

    fun testEmptyLanguageValueIsNull() {
        assertNull(parse("# language="))
        assertNull(parse("# language=\"\""))
    }

    fun testCommentMarkerVariants() {
        assertEquals("SQL", parse("#language=SQL")!!.languageId)
        assertEquals("SQL", parse("  #   language=SQL")!!.languageId)
    }
}
