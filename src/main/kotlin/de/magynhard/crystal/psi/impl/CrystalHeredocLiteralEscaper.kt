package de.magynhard.crystal.psi.impl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import de.magynhard.crystal.psi.CrystalHeredocLiteral

/**
 * Identity escaper for injected [CrystalHeredocLiteral] hosts: heredoc bodies
 * contain their content verbatim (escapes stay in source form), and the body
 * is inherently multi-line.
 */
class CrystalHeredocLiteralEscaper(host: CrystalHeredocLiteral) :
    LiteralTextEscaper<PsiLanguageInjectionHost>(host) {

    override fun decode(rangeInsideHost: TextRange, builder: StringBuilder): Boolean {
        builder.append(rangeInsideHost.substring(myHost.text))
        return true
    }

    override fun getOffsetInHost(offsetInDecodedText: Int, rangeWithinHost: TextRange): Int =
        (rangeWithinHost.startOffset + offsetInDecodedText).coerceAtMost(rangeWithinHost.endOffset)

    override fun isOneLine(): Boolean = false
}
