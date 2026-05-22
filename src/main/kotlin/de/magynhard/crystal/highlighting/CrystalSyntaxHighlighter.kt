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

class CrystalSyntaxHighlighter : SyntaxHighlighterBase() {

    companion object {
        val KEYWORD = createTextAttributesKey("CRYSTAL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val NUMBER = createTextAttributesKey("CRYSTAL_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING = createTextAttributesKey("CRYSTAL_STRING", DefaultLanguageHighlighterColors.STRING)
        val COMMENT = createTextAttributesKey("CRYSTAL_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val IDENTIFIER = createTextAttributesKey("CRYSTAL_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val CONSTANT = createTextAttributesKey("CRYSTAL_CONSTANT", DefaultLanguageHighlighterColors.CONSTANT)
        val INSTANCE_VAR = createTextAttributesKey("CRYSTAL_INSTANCE_VAR", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val CLASS_VAR = createTextAttributesKey("CRYSTAL_CLASS_VAR", DefaultLanguageHighlighterColors.STATIC_FIELD)
        val GLOBAL_VAR = createTextAttributesKey("CRYSTAL_GLOBAL_VAR", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE)
        val SYMBOL = createTextAttributesKey("CRYSTAL_SYMBOL", DefaultLanguageHighlighterColors.METADATA)
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
        val BAD_CHARACTER = createTextAttributesKey("CRYSTAL_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

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
        private val BAD_CHARACTER_KEYS = arrayOf(BAD_CHARACTER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }

    override fun getHighlightingLexer(): Lexer = CrystalLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when {
            tokenType == null -> EMPTY_KEYS
            CrystalTokenTypes.KEYWORDS.contains(tokenType) -> KEYWORD_KEYS
            CrystalTokenTypes.NUMBERS.contains(tokenType) -> NUMBER_KEYS
            tokenType == CrystalTokenTypes.STRING_LITERAL -> STRING_KEYS
            tokenType == CrystalTokenTypes.CHAR_LITERAL -> CHAR_KEYS
            tokenType == CrystalTokenTypes.COMMAND_LITERAL -> STRING_KEYS
            tokenType == CrystalTokenTypes.HEREDOC_CONTENT || tokenType == CrystalTokenTypes.HEREDOC_START || tokenType == CrystalTokenTypes.HEREDOC_END -> STRING_KEYS
            tokenType == CrystalTokenTypes.PERCENT_LITERAL_BEGIN || tokenType == CrystalTokenTypes.PERCENT_LITERAL_END -> STRING_KEYS
            tokenType == CrystalTokenTypes.REGEX_LITERAL -> REGEX_KEYS
            tokenType == CrystalTokenTypes.SYMBOL_LITERAL -> SYMBOL_KEYS
            tokenType == CrystalTokenTypes.STRING_INTERPOLATION_BEGIN || tokenType == CrystalTokenTypes.STRING_INTERPOLATION_END -> INTERPOLATION_KEYS
            tokenType == CrystalTokenTypes.LINE_COMMENT -> COMMENT_KEYS
            tokenType == CrystalTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
            tokenType == CrystalTokenTypes.CONSTANT -> CONSTANT_KEYS
            tokenType == CrystalTokenTypes.INSTANCE_VAR -> INSTANCE_VAR_KEYS
            tokenType == CrystalTokenTypes.CLASS_VAR -> CLASS_VAR_KEYS
            tokenType == CrystalTokenTypes.GLOBAL_VAR -> GLOBAL_VAR_KEYS
            CrystalTokenTypes.OPERATORS.contains(tokenType) -> OPERATOR_KEYS
            tokenType == CrystalTokenTypes.COMMA -> COMMA_KEYS
            tokenType == CrystalTokenTypes.SEMICOLON -> SEMICOLON_KEYS
            tokenType == CrystalTokenTypes.DOT -> DOT_KEYS
            tokenType == CrystalTokenTypes.LPAREN || tokenType == CrystalTokenTypes.RPAREN -> PARENTHESES_KEYS
            tokenType == CrystalTokenTypes.LBRACKET || tokenType == CrystalTokenTypes.RBRACKET -> BRACKETS_KEYS
            tokenType == CrystalTokenTypes.LBRACE || tokenType == CrystalTokenTypes.RBRACE -> BRACES_KEYS
            tokenType == CrystalTokenTypes.BAD_CHARACTER -> BAD_CHARACTER_KEYS
            else -> EMPTY_KEYS
        }
    }
}
