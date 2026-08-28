package de.magynhard.crystal.injection

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Parses and locates RubyMine-style language injection comments:
 *
 * ```
 * # language=<id> prefix="<text>" suffix="<text>"
 * ```
 *
 * The comment must be a whole-line Crystal line comment (`# ...`) on the line
 * immediately above the line containing the injection target (heredoc header
 * or string). Only whitespace may separate it from the target line.
 *
 * Attribute values are double-quoted (with `\"` / `\\` escapes) or bare
 * non-space tokens, mirroring the platform's IntelliLang comment syntax.
 */
object CrystalLanguageComment {

    data class ParsedComment(val languageId: String, val prefix: String, val suffix: String)

    /**
     * Mirrors the platform's IntelliLang attribute regex:
     * `key="value with \" escapes"` or `key=bareValue`.
     */
    private val ATTRIBUTE_REGEX = Regex("""([\S&&[^=]]+)=("(?:[^"]|\\")*"|\S*)""")
    private const val LANGUAGE_ATTRIBUTE = "language"

    /** Parses a comment's text (`# language=SQL prefix="select "`). */
    fun parse(commentText: String): ParsedComment? {
        var languageId: String? = null
        var prefix = ""
        var suffix = ""
        for (match in ATTRIBUTE_REGEX.findAll(commentText)) {
            val key = match.groupValues[1].lowercase()
            val rawValue = match.groupValues[2]
            if (key == LANGUAGE_ATTRIBUTE) {
                languageId = unescape(rawValue)
                continue
            }
            val value = unescape(rawValue)
            when (key) {
                "prefix" -> prefix = value
                "suffix" -> suffix = value
            }
        }
        languageId ?: return null
        if (languageId.isBlank()) return null
        return ParsedComment(languageId, prefix, suffix)
    }

    /**
     * Returns the parsed injection comment on the line directly above
     * [anchorOffset], or null. The previous line must be a whole-line Crystal
     * line comment; only whitespace may precede the `#`.
     */
    fun findAdjacentComment(file: PsiFile, anchorOffset: Int): ParsedComment? {
        val document = file.viewProvider.document ?: return null
        val anchorLine = document.getLineNumber(anchorOffset)
        if (anchorLine == 0) return null
        val lineStart = document.getLineStartOffset(anchorLine - 1)
        val lineEnd = document.getLineEndOffset(anchorLine - 1)
        val sequence = document.charsSequence

        var firstCharOffset = lineStart
        while (firstCharOffset < lineEnd && sequence[firstCharOffset].isWhitespace()) firstCharOffset++
        if (firstCharOffset >= lineEnd) return null

        val element: PsiElement = file.findElementAt(firstCharOffset) ?: return null
        if (element.node.elementType != CrystalTypes.LINE_COMMENT) return null
        if (element.textRange.startOffset != firstCharOffset) return null
        return parse(element.text)
    }

    /**
     * Lenient unescape for attribute values: strips surrounding double quotes
     * and resolves `\"`, `\\` and the common control escapes; unknown escapes
     * keep the escaped character (mirrors the platform's lenient handling).
     */
    private fun unescape(raw: String): String {
        var value = raw
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1)
        }
        if (!value.contains('\\')) return value
        val result = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\' || i + 1 >= value.length) {
                result.append(c)
                i++
                continue
            }
            when (val next = value[i + 1]) {
                '"' -> result.append('"')
                '\\' -> result.append('\\')
                'n' -> result.append('\n')
                't' -> result.append('\t')
                'r' -> result.append('\r')
                else -> result.append(next)
            }
            i += 2
        }
        return result.toString()
    }
}
