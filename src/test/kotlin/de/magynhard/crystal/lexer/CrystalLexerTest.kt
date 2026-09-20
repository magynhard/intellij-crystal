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
    fun testOnlyMacroDefinitionsEnterMacroBody() {
        val nonDefinitions = listOf(
            "filename.macro.location\nsource_filename = macro_source.try &.filename" to "source_filename",
            "getter macro : Macro\ndef initialize(@macro : Macro)\nend" to "def",
            "def macro(type)\n  Macro.new(type)\nend" to "Macro",
            "record FinishedHook, scope : ModuleType, macro : Macro\ngetter finished_hooks = [] of FinishedHook" to "getter",
        )

        for ((source, followingToken) in nonDefinitions) {
            val token = nonWhitespaceTokens(source).firstOrNull { it.second == followingToken }
            assertNotNull("'$followingToken' should be lexed after a non-definition macro", token)
            assertNotEquals("'$followingToken' must not become macro body content", CrystalTypes.MACRO_BODY_CONTENT, token?.first)
        }
    }

    @Test
    fun testMultilineMacroHeaderWaitsForClosingParenthesis() {
        val tokens = nonWhitespaceTokens("""
            private macro build(
              name,
              type
            )
              {{ name }}
            end
        """.trimIndent())

        assertEquals(CrystalTypes.IDENTIFIER, tokens.first { it.second == "name" }.first)
        assertTrue(tokens.any { it.first == CrystalTypes.MACRO_INTERPOLATION_BEGIN })
    }

    @Test
    fun testRequireKeywordInExpressionLexerStates() {
        val inputs = listOf(
            "\"#{require \"./dependency\"}\"",
            "{{ require \"./dependency\" }}",
            "{% require \"./dependency\" %}",
        )

        for (input in inputs) {
            val requireToken = nonWhitespaceTokens(input).firstOrNull { it.second == "require" }
            assertNotNull("Should tokenize require in '$input'", requireToken)
            assertEquals("Require token in '$input'", CrystalTypes.REQUIRE, requireToken?.first)
        }
    }

    @Test
    fun testBangTildeIsOneTokenInExpressionLexerStates() {
        val inputs = listOf(
            "left !~ right",
            "\"#{left !~ right}\"",
            "{{ left !~ right }}",
            "{% if left !~ right %}",
        )

        for (input in inputs) {
            val tokens = nonWhitespaceTokens(input).filter { it.second == "!~" }
            assertEquals("Should emit one !~ token in '$input'", 1, tokens.size)
            assertEquals("!~ token in '$input'", CrystalTypes.BANG_TILDE, tokens.single().first)
        }
    }

    @Test
    fun testBareRegexArgumentAfterDotCall() {
        val tokens = nonWhitespaceTokens("range.match /(\\d{1,})-(\\d{0,})/")
        assertTrue(tokens.any { it.first == CrystalTypes.REGEX_BEGIN })
        assertTrue(tokens.any { it.first == CrystalTypes.REGEX_END })
        assertFalse(tokens.any { it.first == CrystalTypes.SLASH })
    }

    @Test
    fun testSlashAfterDotCallWithoutRegexTerminatorIsDivision() {
        val tokens = nonWhitespaceTokens("object.value /2")
        assertTrue(tokens.any { it.first == CrystalTypes.SLASH })
        assertFalse(tokens.any { it.first == CrystalTypes.REGEX_BEGIN })
    }

    @Test
    fun testChainedDivisionAfterDotCallRemainsDivision() {
        for (input in listOf("object.value / 2 / 3", "object.value / divisor / scale")) {
            val tokens = nonWhitespaceTokens(input)
            assertEquals("Both slashes in '$input'", 2, tokens.count { it.first == CrystalTypes.SLASH })
            assertFalse("No regex in '$input'", tokens.any { it.first == CrystalTypes.REGEX_BEGIN })
        }
    }

    @Test
    fun testMultilineBareRegexArgumentAfterDotCall() {
        val tokens = nonWhitespaceTokens("text.match /foo\nbar/")
        assertTrue(tokens.any { it.first == CrystalTypes.REGEX_BEGIN })
        assertTrue(tokens.any { it.first == CrystalTypes.REGEX_END })
        assertFalse(tokens.any { it.first == CrystalTypes.SLASH })
    }

    @Test
    fun testTightSlashAfterColonIsSymbolSlash() {
        // `run_op_tests ..., :/` (int_spec): a `/` tightly glued to `:` starts
        // the `:/` operator symbol, never a regex.
        for (input in listOf("foo(:/)", "foo(a, :/)")) {
            val tokens = nonWhitespaceTokens(input)
            assertTrue("Slash in '$input'", tokens.any { it.first == CrystalTypes.SLASH })
            assertFalse("No regex in '$input'", tokens.any { it.first == CrystalTypes.REGEX_BEGIN })
        }
    }

    @Test
    fun testTightSlashAfterLabelColonStaysRegex() {
        // `{a:/re/}`, `f(x:/re/)`: after an identifier label the tight `:/`
        // keeps the regex reading, exactly like the compiler.
        for (input in listOf("{a:/re/}", "f(x:/re/)")) {
            val tokens = nonWhitespaceTokens(input)
            assertTrue("Regex in '$input'", tokens.any { it.first == CrystalTypes.REGEX_BEGIN })
        }
    }

    @Test
    fun testDoubleBraceInStringInterpolatesOnlyInMacroCode() {
        // `"a {{ op }} b"` inside `{% for %}`: the braces interpolate.
        val macroTokens = nonWhitespaceTokens("{% for a in [1] %}\n  x = \"a {{ op }} b\"\n{% end %}\n")
        assertTrue(macroTokens.any { it.first == CrystalTypes.MACRO_INTERPOLATION_BEGIN })
        assertTrue(macroTokens.any { it.first == CrystalTypes.MACRO_INTERPOLATION_END })
        // Same text in plain code: braces stay literal string content.
        val plainTokens = nonWhitespaceTokens("x = \"a {{ op }} b\"\n")
        assertFalse(
            "No interpolation in plain strings",
            plainTokens.any { it.first == CrystalTypes.MACRO_INTERPOLATION_BEGIN },
        )
    }

    @Test
    fun testEscapedDoubleBraceInMacroStringStaysLiteral() {
        // `\{{` never opens interpolation, even with macro depth behind it.
        val tokens = nonWhitespaceTokens("{% for a in [1] %}\n  x = \"a \\{{ op }} b\"\n{% end %}\n")
        assertFalse(
            "Escaped braces stay literal",
            tokens.any { it.first == CrystalTypes.MACRO_INTERPOLATION_BEGIN },
        )
    }

    @Test
    fun testPostfixIfKeywordInExpressionLexerStates() {
        val inputs = listOf(
            "\"#{require \"./dependency\" if true}\"",
            "{{ require \"./dependency\" if true }}",
            "{% require \"./dependency\" if true %}",
        )

        for (input in inputs) {
            val ifToken = nonWhitespaceTokens(input).firstOrNull { it.second == "if" }
            assertNotNull("Should tokenize postfix if in '$input'", ifToken)
            assertEquals("Postfix if token in '$input'", CrystalTypes.IF, ifToken?.first)
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
            "1_f64" to CrystalTypes.FLOAT_LITERAL,
            "1f32" to CrystalTypes.FLOAT_LITERAL,
            "1f64" to CrystalTypes.FLOAT_LITERAL,
        )
        for ((text, expected) in cases) {
            val tokens = nonWhitespaceTokens(text)
            assertEquals("Number '$text' should produce one token, got: $tokens", 1, tokens.size)
            assertEquals("Number '$text'", expected, tokens[0].first)
        }
    }

    @Test
    fun testSuffixedFloatWithoutUnderscoreInExpressionLexerStates() {
        val inputs = mapOf(
            "__pow_impl(__powisf2, 1f32, Float32)" to 1,
            "\"#{1f32}\"" to 1,
            "{{ 1f32 }}" to 1,
            "{% if 1f32 == 0 %}" to 1,
        )

        for ((input, expectedCount) in inputs) {
            val tokens = nonWhitespaceTokens(input).filter {
                it.first == CrystalTypes.FLOAT_LITERAL && (it.second == "1f32" || it.second == "1f64")
            }
            assertEquals(
                "Should emit $expectedCount suffixed float token(s) in '$input', got: ${nonWhitespaceTokens(input)}",
                expectedCount,
                tokens.size,
            )
        }
    }

    @Test
    fun testSuffixedFloatBoundaries() {
        val separated = nonWhitespaceTokens("1 f32")
        assertEquals(
            "Space-separated '1 f32' stays two tokens, got: $separated",
            listOf(CrystalTypes.INTEGER_LITERAL, CrystalTypes.IDENTIFIER),
            separated.map { it.first },
        )

        val integer = nonWhitespaceTokens("42")
        assertEquals(1, integer.size)
        assertEquals(CrystalTypes.INTEGER_LITERAL, integer.single().first)
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
    fun testSetterSymbolIsSingleToken() {
        // `:color=` is one symbol (delegate :color=, ...), not `:color` + `=`.
        val tokens = nonWhitespaceTokens(":color=")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTypes.SYMBOL_LITERAL, tokens[0].first)
        assertEquals(":color=", tokens[0].second)
    }

    @Test
    fun testConstantSetterSymbolIsSingleToken() {
        val tokens = nonWhitespaceTokens(":Constant=")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTypes.SYMBOL_LITERAL, tokens[0].first)
        assertEquals(":Constant=", tokens[0].second)
    }

    @Test
    fun testSymbolBeforeDoubleEqualsKeepsOperator() {
        // `:foo==` is `:foo` + `==`, never the symbol `:foo=`.
        val tokens = nonWhitespaceTokens(":foo==")
        assertEquals(2, tokens.size)
        assertEquals(CrystalTypes.SYMBOL_LITERAL, tokens[0].first)
        assertEquals(":foo", tokens[0].second)
        assertEquals(CrystalTypes.EQ, tokens[1].first)
    }

    @Test
    fun testQuestionAndBangSymbolsUnchanged() {
        val question = nonWhitespaceTokens(":color?")
        assertEquals(listOf(CrystalTypes.SYMBOL_LITERAL to ":color?"), question.map { it.first to it.second })
        val bang = nonWhitespaceTokens(":color!")
        assertEquals(listOf(CrystalTypes.SYMBOL_LITERAL to ":color!"), bang.map { it.first to it.second })
    }

    @Test
    fun testCharLiteral() {
        val tokens = nonWhitespaceTokens("'a'")
        assertEquals(1, tokens.size)
        assertEquals(CrystalTypes.CHAR_LITERAL, tokens[0].first)
    }

    @Test
    fun testCharLiteralInInterpolation() {
        val tokens = nonWhitespaceTokens("\"cat-#{rule_id.split('-').last.rjust(3, '0')}\"")
        val types = tokens.map { it.first }
        assertTrue("Should contain CHAR_LITERAL for '-'", tokens.any { it.first == CrystalTypes.CHAR_LITERAL && it.second == "'-'" })
        assertTrue("Should contain CHAR_LITERAL for '0'", tokens.any { it.first == CrystalTypes.CHAR_LITERAL && it.second == "'0'" })
        assertFalse("Interpolation with char literals must not produce BAD_CHARACTER",
            types.contains(TokenType.BAD_CHARACTER))
    }

    @Test
    fun testCharLiteralEscapeSequencesInInterpolation() {
        for (literal in listOf("'\\n'", "'\\t'", "'\\''", "'\\\\'")) {
            val tokens = nonWhitespaceTokens("\"#{$literal}\"")
            assertTrue("'${literal.replace("\\", "\\\\")}' inside interpolation should lex as CHAR_LITERAL, got: $tokens",
                tokens.any { it.first == CrystalTypes.CHAR_LITERAL })
            assertFalse(tokens.any { it.first == TokenType.BAD_CHARACTER })
        }
    }

    @Test
    fun testInvalidCharLiteralIsSingleBadCharacter() {
        // Top level
        val top = tokenize("'ab'")
        val badTop = top.filter { it.first == TokenType.BAD_CHARACTER }
        assertEquals("Multi-character single-quote literal should be one BAD_CHARACTER token at top level", 1, badTop.size)
        assertEquals("'ab'", badTop[0].second)

        // Inside interpolation
        val interpolated = tokenize("\"#{'ab'}\"")
        val badInterpolated = interpolated.filter { it.first == TokenType.BAD_CHARACTER }
        assertEquals("Multi-character single-quote literal should be one BAD_CHARACTER token inside interpolation", 1, badInterpolated.size)
        assertEquals("'ab'", badInterpolated[0].second)
    }

    @Test
    fun testCharLiteralInMacroControl() {
        val tokens = nonWhitespaceTokens("{% if x == 'a' %}")
        assertTrue("Macro control should lex the char comparison operand as CHAR_LITERAL, got: $tokens",
            tokens.any { it.first == CrystalTypes.CHAR_LITERAL && it.second == "'a'" })
        assertFalse(tokens.any { it.first == TokenType.BAD_CHARACTER })
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
    fun testBlockPassProcPointerTokens() {
        val tokens = nonWhitespaceTokens("f &->@worker.run")
        assertEquals(
            "Block-pass proc pointer should lex as AMPERSAND, ARROW, INSTANCE_VAR, DOT, IDENTIFIER, got: $tokens",
            listOf(
                CrystalTypes.IDENTIFIER to "f",
                CrystalTypes.AMPERSAND to "&",
                CrystalTypes.ARROW to "->",
                CrystalTypes.INSTANCE_VAR to "@worker",
                CrystalTypes.DOT to ".",
                CrystalTypes.IDENTIFIER to "run"
            ),
            tokens
        )
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
        assertEquals("First token should be PERCENT_WORD_ARRAY_BEGIN", CrystalTypes.PERCENT_WORD_ARRAY_BEGIN, tokens[0].first)
        assertEquals("Last token should be PERCENT_WORD_ARRAY_END", CrystalTypes.PERCENT_WORD_ARRAY_END, tokens.last().first)

        // %W[...] (interpolating word array)
        val tokensW = nonWhitespaceTokens("%W[foo bar]")
        assertEquals(CrystalTypes.PERCENT_WORD_ARRAY_BEGIN, tokensW[0].first)
        assertEquals(CrystalTypes.PERCENT_WORD_ARRAY_END, tokensW.last().first)

        // %i[...]
        val tokens2 = nonWhitespaceTokens("%i[one two]")
        assertEquals(CrystalTypes.PERCENT_SYMBOL_BEGIN, tokens2[0].first)
        assertEquals(CrystalTypes.PERCENT_SYMBOL_END, tokens2.last().first)

        // %I[...] (interpolating symbol array)
        val tokensI = nonWhitespaceTokens("%I[one two]")
        assertEquals(CrystalTypes.PERCENT_SYMBOL_BEGIN, tokensI[0].first)
        assertEquals(CrystalTypes.PERCENT_SYMBOL_END, tokensI.last().first)

        // %(...)
        val tokens3 = nonWhitespaceTokens("%(hello world)")
        assertEquals(CrystalTypes.PERCENT_LITERAL_BEGIN, tokens3[0].first)
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, tokens3.last().first)

        // %q(...) must NOT use the word-array tokens
        val tokensQ = nonWhitespaceTokens("%q(hello world)")
        assertEquals(CrystalTypes.PERCENT_LITERAL_BEGIN, tokensQ[0].first)
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, tokensQ.last().first)
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
    fun testPercentLiteralInStringInterpolation() {
        // `"#{ %(a) if b }"` (generate_grapheme_break_specs.cr): the `%(` must
        // open one percent literal instead of splitting into PERCENT + LPAREN.
        val tokens = nonWhitespaceTokens("\"#{ %(a) if b }\"")
        assertTrue(tokens.any { it.first == CrystalTypes.STRING_INTERPOLATION_BEGIN })
        val begin = tokens.first { it.first == CrystalTypes.PERCENT_LITERAL_BEGIN }
        assertEquals("%(", begin.second)
        val end = tokens.last { it.first == CrystalTypes.PERCENT_LITERAL_END }
        assertEquals(")", end.second)
        assertTrue(tokens.any { it.first == CrystalTypes.STRING_INTERPOLATION_END })
    }

    @Test
    fun testWordArrayInMacroControlTag() {
        // `{% for op in %w(+ - * /) %}` (raytracer.cr): the `%w(` must open
        // one word array instead of splitting, or a later `/` after an
        // operator lexes as REGEX_BEGIN via isRegexAllowed and derails the tag.
        val tokens = nonWhitespaceTokens("{% for op in %w(+ - * /) %}")
        val begin = tokens.first { it.first == CrystalTypes.PERCENT_WORD_ARRAY_BEGIN }
        assertEquals("%w(", begin.second)
        val end = tokens.last { it.first == CrystalTypes.PERCENT_WORD_ARRAY_END }
        assertEquals(")", end.second)
        assertTrue(tokens.any { it.first == CrystalTypes.MACRO_CONTROL_BEGIN })
        assertTrue(tokens.any { it.first == CrystalTypes.MACRO_CONTROL_END })
        assertFalse(tokens.any { it.first == CrystalTypes.REGEX_BEGIN })
    }

    @Test
    fun testRawPercentLiteralInMacroControlTag() {
        // `%q(+ - * /)` family proof alongside `%w`: same opener mechanics.
        val tokens = nonWhitespaceTokens("{% if s == %q(+ - * /) %}")
        val begin = tokens.first { it.first == CrystalTypes.PERCENT_LITERAL_BEGIN }
        assertEquals("%q(", begin.second)
        assertFalse(tokens.any { it.first == CrystalTypes.REGEX_BEGIN })
    }

    @Test
    fun testSpacedPercentWordInMacroControlStaysSplit() {
        // `%w ==` (modulo plus variable) must not become a word array: the
        // opener requires a tight delimiter.
        val tokens = nonWhitespaceTokens("{% if a %w == b %}")
        assertTrue(tokens.any { it.first == CrystalTypes.PERCENT })
        assertFalse(tokens.any { it.first == CrystalTypes.PERCENT_WORD_ARRAY_BEGIN })
    }

    @Test
    fun testModuloInStringInterpolationStaysPercent() {
        // `"#{a % b}"`: spaced `%` remains the modulo operator, never a
        // percent literal opener.
        val tokens = nonWhitespaceTokens("\"#{a % b}\"")
        val percents = tokens.filter { it.first == CrystalTypes.PERCENT }
        assertEquals(1, percents.size)
        assertEquals("%", percents.single().second)
        assertFalse(tokens.any { it.first == CrystalTypes.PERCENT_LITERAL_BEGIN })
    }

    @Test
    fun testRawPercentLiteralBackslashDoesNotEscapeCloser() {
        // %q is raw (compiler allow_escapes: false): the backslash is literal
        // content and the `)` after it still closes the literal.
        val tokens = nonWhitespaceTokens("%q(\\)")
        assertEquals(CrystalTypes.PERCENT_LITERAL_BEGIN, tokens[0].first)
        assertEquals("%q(", tokens[0].second)
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, tokens.last().first)
        assertEquals(")", tokens.last().second)
        val middle = tokens.subList(1, tokens.size - 1)
        assertEquals(listOf("\\"), middle.map { it.second })
        assertTrue(middle.all { it.first == CrystalTypes.STRING_LITERAL })
    }

    @Test
    fun testRawPercentLiteralBackslashBeforeOpenerStillNests() {
        // `%q(a\(b)` is `a\(b)`: the `\` is content, the `(` still nests, so
        // the first `)` is content and the second one closes.
        val tokens = nonWhitespaceTokens("%q(a\\(b))")
        assertEquals(CrystalTypes.PERCENT_LITERAL_BEGIN, tokens[0].first)
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, tokens.last().first)
        assertEquals(")", tokens.last().second)
        val middle = tokens.subList(1, tokens.size - 1).map { it.second }.joinToString("")
        assertEquals("a\\(b)", middle)
    }

    @Test
    fun testInterpolatingPercentLiteralBackslashStillEscapes() {
        // %Q keeps escape semantics: `\\` is one escape token and does not
        // close the literal, which stays open until the final `)`.
        val tokens = nonWhitespaceTokens("%Q(\\\\a)")
        assertEquals(CrystalTypes.PERCENT_LITERAL_BEGIN, tokens[0].first)
        assertEquals(CrystalTypes.PERCENT_LITERAL_END, tokens.last().first)
        val middle = tokens.subList(1, tokens.size - 1)
        assertEquals(
            listOf(CrystalTypes.STRING_ESCAPE to "\\\\", CrystalTypes.STRING_LITERAL to "a"),
            middle.map { it.first to it.second }
        )
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
    fun testPlainEolHeredocDoesNotDisturbFollowingCode() {
        // After a plain eol heredoc finished, subsequent code must lex normally
        val text = "<<-E\nx\nE\nlater = 1"
        val tokens = nonWhitespaceTokens(text)
        val laterIdx = tokens.indexOfFirst { it.second == "later" }
        assertEquals("'later' should be IDENTIFIER", CrystalTypes.IDENTIFIER, tokens[laterIdx].first)
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
