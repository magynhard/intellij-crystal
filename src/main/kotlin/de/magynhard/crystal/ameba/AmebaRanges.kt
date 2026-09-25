package de.magynhard.crystal.ameba

import com.intellij.openapi.editor.Document

/**
 * Maps 1-based Ameba line/column positions onto document offsets.
 * Out-of-range positions (stale results racing an edit) yield null so the
 * caller drops the issue instead of highlighting garbage. Pure function of
 * the document — fully unit-testable without the daemon.
 */
object AmebaRanges {

    fun toRange(document: Document, issue: AmebaIssue): com.intellij.openapi.util.TextRange? {
        val lineCount = document.lineCount
        if (lineCount == 0) return null
        val line = (issue.line - 1).coerceIn(0, lineCount - 1)
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val start = lineStart + (issue.column - 1).coerceIn(0, (lineEnd - lineStart).coerceAtLeast(0))
        val end = if (issue.endLine != null && issue.endColumn != null) {
            // Ameba end columns are inclusive (a single `x` reports 1:1→1:1),
            // so convert to the exclusive end TextRange expects.
            val endLineIdx = (issue.endLine - 1).coerceIn(0, lineCount - 1)
            val endLineStart = document.getLineStartOffset(endLineIdx)
            val endLineEnd = document.getLineEndOffset(endLineIdx)
            endLineStart + issue.endColumn.coerceIn(0, (endLineEnd - endLineStart).coerceAtLeast(0))
        } else {
            // Range-less (flycheck) issues: single character, or the whole
            // line when even that is out of bounds.
            if (start < lineEnd) start + 1 else lineEnd
        }
        if (start > end) return null
        if (start == end && start == document.textLength && start > 0) return null
        return com.intellij.openapi.util.TextRange(start, end.coerceAtMost(document.textLength))
    }
}
