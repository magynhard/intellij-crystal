package de.magynhard.crystal

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class CrystalEnterHandler : EnterHandlerDelegateAdapter() {

    companion object {
        // Keywords that open a block requiring 'end'
        private val BLOCK_OPENERS = setOf(
            "def", "class", "module", "struct", "enum",
            "if", "unless", "while", "until", "case",
            "begin", "do", "macro", "lib", "fun", "annotation"
        )

        // All keywords that open a block (for balance counting)
        private val OPENER_KEYWORDS = setOf(
            "def", "class", "module", "struct", "enum",
            "if", "unless", "while", "until", "case",
            "begin", "do", "macro", "lib", "fun", "annotation",
            "for"
        )
    }

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result {
        if (file.fileType != CrystalFileType) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val caretOffset = editor.caretModel.offset
        val caretLine = document.getLineNumber(caretOffset)

        // We need to check the line ABOVE the caret (where the user pressed Enter)
        if (caretLine < 1) return EnterHandlerDelegate.Result.Continue
        val prevLineNumber = caretLine - 1
        val prevLineStart = document.getLineStartOffset(prevLineNumber)
        val prevLineEnd = document.getLineEndOffset(prevLineNumber)
        val prevLineText = document.getText(TextRange(prevLineStart, prevLineEnd))

        // Check if previous line ends with a block-opening keyword
        val trimmed = prevLineText.trimEnd()
        if (trimmed.isEmpty()) return EnterHandlerDelegate.Result.Continue

        if (!endsWithBlockOpener(trimmed)) return EnterHandlerDelegate.Result.Continue

        // Balance check: scan the entire document
        // Count all openers vs all 'end's
        val fullText = document.text
        if (isDocumentBalanced(fullText)) return EnterHandlerDelegate.Result.Continue

        // Insert 'end' on a new line below the cursor, with same indentation as the opener line
        val indent = prevLineText.takeWhile { it == ' ' || it == '\t' }

        // Find the end of the current caret line to insert after it
        val currentLineEnd = document.getLineEndOffset(caretLine)
        document.insertString(currentLineEnd, "\n${indent}end")

        return EnterHandlerDelegate.Result.Continue
    }

    /**
     * Check if a line (trimmed) ends with a block-opening construct.
     */
    private fun endsWithBlockOpener(trimmed: String): Boolean {
        // Extract the last word of the line
        val lastWord = trimmed.split(Regex("\\s+")).lastOrNull() ?: return false

        // Direct keyword at end of line: "do", "begin", etc.
        if (lastWord in BLOCK_OPENERS) return true

        // Lines starting with block keywords: "def foo", "def foo(x)", "class Foo < Bar"
        // The line may end with ")" or identifier, but starts with the keyword
        val firstWord = trimmed.trimStart().split(Regex("\\s+")).firstOrNull() ?: return false
        if (firstWord in setOf("def", "class", "module", "struct", "enum", "macro", "lib", "fun", "annotation")) {
            return true
        }

        // "if expr", "unless expr", "while expr", "until expr", "case expr"
        // But NOT suffix-if: "return x if condition" — suffix-if has something before "if"
        if (firstWord in setOf("if", "unless", "while", "until", "case", "begin", "for")) {
            return true
        }

        return false
    }

    /**
     * Check if the entire document has balanced block openers and 'end's.
     * Returns true if openers == ends (balanced), meaning no additional 'end' is needed.
     */
    private fun isDocumentBalanced(text: String): Boolean {
        var depth = 0
        for (word in tokenizeWords(text)) {
            when {
                word in OPENER_KEYWORDS -> depth++
                word == "end" -> depth--
            }
        }
        return depth <= 0
    }

    /**
     * Extract words from text, skipping comments and string contents.
     */
    private fun tokenizeWords(text: String): List<String> {
        val words = mutableListOf<String>()
        var i = 0
        val len = text.length

        while (i < len) {
            val c = text[i]
            when {
                c == '#' -> {
                    // Skip to end of line (comment)
                    while (i < len && text[i] != '\n') i++
                }
                c == '"' -> {
                    // Skip string content
                    i++
                    while (i < len && text[i] != '"') {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < len) i++
                }
                c == '\'' -> {
                    // Skip char literal
                    i++
                    while (i < len && text[i] != '\'') {
                        if (text[i] == '\\') i++
                        i++
                    }
                    if (i < len) i++
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '?')) i++
                    words.add(text.substring(start, i))
                }
                else -> i++
            }
        }
        return words
    }
}
