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

        // Keywords that should be dedented to match their opening block keyword
        private val DEDENT_KEYWORDS = setOf("else", "elsif", "end", "when", "ensure", "rescue", "in")
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

        // Electric dedent: if the previous line is a dedent keyword (else, end, etc.),
        // adjust its indentation to match the opening block keyword
        val prevTrimmedStart = prevLineText.trimStart()
        val dedentKeyword = DEDENT_KEYWORDS.find { kw ->
            prevTrimmedStart == kw || prevTrimmedStart.startsWith("$kw ") || prevTrimmedStart.startsWith("$kw\t")
        }
        if (dedentKeyword != null) {
            val expectedIndent = findOpeningBlockIndent(document, prevLineNumber, dedentKeyword)
            if (expectedIndent != null) {
                val currentIndentLen = prevLineText.length - prevLineText.trimStart().length
                val currentIndent = prevLineText.substring(0, currentIndentLen)
                if (currentIndent != expectedIndent) {
                    // Fix the previous line's indentation
                    document.replaceString(prevLineStart, prevLineStart + currentIndentLen, expectedIndent)
                    val delta = expectedIndent.length - currentIndentLen
                    // Also fix caret line position (it shifted)
                    editor.caretModel.moveToOffset(editor.caretModel.offset + delta)
                }
            }
            // For dedent keywords that also open a sub-block (else, elsif, rescue, ensure, when, in),
            // we still want to indent the new line one level deeper
            if (dedentKeyword != "end") {
                val updatedCaretOffset = editor.caretModel.offset
                val updatedCaretLine = document.getLineNumber(updatedCaretOffset)
                val updatedPrevLineStart = document.getLineStartOffset(updatedCaretLine - 1)
                val updatedPrevLineEnd = document.getLineEndOffset(updatedCaretLine - 1)
                val updatedPrevLineText = document.getText(TextRange(updatedPrevLineStart, updatedPrevLineEnd))
                val baseIndent = updatedPrevLineText.takeWhile { it == ' ' || it == '\t' }
                val newIndent = "$baseIndent  "
                val currentLineStart = document.getLineStartOffset(updatedCaretLine)
                val currentLineEnd = document.getLineEndOffset(updatedCaretLine)
                val currentLineText = document.getText(TextRange(currentLineStart, currentLineEnd))
                val currentLineContent = currentLineText.trimStart()
                document.replaceString(currentLineStart, currentLineEnd, "$newIndent$currentLineContent")
                editor.caretModel.moveToOffset(currentLineStart + newIndent.length)
                return EnterHandlerDelegate.Result.Stop
            }
            // For "end", just adjust indent of cursor line to match end's level
            val updatedCaretOffset = editor.caretModel.offset
            val updatedCaretLine = document.getLineNumber(updatedCaretOffset)
            val updatedPrevLineStart = document.getLineStartOffset(updatedCaretLine - 1)
            val updatedPrevLineEnd = document.getLineEndOffset(updatedCaretLine - 1)
            val updatedPrevLineText = document.getText(TextRange(updatedPrevLineStart, updatedPrevLineEnd))
            val baseIndent = updatedPrevLineText.takeWhile { it == ' ' || it == '\t' }
            val currentLineStart = document.getLineStartOffset(updatedCaretLine)
            val currentLineEnd = document.getLineEndOffset(updatedCaretLine)
            val currentLineText = document.getText(TextRange(currentLineStart, currentLineEnd))
            val currentLineContent = currentLineText.trimStart()
            document.replaceString(currentLineStart, currentLineEnd, "$baseIndent$currentLineContent")
            editor.caretModel.moveToOffset(currentLineStart + baseIndent.length)
            return EnterHandlerDelegate.Result.Stop
        }

        // Handle brace/bracket enter: { | } or [ | ]
        if (trimmed.endsWith("{") || trimmed.endsWith("[")) {
            val totalLines = document.lineCount
            if (caretLine < totalLines) {
                val nextLineStart = document.getLineStartOffset(caretLine)
                val nextLineEnd = document.getLineEndOffset(caretLine)
                val nextLineText = document.getText(TextRange(nextLineStart, nextLineEnd))
                val nextTrimmed = nextLineText.trimStart()
                if (nextTrimmed.startsWith("}") || nextTrimmed.startsWith("]")) {
                    // We're between matching braces — indent cursor and move close brace down
                    // Use opener line's indent (not the [ line) so ] aligns with a = [...]
                    val baseIndent = findOpeningBracketIndent(document, prevLineNumber) ?: prevLineText.takeWhile { it == ' ' || it == '\t' }
                    val newIndent = "$baseIndent  "
                    val currentLineStart = document.getLineStartOffset(caretLine)
                    val currentLineEnd = document.getLineEndOffset(caretLine)

                    // Replace current line (which has the closing brace) with:
                    // - indented cursor line
                    // - closing brace on its own line with opener indent
                    document.replaceString(currentLineStart, currentLineEnd, "$newIndent\n$baseIndent${nextTrimmed}")
                    editor.caretModel.moveToOffset(currentLineStart + newIndent.length)
                    return EnterHandlerDelegate.Result.Stop
                }
            }

            // Fallback: previous line ends with { or [ but closing brace is not on next line
            // Just indent the cursor one level deeper
            val baseIndent = prevLineText.takeWhile { it == ' ' || it == '\t' }
            val newIndent = "$baseIndent  "
            val currentLineStart = document.getLineStartOffset(caretLine)
            val currentLineEnd = document.getLineEndOffset(caretLine)
            val currentLineText = document.getText(TextRange(currentLineStart, currentLineEnd))
            val currentLineContent = currentLineText.trimStart()
            document.replaceString(currentLineStart, currentLineEnd, "$newIndent$currentLineContent")
            editor.caretModel.moveToOffset(currentLineStart + newIndent.length)
            return EnterHandlerDelegate.Result.Stop
        }

        // Handle continuation inside collections (lines ending with ,)
        if (trimmed.endsWith(",") && isInsideUnclosedBracket(document, prevLineNumber)) {
            val elemIndent = findFirstElementIndent(document, prevLineNumber) ?: ""
            val currentLineStart = document.getLineStartOffset(caretLine)
            val currentLineEnd = document.getLineEndOffset(caretLine)
            val currentLineText = document.getText(TextRange(currentLineStart, currentLineEnd))
            val currentLineContent = currentLineText.trimStart()
            document.replaceString(currentLineStart, currentLineEnd, "$elemIndent$currentLineContent")
            editor.caretModel.moveToOffset(currentLineStart + elemIndent.length)
            return EnterHandlerDelegate.Result.Stop
        }

        // Handle closing bracket/brace on its own line inside a collection
        // e.g. pressing Enter after 3 in: a = [1,\n     2,3<caret>]
        // The ] should align with the opener (variable name), not the previous element
        val caretLineText = document.getText(TextRange(document.getLineStartOffset(caretLine), document.getLineEndOffset(caretLine)))
        val caretLineTrimmed = caretLineText.trimStart()
        if ((caretLineTrimmed.startsWith("]") || caretLineTrimmed.startsWith("}")) && isInsideUnclosedBracket(document, prevLineNumber)) {
            val baseIndent = findOpeningBracketIndent(document, prevLineNumber) ?: ""
            val currentLineStart = document.getLineStartOffset(caretLine)
            val currentLineEnd = document.getLineEndOffset(caretLine)
            document.replaceString(currentLineStart, currentLineEnd, "$baseIndent$caretLineTrimmed")
            editor.caretModel.moveToOffset(currentLineStart + baseIndent.length)
            return EnterHandlerDelegate.Result.Stop
        }

        if (!endsWithBlockOpener(trimmed)) return EnterHandlerDelegate.Result.Continue

        // For "a = if expr" patterns, align body/end with the keyword, not the line start
        val assignKwPattern = Regex("""=\s+(if|unless|while|until|case|begin)\b""")
        val assignMatch = assignKwPattern.find(trimmed)

        val (baseIndent, newIndent) = if (assignMatch != null) {
            // Find the keyword column in the trimmed line
            val kw = assignMatch.groupValues[1]
            val kwCol = trimmed.indexOf(kw, assignMatch.range.first)
            // end aligns with keyword; body = keyword + 2
            Pair(" ".repeat(kwCol), " ".repeat(kwCol + 2))
        } else {
            val lineIndent = prevLineText.takeWhile { it == ' ' || it == '\t' }
            Pair(lineIndent, "$lineIndent  ")
        }

        val currentLineStart = document.getLineStartOffset(caretLine)
        val currentLineEnd = document.getLineEndOffset(caretLine)
        val currentLineText = document.getText(TextRange(currentLineStart, currentLineEnd))
        val currentLineContent = currentLineText.trimStart()

        // Replace current line content with proper indentation
        document.replaceString(currentLineStart, currentLineEnd, "$newIndent$currentLineContent")
        editor.caretModel.moveToOffset(currentLineStart + newIndent.length)

        // Check if 'end' already exists below the cursor at the same indent level.
        // Stop early if we encounter another block opener at the same indent —
        // the next 'end' at that level belongs to that opener, not to us.
        val updatedCaretLine = document.getLineNumber(editor.caretModel.offset)
        val totalLines = document.lineCount
        var hasEndBelow = false
        for (line in (updatedCaretLine + 1) until totalLines) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            val trimmed = lineText.trim()
            if (trimmed.isEmpty()) continue
            val lineIndent = lineText.takeWhile { it == ' ' || it == '\t' }
            if (lineIndent == baseIndent && endsWithBlockOpener(trimmed)) {
                // Another block starts at our level — its 'end' is not ours
                break
            }
            if (trimmed == "end" && lineIndent == baseIndent) {
                hasEndBelow = true
                break
            }
            // If we hit content at a lower indent, we've left the block — no end found
            if (lineIndent.length < baseIndent.length) break
        }

        if (hasEndBelow) return EnterHandlerDelegate.Result.Stop

        // Balance check: scan the entire document to decide if 'end' is needed
        val fullText = document.text
        if (isDocumentBalanced(fullText)) return EnterHandlerDelegate.Result.Stop

        // Insert 'end' on a new line below the cursor, aligned with the block keyword
        val updatedLineEnd = document.getLineEndOffset(updatedCaretLine)
        document.insertString(updatedLineEnd, "\n${baseIndent}end")

        return EnterHandlerDelegate.Result.Stop
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

        // "var = if expr", "result = case x", "var = begin"
        // Assignment to a block keyword — NOT suffix-if (which has no = before the keyword)
        val assignKwPattern = Regex("""=\s+(if|unless|while|until|case|begin)\b""")
        if (assignKwPattern.containsMatchIn(trimmed)) {
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
     * Scans backwards from the current line to find the opening block keyword
     * and returns its indentation string.
     */
    private fun findOpeningBlockIndent(document: com.intellij.openapi.editor.Document, currentLine: Int, keyword: String): String? {
        val blockOpeners = setOf("if", "unless", "case", "while", "until", "begin", "def", "class", "module", "struct", "enum", "lib", "do", "for", "macro", "select", "fun", "annotation")
        val midKeywords = setOf("else", "elsif", "when", "in", "ensure", "rescue")

        var depth = 0
        for (line in (currentLine - 1) downTo 0) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val text = document.getText(TextRange(lineStart, lineEnd))
            val trimmed = text.trimStart()

            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val firstWord = trimmed.split(Regex("[\\s({]"), 2)[0]

            if (firstWord == "end") {
                depth++
                continue
            }

            if (depth > 0 && (firstWord in blockOpeners || endsWithBlockOpener(trimmed.trimEnd()))) {
                depth--
                continue
            }

            if (depth == 0) {
                if (firstWord in midKeywords) {
                    // Same level — skip and keep looking for opener
                    continue
                }
                if (firstWord in blockOpeners || endsWithBlockOpener(trimmed.trimEnd())) {
                    val lineIndent = text.substring(0, text.length - text.trimStart().length)
                    // For "var = if expr" patterns, return indent at keyword position
                    val assignMatch = Regex("""=\s+(if|unless|while|until|case|begin)\b""").find(trimmed)
                    if (assignMatch != null) {
                        val kw = assignMatch.groupValues[1]
                        val kwCol = trimmed.indexOf(kw, assignMatch.range.first)
                        return " ".repeat(lineIndent.length + kwCol)
                    }
                    return lineIndent
                }
            }
        }
        return null
    }

    /**
     * Check if the caret is inside an unclosed `[` or `{` by scanning backwards.
     */
    private fun isInsideUnclosedBracket(document: com.intellij.openapi.editor.Document, currentLine: Int): Boolean {
        var closeSquareCount = 0
        var closeCurlyCount = 0
        for (line in currentLine downTo 0) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val text = document.getText(TextRange(lineStart, lineEnd))
            val trimmed = text.trimEnd()

            for (i in (trimmed.length - 1) downTo 0) {
                val c = trimmed[i]
                when (c) {
                    ']' -> closeSquareCount++
                    '[' -> if (closeSquareCount > 0) closeSquareCount-- else return true
                    '}' -> closeCurlyCount++
                    '{' -> if (closeCurlyCount > 0) closeCurlyCount-- else return true
                }
            }
        }
        return false
    }

    /**
     * Scan backwards to find the unclosed `[` or `{` and return its line's indentation.
     */
    private fun findOpeningBracketIndent(document: com.intellij.openapi.editor.Document, currentLine: Int): String? {
        var closeSquareCount = 0
        var closeCurlyCount = 0
        for (line in currentLine downTo 0) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val text = document.getText(TextRange(lineStart, lineEnd))
            val trimmed = text.trimEnd()

            for (i in (trimmed.length - 1) downTo 0) {
                val c = trimmed[i]
                when (c) {
                    ']' -> closeSquareCount++
                    '[' -> if (closeSquareCount > 0) closeSquareCount-- else return text.takeWhile { it == ' ' || it == '\t' }
                    '}' -> closeCurlyCount++
                    '{' -> if (closeCurlyCount > 0) closeCurlyCount-- else return text.takeWhile { it == ' ' || it == '\t' }
                }
            }
        }
        return null
    }

    /**
     * Find the unclosed `[` or `{` and return the indentation string that aligns
     * with the first element after the bracket.
     * For `a = [1, 2, 3]` returns "     " (5 spaces, column of `1`).
     * For `a = [\n  1,` returns "  " (2 spaces, column of `1` on next line).
     */
    private fun findFirstElementIndent(document: com.intellij.openapi.editor.Document, currentLine: Int): String? {
        var closeSquareCount = 0
        var closeCurlyCount = 0
        for (line in currentLine downTo 0) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val text = document.getText(TextRange(lineStart, lineEnd))
            val trimmed = text.trimEnd()

            for (i in (trimmed.length - 1) downTo 0) {
                val c = trimmed[i]
                when (c) {
                    ']' -> closeSquareCount++
                    '[' -> {
                        if (closeSquareCount > 0) { closeSquareCount--; continue }
                        // Found the unclosed bracket. Now scan forward for first element.
                        val bracketOffset = lineStart + i
                        // Check same line: content after [
                        val restOfLine = trimmed.substring(i + 1).trimStart()
                        if (restOfLine.isNotEmpty() && !restOfLine.startsWith("\n")) {
                            // First element is on the same line as [
                            val firstElemCol = i + 1 + (trimmed.substring(i + 1).length - trimmed.substring(i + 1).trimStart().length)
                            return " ".repeat(firstElemCol)
                        }
                        // First element is on the next line — use that line's indentation
                        if (line + 1 <= currentLine) {
                            val nextLineStart = document.getLineStartOffset(line + 1)
                            val nextLineEnd = document.getLineEndOffset(line + 1)
                            val nextText = document.getText(TextRange(nextLineStart, nextLineEnd))
                            val nextIndentLen = nextText.length - nextText.trimStart().length
                            return nextText.substring(0, nextIndentLen)
                        }
                        // Fallback: opener + 2
                        return text.takeWhile { it == ' ' || it == '\t' } + "  "
                    }
                    '}' -> closeCurlyCount++
                    '{' -> {
                        if (closeCurlyCount > 0) { closeCurlyCount--; continue }
                        val restOfLine = trimmed.substring(i + 1).trimStart()
                        if (restOfLine.isNotEmpty() && !restOfLine.startsWith("\n")) {
                            val firstElemCol = i + 1 + (trimmed.substring(i + 1).length - trimmed.substring(i + 1).trimStart().length)
                            return " ".repeat(firstElemCol)
                        }
                        if (line + 1 <= currentLine) {
                            val nextLineStart = document.getLineStartOffset(line + 1)
                            val nextLineEnd = document.getLineEndOffset(line + 1)
                            val nextText = document.getText(TextRange(nextLineStart, nextLineEnd))
                            val nextIndentLen = nextText.length - nextText.trimStart().length
                            return nextText.substring(0, nextIndentLen)
                        }
                        return text.takeWhile { it == ' ' || it == '\t' } + "  "
                    }
                }
            }
        }
        return null
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
