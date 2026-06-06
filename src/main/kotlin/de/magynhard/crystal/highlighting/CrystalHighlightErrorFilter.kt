package de.magynhard.crystal.highlighting

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.psi.PsiErrorElement
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Suppresses generic parser error highlights when our own annotations/inspections
 * already provide a user-friendly error message for the same issue.
 *
 * This prevents double errors:
 * - Single-quote strings: BAD_CHARACTER inspection shows the good message,
 *   parser shows "<expression> or NEWLINE expected" → we suppress the parser error
 * - Incomplete heredocs: annotator shows "Missing heredoc end delimiter",
 *   parser shows generic syntax error → we suppress the parser error
 *
 * For all other parser errors, the original highlight is preserved.
 */
class CrystalHighlightErrorFilter : HighlightErrorFilter() {

    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean {
        // Suppress parser errors caused by invalid single-quote strings (BAD_CHARACTER)
        if (isCausedByBadCharacter(element)) return false

        // Suppress parser errors caused by missing heredoc end delimiters
        if (isCausedByMissingHeredocEnd(element)) return false

        // Keep all other parser errors visible
        return true
    }

    /**
     * Check if this PsiErrorElement is caused by a BAD_CHARACTER token nearby.
     * We traverse up the tree looking for a BAD_CHARACTER sibling at any level
     * (the BAD_CHARACTER may be a sibling of an ancestor, e.g. inside FILE).
     */
    private fun isCausedByBadCharacter(element: PsiErrorElement): Boolean {
        var current: com.intellij.psi.PsiElement? = element
        // Traverse up to 3 levels looking for BAD_CHARACTER siblings
        repeat(3) {
            current = current?.parent ?: return false
            var sibling = current?.firstChild
            while (sibling != null) {
                if (sibling.node?.elementType == CrystalTokenTypes.BAD_CHARACTER) {
                    return true
                }
                sibling = sibling.nextSibling
            }
        }
        return false
    }

    /**
     * Check if this PsiErrorElement is caused by a HEREDOC_START without matching HEREDOC_END.
     * We look for HEREDOC_START in siblings where there's no HEREDOC_END.
     */
    private fun isCausedByMissingHeredocEnd(element: PsiErrorElement): Boolean {
        val parent = element.parent ?: return false
        var hasHeredocStart = false
        var hasHeredocEnd = false

        var sibling = parent.firstChild
        while (sibling != null) {
            val type = sibling.node?.elementType
            if (type == CrystalTypes.HEREDOC_START) hasHeredocStart = true
            if (type == CrystalTypes.HEREDOC_END) hasHeredocEnd = true
            sibling = sibling.nextSibling
        }

        return hasHeredocStart && !hasHeredocEnd
    }
}
