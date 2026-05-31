package de.magynhard.crystal.lexer

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.psi.CrystalTypes
import org.junit.Assert.*
import org.junit.Test

class CrystalLexerTest {

    private fun tokenize(text: String): List<Pair<IElementType, String>> {
        val lexer = CrystalLexer(null)
        lexer.reset(text, 0, text.length, CrystalLexer.YYINITIAL)
        val tokens = mutableListOf<Pair<IElementType, String>>()
        var tokenType: IElementType? = lexer.advance()
        while (tokenType != null) {
            tokens.add(tokenType to text.substring(lexer.tokenStart, lexer.tokenEnd))
            tokenType = lexer.advance()
        }
        return tokens
    }

    private fun nonWhitespaceTokens(text: String): List<Pair<IElementType, String>> =
        tokenize(text).filter {
            it.first != TokenType.WHITE_SPACE && it.first != CrystalTypes.NEWLINE
        }

    @Test
    fun testKeywords() {
        val keywords = mapOf(
            "def" to CrystalTypes.DEF,
            "class" to CrystalTypes.CLASS,
            "module" to CrystalTypes.MODULE,
            "struct" to CrystalTypes.STRUCT,
            "enum" to CrystalTypes.ENUM,
            "if" to CrystalTypes.IF,
            "elsif" to CrystalTypes.ELSIF,
            "else" to CrystalTypes.ELSE,
            "end" to CrystalTypes.END,
            "while" to CrystalTypes.WHILE,
            "until" to CrystalTypes.UNTIL,
            "unless" to CrystalTypes.UNLESS,
            "case" to CrystalTypes.CASE,
            "when" to CrystalTypes.WHEN,
            "return" to CrystalTypes.RETURN,
            "yield" to CrystalTypes.YIELD,
            "begin" to CrystalTypes.BEGIN,
            "rescue" to CrystalTypes.RESCUE,
            "ensure" to CrystalTypes.ENSURE,
            "nil" to CrystalTypes.NIL,
            "true" to CrystalTypes.TRUE,
            "false" to CrystalTypes.FALSE,
            "self" to CrystalTypes.SELF,
            "super" to CrystalTypes.SUPER,
            "abstract" to CrystalTypes.ABSTRACT,
            "require" to CrystalTypes.REQUIRE,
            "include" to CrystalTypes.INCLUDE,
            "extend" to CrystalTypes.EXTEND,
            "macro" to CrystalTypes.MACRO,
            "is_a?" to CrystalTypes.IS_A,
            "nil?" to CrystalTypes.NIL_QUESTION,
            "responds_to?" to CrystalTypes.RESPONDS_TO,
            "as?" to CrystalTypes.AS_QUESTION,
            "as" to CrystalTypes.AS,
        )
        for ((text, expected) in keywords) {
            val tokens = nonWhitespaceTokens(text)
            assertEquals("Keyword '$text' should produce one token", 1, tokens.size)
            assertEquals("Keyword '$text'", expected, tokens[0].first)
        }
    }

    @Test
    fun testIdentifiers() {
        val cases = mapOf(
            "foo" to CrystalTypes.IDENTIFIER,
            "bar_baz" to CrystalTypes.IDENTIFIER,
            "empty?" to CrystalTypes.IDENTIFIER,
            "save!" to CrystalTypes.IDENTIFIER,
            "_private" to CrystalTypes.IDENTIFIER,
            "MyClass" to CrystalTypes.CONSTANT,
            "HTTP" to CrystalTypes.CONSTANT,
            "@name" to CrystalTypes.INSTANCE_VAR,
            "@@count" to CrystalTypes.CLASS_VAR,
        )
        for ((text, expected) in cases) {
            val tokens = nonWhitespaceTokens(text)
            assertEquals("'$text' should produce one token, got: $tokens", 1, tokens.size)
            assertEquals("'$text'", expected, tokens[0].first)
        }
    }

    @Test
    fun testNumbers() {
        val cases = listOf(
            "42" to CrystalTypes.INTEGER_LITERAL,
            "1_000_000" to CrystalTypes.INTEGER_LITERAL,
            "0xFF" to CrystalTypes.INTEGER_LITERAL,
            "0b1010" to CrystalTypes.INTEGER_LITERAL,
            "0o777" to CrystalTypes.INTEGER_LITERAL,
            "42_i64" to CrystalTypes.INTEGER_LITERAL,
            "3.14" to CrystalTypes.FLOAT_LITERAL,
            "1.0e10" to CrystalTypes.FLOAT_LITERAL,
            "1_f32" to CrystalTypes.FLOAT_LITERAL,
        )
        for ((text, expected) in cases) {
            val tokens = nonWhitespaceTokens(text)
            assertEquals("Number '$text' should produce one token, got: $tokens", 1, tokens.size)
            assertEquals("Number '$text'", expected, tokens[0].first)
        }
    }

    @Test
    fun testStrings() {
        val tokens = nonWhitespaceTokens("\"hello\"")
        assertTrue("String tokens should all be STRING_LITERAL",
            tokens.all { it.first == CrystalTypes.STRING_LITERAL })
    }

    @Test
    fun testStringInterpolation() {
        val tokens = nonWhitespaceTokens("\"hello #{name}\"")
        val types = tokens.map { it.first }
        assertTrue("Should contain STRING_INTERPOLATION_BEGIN", types.contains(CrystalTypes.STRING_INTERPOLATION_BEGIN))
        assertTrue("Should contain STRING_INTERPOLATION_END", types.contains(CrystalTypes.STRING_INTERPOLATION_END))
        assertTrue("Should contain IDENTIFIER for interpolated var", types.contains(CrystalTypes.IDENTIFIER))
    }

    @Test
    fun testComments() {
        val tokens = nonWhitespaceTokens("# this is a comment")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTypes.LINE_COMMENT, tokens[0].first)
    }

    @Test
    fun testSymbols() {
        val tokens = nonWhitespaceTokens(":my_symbol")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTypes.SYMBOL_LITERAL, tokens[0].first)
    }

    @Test
    fun testCharLiteral() {
        val tokens = nonWhitespaceTokens("'a'")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTypes.CHAR_LITERAL, tokens[0].first)
    }

    @Test
    fun testOperators() {
        val cases = mapOf(
            "<=>" to CrystalTypes.SPACESHIP,
            "===" to CrystalTypes.CASE_EQ,
            "==" to CrystalTypes.EQ,
            "!=" to CrystalTypes.NEQ,
            "&&" to CrystalTypes.AND_AND,
            "||" to CrystalTypes.OR_OR,
            "->" to CrystalTypes.ARROW,
            "=>" to CrystalTypes.DOUBLE_ARROW,
            "::" to CrystalTypes.DOUBLE_COLON,
            ".." to CrystalTypes.DOTDOT,
            "..." to CrystalTypes.DOTDOTDOT,
            "**" to CrystalTypes.DOUBLE_STAR,
        )
        for ((text, expected) in cases) {
            val tokens = nonWhitespaceTokens(text)
            assertEquals("Operator '$text' should produce one token, got: $tokens", 1, tokens.size)
            assertEquals("Operator '$text'", expected, tokens[0].first)
        }
    }

    @Test
    fun testComprehensiveFileHasNoBadCharacters() {
        val file = java.io.File("src/test/testData/lexer/comprehensive.cr")
        if (!file.exists()) return
        val text = file.readText()
        val tokens = tokenize(text)
        val badChars = tokens.filter { it.first == TokenType.BAD_CHARACTER }
        assertTrue(
            "Comprehensive test file should have no BAD_CHARACTER tokens, but found: ${badChars.map { "'${it.second}'" }}",
            badChars.isEmpty()
        )
    }

    @Test
    fun testPercentLiterals() {
        // %w(...)
        val tokens = nonWhitespaceTokens("%w(foo bar)")
        assertEquals("First token should be PERCENT_LITERAL_BEGIN", CrystalTypes.PERCENT_LITERAL_BEGIN, tokens[0].first)
        assertEquals("Last token should be PERCENT_LITERAL_END", CrystalTypes.PERCENT_LITERAL_END, tokens.last().first)

        // %i[...]
        val tokens2 = nonWhitespaceTokens("%i[one two]")
        assertEquals(CrystalTypes.PERCENT_LITERAL_BEGIN, tokens2[0].first)
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, tokens2.last().first)

        // %(...)
        val tokens3 = nonWhitespaceTokens("%(hello world)")
        assertEquals(CrystalTypes.PERCENT_LITERAL_BEGIN, tokens3[0].first)
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, tokens3.last().first)
    }

    @Test
    fun testPercentLiteralNesting() {
        // Nested parentheses: %(hello (world))
        val tokens = nonWhitespaceTokens("%(hello (world))")
        assertEquals(CrystalTypes.PERCENT_LITERAL_BEGIN, tokens[0].first)
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, tokens.last().first)
        // Should be exactly 2 non-string tokens (BEGIN and END), rest is content
        val nonContent = tokens.filter {
            it.first == CrystalTypes.PERCENT_LITERAL_BEGIN || it.first == CrystalTypes.PERCENT_LITERAL_END
        }
        assertEquals(2, nonContent.size)
    }

    @Test
    fun testHeredoc() {
        val text = "<<-HEREDOC\n  hello\n  world\n  HEREDOC"
        val tokens = nonWhitespaceTokens(text)
        val types = tokens.map { it.first }
        assertTrue("Should contain HEREDOC_START", types.contains(CrystalTypes.HEREDOC_START))
        assertTrue("Should contain HEREDOC_CONTENT", types.contains(CrystalTypes.HEREDOC_CONTENT))
        assertTrue("Should contain HEREDOC_END", types.contains(CrystalTypes.HEREDOC_END))
    }

    @Test
    fun testHeredocRaw() {
        val text = "<<-'RAW'\n  no #{interpolation}\n  RAW"
        val tokens = nonWhitespaceTokens(text)
        val types = tokens.map { it.first }
        assertTrue("Should contain HEREDOC_START", types.contains(CrystalTypes.HEREDOC_START))
        assertTrue("Should contain HEREDOC_END", types.contains(CrystalTypes.HEREDOC_END))
    }

    @Test
    fun testStringInterpolationTokens() {
        val text = """"Example is #{1+1}""""
        val tokens = nonWhitespaceTokens(text)
        val types = tokens.map { it.first }
        assertTrue("Should contain STRING_INTERPOLATION_BEGIN", types.contains(CrystalTypes.STRING_INTERPOLATION_BEGIN))
        assertTrue("Should contain STRING_INTERPOLATION_END", types.contains(CrystalTypes.STRING_INTERPOLATION_END))
        // Verify the closing } is STRING_INTERPOLATION_END, not RBRACE
        val endIndex = tokens.indexOfFirst { it.first == CrystalTypes.STRING_INTERPOLATION_END }
        assertTrue("STRING_INTERPOLATION_END should be present", endIndex >= 0)
        assertEquals("STRING_INTERPOLATION_END should be '}'", "}", tokens[endIndex].second)
    }

    @Test
    fun testHeredocInterpolation() {
        val text = "<<-HEREDOC\n  hello #" + "{name}\n  HEREDOC"
        val tokens = nonWhitespaceTokens(text)
        val types = tokens.map { it.first }
        assertTrue("Should contain HEREDOC_START", types.contains(CrystalTypes.HEREDOC_START))
        assertTrue("Should contain HEREDOC_CONTENT", types.contains(CrystalTypes.HEREDOC_CONTENT))
        assertTrue("Should contain STRING_INTERPOLATION_BEGIN", types.contains(CrystalTypes.STRING_INTERPOLATION_BEGIN))
        assertTrue("Should contain STRING_INTERPOLATION_END", types.contains(CrystalTypes.STRING_INTERPOLATION_END))
        assertTrue("Should contain IDENTIFIER inside interpolation", types.contains(CrystalTypes.IDENTIFIER))
        assertTrue("Should contain HEREDOC_END", types.contains(CrystalTypes.HEREDOC_END))
    }

    @Test
    fun testHeredocInterpolationReturnsToHeredoc() {
        // After interpolation, remaining content should still be HEREDOC_CONTENT
        val text = "<<-HEREDOC\n  #" + "{x} world\n  HEREDOC"
        val tokens = nonWhitespaceTokens(text)
        val interpolationEnd = tokens.indexOfFirst { it.first == CrystalTypes.STRING_INTERPOLATION_END }
        assertTrue("Should have interpolation end", interpolationEnd >= 0)
        // Next token after interpolation end should be HEREDOC_CONTENT (the " world" part)
        val afterInterpolation = tokens[interpolationEnd + 1]
        assertEquals("After interpolation should be HEREDOC_CONTENT", CrystalTypes.HEREDOC_CONTENT, afterInterpolation.first)
    }

    @Test
    fun testHeredocRawNoInterpolation() {
        // <<-'RAW' should NOT interpolate
        val text = "<<-'RAW'\n  hello #" + "{name}\n  RAW"
        val tokens = nonWhitespaceTokens(text)
        val types = tokens.map { it.first }
        assertTrue("Should contain HEREDOC_START", types.contains(CrystalTypes.HEREDOC_START))
        assertTrue("Should contain HEREDOC_END", types.contains(CrystalTypes.HEREDOC_END))
        assertFalse("Should NOT contain STRING_INTERPOLATION_BEGIN", types.contains(CrystalTypes.STRING_INTERPOLATION_BEGIN))
    }

    @Test
    fun testHeredocMultipleInterpolations() {
        val text = "<<-HEREDOC\n  #" + "{a} and #" + "{b}\n  HEREDOC"
        val tokens = nonWhitespaceTokens(text)
        val interpolationBegins = tokens.count { it.first == CrystalTypes.STRING_INTERPOLATION_BEGIN }
        val interpolationEnds = tokens.count { it.first == CrystalTypes.STRING_INTERPOLATION_END }
        assertEquals("Should have 2 interpolation begins", 2, interpolationBegins)
        assertEquals("Should have 2 interpolation ends", 2, interpolationEnds)
    }

    @Test
    fun testStringInterpolationStillWorks() {
        // Ensure string interpolation still returns to STRING state correctly
        val text = "\"hello #" + "{name} world\""
        val tokens = nonWhitespaceTokens(text)
        val types = tokens.map { it.first }
        assertTrue("Should contain STRING_INTERPOLATION_BEGIN", types.contains(CrystalTypes.STRING_INTERPOLATION_BEGIN))
        assertTrue("Should contain STRING_INTERPOLATION_END", types.contains(CrystalTypes.STRING_INTERPOLATION_END))
        // After interpolation, should have STRING_LITERAL for " world"
        val endIdx = tokens.indexOfFirst { it.first == CrystalTypes.STRING_INTERPOLATION_END }
        val afterInterpolation = tokens[endIdx + 1]
        assertEquals("After interpolation should be STRING_LITERAL", CrystalTypes.STRING_LITERAL, afterInterpolation.first)
    }
}
