package de.magynhard.crystal.analysis

import junit.framework.TestCase

class CrystalStringLiteralDecoderTest : TestCase() {

    fun testDecodesNamedAndPassThroughEscapes() {
        assertEquals("a\u0007\b\u001b\u000c\n\r\t\u000b\"\\#q", decode("a\\a\\b\\e\\f\\n\\r\\t\\v\\\"\\\\\\#\\q"))
    }

    fun testDecodesOctalHexAndUnicodeEscapes() {
        assertEquals("//json/A", decode("\\057\\x2fjson\\u002f\\u{41}"))
        assertEquals("A😀", decode("\\u{41 1F600}"))
        assertEquals("\u0000", decode("\\0000"))
    }

    fun testDecodesEscapedNewlineContinuation() {
        assertEquals("json", decode("js\\\non"))
        assertEquals("json", decode("js\\\r\non"))
        assertEquals("json", decode("js\\\n \t on"))
        assertNull(decode("json\\\n  \t"))
    }

    fun testRejectsInvalidUnicodeAndIncompleteSequences() {
        assertNull(decode("\\xF"))
        assertNull(decode("\\u{110000}"))
        assertNull(decode("\\u{D800}"))
        assertNull(decode("\\u{}"))
        assertNull(decode("\\u{1234567}"))
        assertNull(decode("\\u{10FFFF 41}"))
        assertNull(decode("\\u{41"))
        assertNull(decode("\\u{41\t42}"))
        assertNull(decode("\\u{41  42}"))
        assertNull(decode("\\u{41,42}"))
        assertNull(decode("\\uD800"))
        assertNull(decode("\\u12"))
        assertNull(decode("\\x"))
        assertNull(decode("\\400"))
        assertNull(decode("\\7777"))
    }

    private fun decode(content: String): String? = CrystalStringLiteralDecoder.decode(content)
}
