package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.TreeElement
import de.magynhard.crystal.psi.CrystalSymbolStringExpression
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for `symbol_string_expression` PSI elements — quoted symbol strings
 * (`:"name …"`).
 *
 * Implements [PsiLanguageInjectionHost] so `# language=<id>` comments can
 * inject an embedded language into the symbol's string content. Only the
 * `SYMBOL_COLON` form carries string content; the macro-generated
 * `:{{…}}` form has none and is excluded by [isStringForm], not by the
 * grammar, so the injector and the intention silently skip it.
 *
 * Segment computation and the write-back policy mirror
 * [CrystalStringExpressionMixin]: the escaper decodes Crystal escapes,
 * single-place symbols are re-encoded on write-back, and interpolated
 * symbols are not reconstructed from the flat fragment text.
 */
abstract class CrystalSymbolStringExpressionMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), CrystalSymbolStringExpression, PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = true

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        CrystalStringLiteralEscaper(this)

    /**
     * Writes an edited injected fragment back into the symbol content.
     *
     * Single-place symbols map the fragment text onto the decoded content,
     * so the edit is re-encoded into Crystal escapes and written back
     * exactly. Interpolated symbols are not reconstructed from the flat
     * fragment text — the edit is ignored rather than corrupting the
     * interpolations.
     */
    override fun updateText(text: String): CrystalSymbolStringExpression {
        val places = computeContentSegments(this)
        if (places.size > 1) return this
        return replaceContentWith(CrystalStringExpressionMixin.encodeStringBody(text))
    }

    /**
     * Replaces the symbol content after the colon with a single content leaf
     * (already Crystal-escaped, no opening quote — the opening quote is part
     * of the `SYMBOL_COLON` token).
     */
    fun replaceContentWith(encodedBody: String): CrystalSymbolStringExpression {
        val node = this.node as TreeElement
        val colon = node.findChildByType(CrystalTypes.SYMBOL_COLON) ?: return this
        var current = colon.treeNext
        while (current != null) {
            val next = current.treeNext
            node.removeChild(current)
            current = next
        }
        node.addLeaf(CrystalTypes.STRING_LITERAL, encodedBody + "\"", null)
        return this
    }

    companion object {
        /**
         * True for the quoted `:"…"` form; false for macro-generated
         * `:{{…}}` symbols, which carry no string content.
         */
        fun isStringForm(expression: CrystalSymbolStringExpression): Boolean =
            expression.node.getChildren(null).firstOrNull()?.elementType == CrystalTypes.SYMBOL_COLON

        /**
         * The injectable content span relative to the symbol expression:
         * after the colon and between the enclosing quotes (absent quotes
         * tolerated), mirroring
         * [CrystalStringExpressionMixin.contentRange].
         */
        fun contentRange(expression: CrystalSymbolStringExpression): TextRange {
            val colon = expression.node.findChildByType(CrystalTypes.SYMBOL_COLON)
                ?: return TextRange.EMPTY_RANGE
            val base = expression.textRange.startOffset
            var start = colon.startOffset + colon.textLength - base
            var end = expression.textLength
            val contentLeaves = expression.node.getChildren(null).filter {
                it.elementType == CrystalTypes.STRING_LITERAL ||
                    it.elementType == CrystalTypes.STRING_ESCAPE
            }
            val first = contentLeaves.firstOrNull()
            if (first != null && first.text.startsWith("\"")) start += 1
            val last = contentLeaves.lastOrNull()
            if (last != null && last.textLength > 0 && last.text.endsWith("\"")) {
                end = last.startOffset + last.textLength - 1 - base
            }
            return TextRange(start, maxOf(start, end))
        }

        data class Segment(val range: TextRange, val gapsBefore: Int)

        /**
         * Injectable content segments of the symbol content, relative to the
         * expression. Consecutive `STRING_LITERAL`/`STRING_ESCAPE` leaves
         * form one segment; interpolation expressions (and any unexpected
         * node) terminate a segment. Empty for the macro form.
         * [Segment.gapsBefore] counts the interpolation gaps that precede
         * the segment (0 for a segment at the very start of the content).
         */
        fun computeContentSegments(expression: CrystalSymbolStringExpression): List<Segment> {
            if (!isStringForm(expression)) return emptyList()
            val contentRange = contentRange(expression)
            if (contentRange.isEmpty) return emptyList()

            val segments = mutableListOf<Segment>()
            var segmentStart = -1
            var segmentEnd = -1
            var gaps = 0
            var gapsBefore = 0

            fun closeSegment() {
                if (segmentStart >= 0 && segmentEnd > segmentStart) {
                    val clampedStart = maxOf(segmentStart, contentRange.startOffset)
                    val clampedEnd = minOf(segmentEnd, contentRange.endOffset)
                    if (clampedEnd > clampedStart) {
                        segments.add(Segment(TextRange(clampedStart, clampedEnd), gapsBefore))
                    }
                }
                segmentStart = -1
                segmentEnd = -1
            }

            val base = expression.textRange.startOffset
            for (child in expression.node.getChildren(null)) {
                val childStart = child.startOffset - base
                val childEnd = childStart + child.textLength
                val elementType = child.elementType
                val isContent = elementType == CrystalTypes.STRING_LITERAL ||
                    elementType == CrystalTypes.STRING_ESCAPE
                if (isContent) {
                    if (segmentStart < 0) {
                        segmentStart = childStart
                        gapsBefore = gaps
                    }
                    segmentEnd = childEnd
                } else {
                    if (elementType == CrystalTypes.STRING_INTERPOLATION_BEGIN) gaps++
                    closeSegment()
                }
            }
            closeSegment()
            return segments
        }
    }
}
