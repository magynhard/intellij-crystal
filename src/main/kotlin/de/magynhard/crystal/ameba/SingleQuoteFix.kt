package de.magynhard.crystal.ameba

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * Pure conversion logic for the single-quote quickfix (no platform
 * dependencies — fully unit-testable).
 *
 * Ameba offers no correction for char-literal syntax errors, so the IDE
 * converts `'...'` to `"..."` itself. Only *invalid* char literals qualify:
 * a valid single-char literal (`'a'`, `'\n'`) is a type change, not a fix,
 * and is left alone.
 */
internal data class QuoteFix(val range: TextRange, val replacement: String)

internal fun findSingleQuoteFix(text: String, offset: Int): QuoteFix? {
    if (offset !in text.indices || text[offset] != '\'') return null
    // Char literals cannot span lines: the scan stays on this line, so code
    // can never be swallowed into the replacement.
    var lineEnd = offset + 1
    while (lineEnd < text.length && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
    var i = offset + 1
    var charCount = 0
    var closedAt = -1
    while (i < lineEnd) {
        val c = text[i]
        if (c == '\\' && i + 1 < lineEnd) {
            charCount++
            i += 2
            continue
        }
        if (c == '\'') {
            closedAt = i
            break
        }
        charCount++
        i++
    }
    if (closedAt >= 0) {
        // A single value (plain char or one escape) is a valid char literal:
        // converting would change the type, not fix an error.
        if (charCount == 1) return null
        val raw = text.substring(offset + 1, closedAt)
        return QuoteFix(TextRange(offset, closedAt + 1), "\"" + escapeForDoubleQuoted(raw) + "\"")
    }
    // Unterminated: only a whitespace-free tail may be closed at end of line
    // (`'ab` → `"ab"`); anything else is unknowable intent, never swallowed.
    val raw = text.substring(offset + 1, lineEnd)
    val stripped = raw.trimEnd(' ', '\t')
    if (stripped.any { it == ' ' || it == '\t' }) return null
    return QuoteFix(TextRange(offset, lineEnd), "\"" + escapeForDoubleQuoted(stripped) + "\"")
}

/**
 * Whether the quickfix applies to [issue] whose annotation starts with
 * [firstChar] (null when out of bounds): syntax-rule findings anchored on a
 * single-quote character. The rule check is intentionally loose across
 * Ameba versions (`Lint/Syntax` or bare `Syntax`); the quote anchor makes
 * it precise.
 */
internal fun shouldOfferQuoteFix(issue: AmebaIssue, firstChar: Char?): Boolean {
    if (firstChar != '\'') return false
    return issue.ruleName.substringAfterLast('/').equals("Syntax", ignoreCase = true)
}

/**
 * Editor intention and batch quickfix converting an invalid single-quoted
 * literal to double quotes. Attached per annotation (same popup,
 * Alt+Enter); offered only where [shouldOfferQuoteFix] holds, executed
 * only where [findSingleQuoteFix] resolves (caret at or just after the
 * opening quote) — anywhere else it safely does nothing.
 */
class SingleQuoteToDoubleQuoteFix : LocalQuickFix, IntentionAction {

    override fun getName(): String = "Convert single quotes to double quotes"

    override fun getFamilyName(): String = "Convert single quotes to double quotes"

    override fun getText(): String = name

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        return isLintableAmebaFile(file)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val caretOffset = editor?.caretModel?.offset ?: return
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile ?: return) ?: return
        // Caret exactly on the quote, or just after it — computed once on
        // the unchanged text so a first hit can never double-apply.
        var fix = findSingleQuoteFix(document.text, caretOffset)
        if (fix == null && caretOffset > 0) {
            fix = findSingleQuoteFix(document.text, caretOffset - 1)
        }
        if (fix == null) return
        WriteCommandAction.runWriteCommandAction(project, "Convert single quotes to double quotes", null, {
            document.replaceString(fix.range.startOffset, fix.range.endOffset, fix.replacement)
        })
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val file = element.containingFile ?: return
        val range = descriptor.textRangeInElement ?: return
        val base = element.textRange?.startOffset ?: 0
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile ?: return) ?: return
        val fix = findSingleQuoteFix(document.text, base + range.startOffset) ?: return
        WriteCommandAction.runWriteCommandAction(project, "Convert single quotes to double quotes", null, {
            document.replaceString(fix.range.startOffset, fix.range.endOffset, fix.replacement)
        })
    }
}

/**
 * Escapes raw char-literal content for a double-quoted string: existing
 * `\x` escapes pass through verbatim, `"` becomes `\"`, and `#{` becomes
 * `\#{` (it would otherwise activate interpolation). A trailing lone
 * backslash (incomplete escape in broken input) is dropped instead of
 * escaping the new closing quote.
 */
internal fun escapeForDoubleQuoted(raw: String): String {
    val sb = StringBuilder(raw.length)
    var k = 0
    while (k < raw.length) {
        val c = raw[k]
        if (c == '\\' && k + 1 < raw.length) {
            sb.append(c)
            sb.append(raw[k + 1])
            k += 2
        } else if (c == '\\') {
            k++
        } else if (c == '"') {
            sb.append("\\\"")
            k++
        } else if (c == '#' && k + 1 < raw.length && raw[k + 1] == '{') {
            sb.append("\\#{")
            k += 2
        } else {
            sb.append(c)
            k++
        }
    }
    return sb.toString()
}
