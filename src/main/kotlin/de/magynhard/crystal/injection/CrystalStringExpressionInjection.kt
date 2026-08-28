package de.magynhard.crystal.injection

import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import de.magynhard.crystal.psi.CrystalStringExpression
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.psi.impl.CrystalStringExpressionMixin

/**
 * String-literal side of embedded-language injection: a `# language=<id>`
 * comment on the line directly above a string literal ([CrystalLanguageComment])
 * injects that language into the string's content. Markers do not apply to
 * plain strings; an unresolvable comment language means no injection.
 */
object CrystalStringExpressionInjection {

    data class Segment(val range: TextRange, val gapsBefore: Int)

    data class Plan(val language: Language, val prefix: String, val suffix: String)

    /**
     * Full injection decision for a string literal — comment-driven only.
     * Returns null without an adjacent resolvable comment.
     */
    fun resolveInjectionPlan(stringExpression: CrystalStringExpression): Plan? {
        val comment = stringExpression.containingFile
            ?.let { CrystalLanguageComment.findAdjacentComment(it, stringExpression.textRange.startOffset) }
            ?: return null
        val language = CrystalHeredocInjection.resolveLanguage(comment.languageId) ?: return null
        return Plan(language, comment.prefix, comment.suffix)
    }

    /**
     * Injectable content segments of the string, relative to the string
     * expression: the content span between the quotes
     * ([CrystalStringExpressionMixin.contentRange]) minus interpolation
     * expressions. Consecutive [CrystalTypes.STRING_LITERAL] content chunks
     * and [CrystalTypes.STRING_ESCAPE] leaves form one segment.
     */
    fun computeContentSegments(stringExpression: CrystalStringExpression): List<Segment> {
        val contentRange = CrystalStringExpressionMixin.contentRange(stringExpression)
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

        for (child in stringExpression.node.getChildren(null)) {
            val childStart = child.startOffset - stringExpression.textRange.startOffset
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
