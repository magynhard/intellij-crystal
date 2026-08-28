package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.TreeElement
import de.magynhard.crystal.injection.CrystalHeredocInjection
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for `heredoc_literal` PSI elements — the body of a heredoc string
 * (`<<-SQL … SQL`), delimited by the [CrystalTypes.HEREDOC_START] header token
 * and the optional [CrystalTypes.HEREDOC_END] terminator.
 *
 * Implements [PsiLanguageInjectionHost] so embedded-language injectors can
 * highlight heredoc bodies whose marker names a language (`<<-SQL`,
 * `<<-JAVASCRIPT`, …). The body span between the delimiters is the injectable
 * range; interpolation expressions inside the body split the injection into
 * multiple places (see [CrystalHeredocInjection.computeInjectionPlaces]).
 */
abstract class CrystalHeredocLiteralMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), CrystalHeredocLiteral, PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = true

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        LiteralTextEscaper.createSimple(this)

    /**
     * Writes an edited injected fragment back into the body.
     *
     * Single-place heredocs (no interpolations, raw heredocs included) map the
     * fragment text 1:1 onto the body, so the body is replaced exactly.
     * Multi-place heredocs (interpolated) cannot be reconstructed from the flat
     * fragment text alone — the edit is ignored and the element returned
     * unchanged rather than corrupting the interpolations.
     */
    override fun updateText(text: String): CrystalHeredocLiteral {
        val places = CrystalHeredocInjection.computeBodySegments(this)
        if (places.size > 1) return this
        return replaceBodyWith(text)
    }

    /**
     * Replaces the whole body span (everything between [CrystalTypes.HEREDOC_START]
     * and the [CrystalTypes.HEREDOC_END] terminator) with a single content leaf.
     */
    fun replaceBodyWith(text: String): CrystalHeredocLiteral {
        val node = this.node as TreeElement
        val startToken = node.findChildByType(CrystalTypes.HEREDOC_START)
        val endToken = node.findChildByType(CrystalTypes.HEREDOC_END)
        var current = startToken?.treeNext
        while (current != null && current !== endToken) {
            val next = current.treeNext
            node.removeChild(current)
            current = next
        }
        if (text.isNotEmpty()) {
            node.addLeaf(CrystalTypes.HEREDOC_CONTENT, text, endToken)
        }
        return this
    }

    companion object {
        /**
         * The injectable body span relative to the literal: from the end of the
         * [CrystalTypes.HEREDOC_START] token to the start of the
         * [CrystalTypes.HEREDOC_END] terminator (or the element end).
         */
        fun contentRange(literal: CrystalHeredocLiteral): TextRange {
            val startToken = literal.node.findChildByType(CrystalTypes.HEREDOC_START)
                ?: return TextRange.EMPTY_RANGE
            val base = literal.textRange.startOffset
            val first = startToken.startOffset + startToken.textLength - base
            val endToken = literal.node.findChildByType(CrystalTypes.HEREDOC_END)
            val last = endToken?.startOffset?.minus(base) ?: literal.textLength
            return TextRange(first, maxOf(first, last))
        }
    }
}
