package de.magynhard.crystal.ecr.highlighting

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.highlighting.CrystalSyntaxHighlighter
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Syntax highlighter for Crystal code inside ECR `<% %>` tags.
 *
 * Wraps [CrystalSyntaxHighlighter] but overrides IDENTIFIER/CONSTANT mappings to provide
 * basic colors directly, instead of relying on the [de.magynhard.crystal.highlighting.CrystalAnnotator]
 * (which does not fire for `EmbeddedCrystal` files). When the Annotator does fire via language
 * injection, its semantic colors override these base colors.
 */
class EcrCrystalSyntaxHighlighter : SyntaxHighlighter {

    private val delegate = CrystalSyntaxHighlighter()

    override fun getHighlightingLexer(): Lexer = delegate.highlightingLexer

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when (tokenType) {
            CrystalTypes.IDENTIFIER -> IDENTIFIER_KEYS
            CrystalTypes.CONSTANT -> CONSTANT_KEYS
            else -> delegate.getTokenHighlights(tokenType)
        }
    }

    companion object {
        private val IDENTIFIER_KEYS = arrayOf(CrystalSyntaxHighlighter.IDENTIFIER)
        private val CONSTANT_KEYS = arrayOf(CrystalSyntaxHighlighter.CONSTANT)
    }
}
