package de.magynhard.crystal.lexer

import com.intellij.psi.tree.IElementType
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
            it.first != CrystalTokenTypes.WHITE_SPACE && it.first != CrystalTokenTypes.NEWLINE
        }

    @Test
    fun testKeywords() {
        val keywords = mapOf(
            "def" to CrystalTokenTypes.DEF,
            "class" to CrystalTokenTypes.CLASS,
            "module" to CrystalTokenTypes.MODULE,
            "struct" to CrystalTokenTypes.STRUCT,
            "enum" to CrystalTokenTypes.ENUM,
            "if" to CrystalTokenTypes.IF,
            "elsif" to CrystalTokenTypes.ELSIF,
            "else" to CrystalTokenTypes.ELSE,
            "end" to CrystalTokenTypes.END,
            "while" to CrystalTokenTypes.WHILE,
            "until" to CrystalTokenTypes.UNTIL,
            "unless" to CrystalTokenTypes.UNLESS,
            "case" to CrystalTokenTypes.CASE,
            "when" to CrystalTokenTypes.WHEN,
            "return" to CrystalTokenTypes.RETURN,
            "yield" to CrystalTokenTypes.YIELD,
            "begin" to CrystalTokenTypes.BEGIN,
            "rescue" to CrystalTokenTypes.RESCUE,
            "ensure" to CrystalTokenTypes.ENSURE,
            "nil" to CrystalTokenTypes.NIL,
            "true" to CrystalTokenTypes.TRUE,
            "false" to CrystalTokenTypes.FALSE,
            "self" to CrystalTokenTypes.SELF,
            "super" to CrystalTokenTypes.SUPER,
            "abstract" to CrystalTokenTypes.ABSTRACT,
            "require" to CrystalTokenTypes.REQUIRE,
            "include" to CrystalTokenTypes.INCLUDE,
            "extend" to CrystalTokenTypes.EXTEND,
            "macro" to CrystalTokenTypes.MACRO,
            "is_a?" to CrystalTokenTypes.IS_A,
            "nil?" to CrystalTokenTypes.NIL_QUESTION,
            "responds_to?" to CrystalTokenTypes.RESPONDS_TO,
            "as?" to CrystalTokenTypes.AS_QUESTION,
            "as" to CrystalTokenTypes.AS,
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
            "foo" to CrystalTokenTypes.IDENTIFIER,
            "bar_baz" to CrystalTokenTypes.IDENTIFIER,
            "empty?" to CrystalTokenTypes.IDENTIFIER,
            "save!" to CrystalTokenTypes.IDENTIFIER,
            "_private" to CrystalTokenTypes.IDENTIFIER,
            "MyClass" to CrystalTokenTypes.CONSTANT,
            "HTTP" to CrystalTokenTypes.CONSTANT,
            "@name" to CrystalTokenTypes.INSTANCE_VAR,
            "@@count" to CrystalTokenTypes.CLASS_VAR,
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
            "42" to CrystalTokenTypes.INTEGER_LITERAL,
            "1_000_000" to CrystalTokenTypes.INTEGER_LITERAL,
            "0xFF" to CrystalTokenTypes.INTEGER_LITERAL,
            "0b1010" to CrystalTokenTypes.INTEGER_LITERAL,
            "0o777" to CrystalTokenTypes.INTEGER_LITERAL,
            "42_i64" to CrystalTokenTypes.INTEGER_LITERAL,
            "3.14" to CrystalTokenTypes.FLOAT_LITERAL,
            "1.0e10" to CrystalTokenTypes.FLOAT_LITERAL,
            "1_f32" to CrystalTokenTypes.FLOAT_LITERAL,
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
            tokens.all { it.first == CrystalTokenTypes.STRING_LITERAL })
    }

    @Test
    fun testStringInterpolation() {
        val tokens = nonWhitespaceTokens("\"hello #{name}\"")
        val types = tokens.map { it.first }
        assertTrue("Should contain STRING_INTERPOLATION_BEGIN", types.contains(CrystalTokenTypes.STRING_INTERPOLATION_BEGIN))
        assertTrue("Should contain STRING_INTERPOLATION_END", types.contains(CrystalTokenTypes.STRING_INTERPOLATION_END))
        assertTrue("Should contain IDENTIFIER for interpolated var", types.contains(CrystalTokenTypes.IDENTIFIER))
    }

    @Test
    fun testComments() {
        val tokens = nonWhitespaceTokens("# this is a comment")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTokenTypes.LINE_COMMENT, tokens[0].first)
    }

    @Test
    fun testSymbols() {
        val tokens = nonWhitespaceTokens(":my_symbol")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTokenTypes.SYMBOL_LITERAL, tokens[0].first)
    }

    @Test
    fun testCharLiteral() {
        val tokens = nonWhitespaceTokens("'a'")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTokenTypes.CHAR_LITERAL, tokens[0].first)
    }

    @Test
    fun testOperators() {
        val cases = mapOf(
            "<=>" to CrystalTokenTypes.SPACESHIP,
            "===" to CrystalTokenTypes.CASE_EQ,
            "==" to CrystalTokenTypes.EQ,
            "!=" to CrystalTokenTypes.NEQ,
            "&&" to CrystalTokenTypes.AND_AND,
            "||" to CrystalTokenTypes.OR_OR,
            "->" to CrystalTokenTypes.ARROW,
            "=>" to CrystalTokenTypes.DOUBLE_ARROW,
            "::" to CrystalTokenTypes.DOUBLE_COLON,
            ".." to CrystalTokenTypes.DOTDOT,
            "..." to CrystalTokenTypes.DOTDOTDOT,
            "**" to CrystalTokenTypes.DOUBLE_STAR,
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
        val badChars = tokens.filter { it.first == CrystalTokenTypes.BAD_CHARACTER }
        assertTrue(
            "Comprehensive test file should have no BAD_CHARACTER tokens, but found: ${badChars.map { "'${it.second}'" }}",
            badChars.isEmpty()
        )
    }

    @Test
    fun testPercentLiterals() {
        // %w(...)
        val tokens = nonWhitespaceTokens("%w(foo bar)")
        assertEquals("First token should be PERCENT_LITERAL_BEGIN", CrystalTokenTypes.PERCENT_LITERAL_BEGIN, tokens[0].first)
        assertEquals("Last token should be PERCENT_LITERAL_END", CrystalTokenTypes.PERCENT_LITERAL_END, tokens.last().first)

        // %i[...]
        val tokens2 = nonWhitespaceTokens("%i[one two]")
        assertEquals(CrystalTokenTypes.PERCENT_LITERAL_BEGIN, tokens2[0].first)
        assertEquals(CrystalTokenTypes.PERCENT_LITERAL_END, tokens2.last().first)

        // %(...)
        val tokens3 = nonWhitespaceTokens("%(hello world)")
        assertEquals(CrystalTokenTypes.PERCENT_LITERAL_BEGIN, tokens3[0].first)
        assertEquals(CrystalTokenTypes.PERCENT_LITERAL_END, tokens3.last().first)
    }

    @Test
    fun testPercentLiteralNesting() {
        // Nested parentheses: %(hello (world))
        val tokens = nonWhitespaceTokens("%(hello (world))")
        assertEquals(CrystalTokenTypes.PERCENT_LITERAL_BEGIN, tokens[0].first)
        assertEquals(CrystalTokenTypes.PERCENT_LITERAL_END, tokens.last().first)
        // Should be exactly 2 non-string tokens (BEGIN and END), rest is content
        val nonContent = tokens.filter {
            it.first == CrystalTokenTypes.PERCENT_LITERAL_BEGIN || it.first == CrystalTokenTypes.PERCENT_LITERAL_END
        }
        assertEquals(2, nonContent.size)
    }

    @Test
    fun testHeredoc() {
        val text = "<<-HEREDOC\n  hello\n  world\n  HEREDOC"
        val tokens = nonWhitespaceTokens(text)
        val types = tokens.map { it.first }
        assertTrue("Should contain HEREDOC_START", types.contains(CrystalTokenTypes.HEREDOC_START))
        assertTrue("Should contain HEREDOC_CONTENT", types.contains(CrystalTokenTypes.HEREDOC_CONTENT))
        assertTrue("Should contain HEREDOC_END", types.contains(CrystalTokenTypes.HEREDOC_END))
    }

    @Test
    fun testHeredocRaw() {
        val text = "<<-'RAW'\n  no #{interpolation}\n  RAW"
        val tokens = nonWhitespaceTokens(text)
        val types = tokens.map { it.first }
        assertTrue("Should contain HEREDOC_START", types.contains(CrystalTokenTypes.HEREDOC_START))
        assertTrue("Should contain HEREDOC_END", types.contains(CrystalTokenTypes.HEREDOC_END))
    }
}
