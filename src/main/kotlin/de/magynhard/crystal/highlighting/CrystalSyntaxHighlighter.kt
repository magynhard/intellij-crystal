package de.magynhard.crystal.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.lexer.CrystalLexerAdapter
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalTypes

class CrystalSyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        val KEYWORD = createTextAttributesKey("CRYSTAL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val NUMBER = createTextAttributesKey("CRYSTAL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING = createTextAttributesKey("CRYSTAL_STRING", DefaultLanguageHighlighterColors.STRING)
        val COMMENT = createTextAttributesKey("CRYSTAL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val IDENTIFIER = createTextAttributesKey("CRYSTAL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val CONSTANT = createTextAttributesKey("CRYSTAL_CONSTANT", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
        val INSTANCE_VAR = createTextAttributesKey("CRYSTAL_INSTANCE_VAR", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val CLASS_VAR = createTextAttributesKey("CRYSTAL_CLASS_VAR", DefaultLanguageHighlighterColors.STATIC_FIELD)
        val GLOBAL_VAR = createTextAttributesKey("CRYSTAL_GLOBAL_VAR", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE)
        val SYMBOL = createTextAttributesKey("CRYSTAL_SYMBOL", DefaultLanguageHighlighterColors.NUMBER)
        val OPERATOR = createTextAttributesKey("CRYSTAL_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val COMMA = createTextAttributesKey("CRYSTAL_COMMA", DefaultLanguageHighlighterColors.COMMA)
        val SEMICOLON = createTextAttributesKey("CRYSTAL_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
        val DOT = createTextAttributesKey("CRYSTAL_DOT", DefaultLanguageHighlighterColors.DOT)
        val PARENTHESES = createTextAttributesKey("CRYSTAL_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACKETS = createTextAttributesKey("CRYSTAL_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS)
        val BRACES = createTextAttributesKey("CRYSTAL_BRACES", DefaultLanguageHighlighterColors.BRACES)
        val CHAR = createTextAttributesKey("CRYSTAL_CHAR", DefaultLanguageHighlighterColors.STRING)
        val REGEX = createTextAttributesKey("CRYSTAL_REGEX", DefaultLanguageHighlighterColors.STRING)
        val INTERPOLATION = createTextAttributesKey("CRYSTAL_INTERPOLATION", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
        val FUNCTION_DECLARATION = createTextAttributesKey("CRYSTAL_FUNCTION_DECLARATION", DefaultLanguageHighlighterColors.CONSTANT)
        val CLASS_DECLARATION = createTextAttributesKey("CRYSTAL_CLASS_DECLARATION", DefaultLanguageHighlighterColors.CONSTANT)
        val PARAMETER = createTextAttributesKey("CRYSTAL_PARAMETER_V2", DefaultLanguageHighlighterColors.PARAMETER)
        val BAD_CHARACTER = createTextAttributesKey("CRYSTAL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)
        val MACRO_FRESH_VAR = createTextAttributesKey("CRYSTAL_MACRO_FRESH_VAR", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
        val STRING_ESCAPE = createTextAttributesKey("CRYSTAL_STRING_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE)
        val TODO_COMMENT = createTextAttributesKey("CRYSTAL_TODO_COMMENT", DefaultLanguageHighlighterColors.NUMBER)

        private val KEYWORD_KEYS = arrayOf(KEYWORD)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val STRING_KEYS = arrayOf(STRING)
        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val CONSTANT_KEYS = arrayOf(CONSTANT)
        private val INSTANCE_VAR_KEYS = arrayOf(INSTANCE_VAR)
        private val CLASS_VAR_KEYS = arrayOf(CLASS_VAR)
        private val GLOBAL_VAR_KEYS = arrayOf(GLOBAL_VAR)
        private val SYMBOL_KEYS = arrayOf(SYMBOL)
        private val OPERATOR_KEYS = arrayOf(OPERATOR)
        private val COMMA_KEYS = arrayOf(COMMA)
        private val SEMICOLON_KEYS = arrayOf(SEMICOLON)
        private val DOT_KEYS = arrayOf(DOT)
        private val PARENTHESES_KEYS = arrayOf(PARENTHESES)
        private val BRACKETS_KEYS = arrayOf(BRACKETS)
        private val BRACES_KEYS = arrayOf(BRACES)
        private val CHAR_KEYS = arrayOf(CHAR)
        private val REGEX_KEYS = arrayOf(REGEX)
        private val INTERPOLATION_KEYS = arrayOf(INTERPOLATION)
        private val STRING_ESCAPE_KEYS = arrayOf(STRING_ESCAPE)
        private val BAD_CHARACTER_KEYS = arrayOf(BAD_CHARACTER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = CrystalLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when {
            tokenType == null -> EMPTY_KEYS
            CrystalTokenTypes.KEYWORDS.contains(tokenType) -> KEYWORD_KEYS
            CrystalTokenTypes.NUMBERS.contains(tokenType) -> NUMBER_KEYS
            tokenType == CrystalTypes.STRING_LITERAL -> STRING_KEYS
            tokenType == CrystalTypes.STRING_ESCAPE -> STRING_ESCAPE_KEYS
            tokenType == CrystalTypes.CHAR_LITERAL -> CHAR_KEYS
            tokenType == CrystalTypes.COMMAND_LITERAL -> STRING_KEYS
            tokenType == CrystalTypes.HEREDOC_CONTENT || tokenType == CrystalTypes.HEREDOC_START || tokenType == CrystalTypes.HEREDOC_END -> STRING_KEYS
            tokenType == CrystalTypes.PERCENT_LITERAL_BEGIN || tokenType == CrystalTypes.PERCENT_LITERAL_END -> STRING_KEYS
            tokenType == CrystalTypes.PERCENT_SYMBOL_BEGIN || tokenType == CrystalTypes.PERCENT_SYMBOL_END -> SYMBOL_KEYS
            tokenType == CrystalTypes.REGEX_LITERAL -> REGEX_KEYS
            tokenType == CrystalTypes.SYMBOL_LITERAL -> SYMBOL_KEYS
            tokenType == CrystalTypes.SYMBOL_COLON -> SYMBOL_KEYS
            tokenType == CrystalTypes.STRING_INTERPOLATION_BEGIN || tokenType == CrystalTypes.STRING_INTERPOLATION_END -> INTERPOLATION_KEYS
            tokenType == CrystalTypes.MACRO_INTERPOLATION_BEGIN || tokenType == CrystalTypes.MACRO_INTERPOLATION_END -> INTERPOLATION_KEYS
            tokenType == CrystalTypes.MACRO_CONTROL_BEGIN || tokenType == CrystalTypes.MACRO_CONTROL_END -> INTERPOLATION_KEYS
            tokenType == CrystalTypes.MACRO_BODY_CONTENT -> STRING_KEYS
            tokenType == CrystalTypes.MACRO_FRESH_VAR -> arrayOf(MACRO_FRESH_VAR)
            tokenType == CrystalTypes.LINE_COMMENT -> COMMENT_KEYS
            // IDENTIFIER and CONSTANT are handled by the Annotator (context-sensitive)
            tokenType == CrystalTypes.IDENTIFIER -> EMPTY_KEYS
            tokenType == CrystalTypes.CONSTANT -> EMPTY_KEYS
            tokenType == CrystalTypes.INSTANCE_VAR -> INSTANCE_VAR_KEYS
            tokenType == CrystalTypes.CLASS_VAR -> CLASS_VAR_KEYS
            tokenType == CrystalTypes.GLOBAL_VAR -> GLOBAL_VAR_KEYS
            CrystalTokenTypes.OPERATORS.contains(tokenType) -> OPERATOR_KEYS
            tokenType == CrystalTypes.COMMA -> COMMA_KEYS
            tokenType == CrystalTypes.SEMICOLON -> SEMICOLON_KEYS
            tokenType == CrystalTypes.DOT -> DOT_KEYS
            tokenType == CrystalTypes.LPAREN || tokenType == CrystalTypes.RPAREN -> PARENTHESES_KEYS
            tokenType == CrystalTypes.LBRACKET || tokenType == CrystalTypes.RBRACKET -> BRACKETS_KEYS
            tokenType == CrystalTypes.LBRACE || tokenType == CrystalTypes.RBRACE -> BRACES_KEYS
            tokenType == CrystalTokenTypes.BAD_CHARACTER -> BAD_CHARACTER_KEYS
            else -> EMPTY_KEYS
        }
    }
}
