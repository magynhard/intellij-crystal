package de.magynhard.crystal.ecr

import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.ecr.lexer.EmbeddedCrystalLexerFactory

class EmbeddedCrystalSyntaxHighlighter : com.intellij.openapi.fileTypes.SyntaxHighlighter {
    private val TAG = com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey(
        "CRYSTAL_ECR_TAG", com.intellij.openapi.editor.DefaultLanguageHighlighterColors.KEYWORD
    )
    private val RAW = com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey(
        "CRYSTAL_ECR_RAW", com.intellij.openapi.editor.DefaultLanguageHighlighterColors.IDENTIFIER
    )

    override fun getHighlightingLexer(): Lexer = EmbeddedCrystalLexerFactory.create()
    override fun getTokenHighlights(tokenType: IElementType): Array<com.intellij.openapi.editor.colors.TextAttributesKey> =
        when (tokenType) {
            EmbeddedCrystalTypes.ECR_TAG_BEGIN, EmbeddedCrystalTypes.ECR_TAG_END -> arrayOf(TAG)
            EmbeddedCrystalTypes.ECR_RAW -> arrayOf(RAW)
            else -> emptyArray()
        }
}
