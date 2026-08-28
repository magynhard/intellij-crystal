package de.magynhard.crystal.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import de.magynhard.crystal.analysis.CrystalStringLiteralDecoder
import de.magynhard.crystal.psi.CrystalStringExpression
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Escaper for injected [CrystalStringExpression] hosts.
 *
 * Decodes Crystal escapes (`\n`, `\t`, `\u{…}`, octal, …) so the injected
 * language sees the string's runtime value, and maps decoded offsets back to
 * the encoded host offsets for highlighting and editing.
 *
 * Interpolation parts that fall inside a decode range (only for full-range
 * decodes — injection places exclude them) are appended 1:1 raw.
 */
class CrystalStringLiteralEscaper(host: CrystalStringExpression) :
    LiteralTextEscaper<PsiLanguageInjectionHost>(host) {

    private val mapping = mutableListOf<Int>()

    override fun decode(rangeInsideHost: TextRange, builder: StringBuilder): Boolean {
        mapping.clear()
        val hostStart = myHost.textRange.startOffset
        for (child in myHost.node.getChildren(null)) {
            val childStart = child.startOffset - hostStart
            val childEnd = childStart + child.textLength
            if (childEnd <= rangeInsideHost.startOffset) continue
            if (childStart >= rangeInsideHost.endOffset) break

            val sliceStart = maxOf(childStart, rangeInsideHost.startOffset)
            val sliceEnd = minOf(childEnd, rangeInsideHost.endOffset)
            val raw = child.text.substring(sliceStart - childStart, sliceEnd - childStart)

            if (child.elementType == CrystalTypes.STRING_ESCAPE) {
                val decoded = CrystalStringLiteralDecoder.decode(raw) ?: raw
                repeat(decoded.length) { mapping.add(sliceStart) }
                builder.append(decoded)
            } else {
                repeat(raw.length) { mapping.add(sliceStart + it) }
                builder.append(raw)
            }
        }
        return true
    }

    override fun getOffsetInHost(offsetInDecodedText: Int, rangeWithinHost: TextRange): Int {
        if (offsetInDecodedText < 0 || offsetInDecodedText >= mapping.size) {
            return rangeWithinHost.endOffset
        }
        return mapping[offsetInDecodedText].coerceIn(rangeWithinHost.startOffset, rangeWithinHost.endOffset)
    }

    override fun isOneLine(): Boolean = true
}
