package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.TreeElement
import de.magynhard.crystal.injection.CrystalStringExpressionInjection
import de.magynhard.crystal.psi.CrystalStringExpression
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for `string_expression` PSI elements — double-quoted string literals
 * (`"SELECT …"`), including their [CrystalTypes.STRING_ESCAPE] leaves and
 * inlined interpolations.
 *
 * Implements [PsiLanguageInjectionHost] so `# language=<id>` comments can
 * inject an embedded language into the string content (between the quotes).
 * The escaper decodes Crystal escapes so the injected language sees the
 * string's runtime value, mapping offsets back for highlighting.
 */
abstract class CrystalStringExpressionMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), CrystalStringExpression, PsiLanguageInjectionHost {

    override fun isValidHost(): Boolean = true

    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> =
        CrystalStringLiteralEscaper(this)

    /**
     * Writes an edited injected fragment back into the string content.
     *
     * Single-place strings (no interpolations) map the fragment text onto the
     * decoded content, so the edit is re-encoded into Crystal escapes and
     * written back exactly. Interpolated strings are not reconstructed from
     * the flat fragment text — the edit is ignored rather than corrupting the
     * interpolations.
     */
    override fun updateText(text: String): CrystalStringExpression {
        val places = CrystalStringExpressionInjection.computeContentSegments(this)
        if (places.size > 1) return this
        return replaceContentWith(encodeStringBody(text))
    }

    /**
     * Replaces the string content between the enclosing quotes with
     * [encodedBody] (already Crystal-escaped, no quotes).
     */
    fun replaceContentWith(encodedBody: String): CrystalStringExpression {
        val node = this.node as TreeElement
        val children = node.getChildren(null)
        if (children.isEmpty()) return this
        val first = children.first()
        val last = children.last()

        if (first === last) {
            val text = first.text
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                (first as LeafElement).replaceWithText("\"" + encodedBody + "\"")
            }
            return this
        }

        var current = first.treeNext
        while (current != null && current !== last) {
            val next = current.treeNext
            node.removeChild(current)
            current = next
        }
        if (encodedBody.isNotEmpty()) {
            node.addLeaf(CrystalTypes.STRING_LITERAL, encodedBody, last)
        }
        return this
    }

    companion object {
        /**
         * The injectable content span relative to the string expression:
         * between the enclosing quotes (absent quotes tolerated).
         */
        fun contentRange(stringExpression: CrystalStringExpression): TextRange {
            val children = stringExpression.node.getChildren(null)
            if (children.isEmpty()) return TextRange.EMPTY_RANGE
            val base = stringExpression.textRange.startOffset
            val first = children.first()
            val last = children.last()
            val start = base + if (first.text.startsWith("\"")) 1 else 0
            val end = if (children.size > 1 && last.textLength > 0 && last.text.endsWith("\"")) {
                last.startOffset + last.textLength - 1
            } else {
                last.startOffset + last.textLength
            }
            val relativeStart = start - base
            val relativeEnd = end - base
            return TextRange(relativeStart, maxOf(relativeStart, relativeEnd))
        }

        /**
         * Encodes a decoded string value as a Crystal double-quoted string
         * body (no surrounding quotes): escapes backslashes, quotes, control
         * characters, and `#{` so re-lexing cannot create an interpolation.
         */
        fun encodeStringBody(text: String): String {
            val result = StringBuilder(text.length + 8)
            for ((index, c) in text.withIndex()) {
                when {
                    c == '\\' -> result.append("\\\\")
                    c == '"' -> result.append("\\\"")
                    c == '\n' -> result.append("\\n")
                    c == '\r' -> result.append("\\r")
                    c == '\t' -> result.append("\\t")
                    c == '#' && index + 1 < text.length && text[index + 1] == '{' -> result.append("\\#")
                    else -> result.append(c)
                }
            }
            return result.toString()
        }
    }
}
