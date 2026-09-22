package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.TreeElement
import de.magynhard.crystal.psi.CrystalPercentLiteral
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for `percent_literal` PSI elements — `%q(…)`, `%Q(…)`, and bare
 * `%(…)` string forms.
 *
 * Implements [PsiLanguageInjectionHost] so `# language=<id>` comments can
 * inject an embedded language into string-like percent content. Only the
 * `PERCENT_LITERAL_BEGIN/END` family with string content is a usable host:
 * `%r`/`%x` share the token pair but carry regex/command content, and
 * `%w`/`%W`/`%i`/`%I` arrays use their own token pairs with array semantics.
 * Those forms are excluded by [isStringLike], not by the grammar, so the
 * injector and the intention silently skip them.
 *
 * Segment computation mirrors [CrystalStringExpressionMixin]: consecutive
 * content leaves form one segment and interpolation expressions terminate
 * it; newlines are content because percent strings may span lines. Raw `%q`
 * has no escape leaves, so the shared escaper passes it through verbatim —
 * exactly like raw heredoc bodies.
 */
abstract class CrystalPercentLiteralMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), CrystalPercentLiteral, PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = true

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        CrystalStringLiteralEscaper(this)

    /**
     * Writes an edited injected fragment back into the percent body.
     *
     * Single-place literals map the fragment text 1:1 onto the body: raw
     * `%q` verbatim, interpolating forms re-encoded into Crystal escapes.
     * Interpolated literals are not reconstructed from the flat fragment
     * text — the edit is ignored rather than corrupting the interpolations.
     */
    override fun updateText(text: String): CrystalPercentLiteral {
        val places = computeContentSegments(this)
        if (places.size > 1) return this
        val body = if (isRaw(this)) text else CrystalStringExpressionMixin.encodeStringBody(text)
        return replaceBodyWith(body)
    }

    /**
     * Replaces the whole body span (everything between
     * [CrystalTypes.PERCENT_LITERAL_BEGIN] and the
     * [CrystalTypes.PERCENT_LITERAL_END] terminator, or the element end when
     * the literal is incomplete) with a single content leaf.
     */
    fun replaceBodyWith(text: String): CrystalPercentLiteral {
        val node = this.node as TreeElement
        val begin = node.findChildByType(CrystalTypes.PERCENT_LITERAL_BEGIN) ?: return this
        val end = node.findChildByType(CrystalTypes.PERCENT_LITERAL_END)
        var current = begin.treeNext
        while (current != null && current !== end) {
            val next = current.treeNext
            node.removeChild(current)
            current = next
        }
        if (text.isNotEmpty()) {
            node.addLeaf(CrystalTypes.STRING_LITERAL, text, end)
        }
        return this
    }

    companion object {
        /**
         * True for string-like percent forms (`%q`, `%Q`, bare `%()`): the
         * `PERCENT_LITERAL_BEGIN/END` family without a `%r`/`%x` opener.
         * Word/symbol arrays and regex/command content are not injection
         * hosts.
         */
        fun isStringLike(literal: CrystalPercentLiteral): Boolean {
            val first = literal.node.getChildren(null).firstOrNull() ?: return false
            if (first.elementType != CrystalTypes.PERCENT_LITERAL_BEGIN) return false
            val text = first.text
            // %r/%x share the literal token pair but carry regex/command content.
            if (text.length >= 2 && (text[1] == 'r' || text[1] == 'x')) return false
            return true
        }

        private fun isRaw(literal: CrystalPercentLiteral): Boolean {
            val first = literal.node.getChildren(null).firstOrNull() ?: return false
            return first.elementType == CrystalTypes.PERCENT_LITERAL_BEGIN &&
                first.text.length >= 2 && first.text[1] == 'q'
        }

        /**
         * The injectable body span relative to the literal: from the end of
         * the `PERCENT_LITERAL_BEGIN` token to the start of the
         * `PERCENT_LITERAL_END` terminator (or the element end).
         */
        fun contentRange(literal: CrystalPercentLiteral): TextRange {
            val children = literal.node.getChildren(null)
            if (children.isEmpty()) return TextRange.EMPTY_RANGE
            val base = literal.textRange.startOffset
            val first = children.first()
            val start = first.startOffset + first.textLength - base
            val endToken = literal.node.findChildByType(CrystalTypes.PERCENT_LITERAL_END)
            val end = endToken?.startOffset?.minus(base) ?: literal.textLength
            return TextRange(start, maxOf(start, end))
        }

        data class Segment(val range: TextRange, val gapsBefore: Int)

        /**
         * Injectable content segments of the percent body, relative to the
         * literal. Consecutive `STRING_LITERAL`/`STRING_ESCAPE`/`NEWLINE`
         * leaves form one segment; interpolation expressions (and any
         * unexpected node) terminate a segment. Empty for non-string-like
         * forms. [Segment.gapsBefore] counts the interpolation gaps that
         * precede the segment (0 for a segment at the very start).
         */
        fun computeContentSegments(literal: CrystalPercentLiteral): List<Segment> {
            if (!isStringLike(literal)) return emptyList()
            val contentRange = contentRange(literal)
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

            val base = literal.textRange.startOffset
            for (child in literal.node.getChildren(null)) {
                val elementType = child.elementType
                if (elementType == CrystalTypes.PERCENT_LITERAL_BEGIN ||
                    elementType == CrystalTypes.PERCENT_LITERAL_END
                ) {
                    closeSegment()
                    continue
                }
                val childStart = child.startOffset - base
                val childEnd = childStart + child.textLength
                val isContent = elementType == CrystalTypes.STRING_LITERAL ||
                    elementType == CrystalTypes.STRING_ESCAPE ||
                    elementType == CrystalTypes.NEWLINE
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
