package de.magynhard.crystal

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * Auto-inserts closing `}` when typing `{` after `#` inside a string (string interpolation).
 */
class CrystalTypedHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (c != '{') return Result.CONTINUE
        if (file.fileType != CrystalFileType) return Result.CONTINUE

        val offset = editor.caretModel.offset
        if (offset < 2) return Result.CONTINUE

        val document = editor.document
        val text = document.text

        // Check that the character before `{` is `#`
        if (text[offset - 2] != '#') return Result.CONTINUE

        // Check we're inside a string by scanning backwards for an unmatched opening `"`
        if (!isInsideString(text, offset - 2)) return Result.CONTINUE

        // Don't insert if there's already a `}` right after the cursor
        if (offset < text.length && text[offset] == '}') return Result.CONTINUE

        document.insertString(offset, "}")
        return Result.STOP
    }

    /**
     * Determines if the given position is inside a double-quoted string.
     * Scans backwards counting unescaped `"` characters.
     */
    private fun isInsideString(text: String, position: Int): Boolean {
        var quoteCount = 0
        var i = position - 1
        while (i >= 0) {
            if (text[i] == '"') {
                // Check if escaped
                var backslashes = 0
                var j = i - 1
                while (j >= 0 && text[j] == '\\') {
                    backslashes++
                    j--
                }
                if (backslashes % 2 == 0) {
                    quoteCount++
                }
            }
            i--
        }
        // Odd number of unescaped quotes means we're inside a string
        return quoteCount % 2 == 1
    }
}
