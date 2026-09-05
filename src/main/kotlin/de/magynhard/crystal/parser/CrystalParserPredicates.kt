package de.magynhard.crystal.parser

import com.intellij.lang.PsiBuilder
import com.intellij.psi.TokenType
import de.magynhard.crystal.psi.CrystalTypes

object CrystalParserPredicates {
    @JvmStatic
    fun colonHasLeadingDeclarationWhitespace(
        builder: PsiBuilder,
        @Suppress("UNUSED_PARAMETER") level: Int,
    ): Boolean {
        if (builder.tokenType !== CrystalTypes.COLON) return false
        val offset = builder.currentOffset
        if (offset == 0) return false
        var rawStep = -1
        var start = offset
        while (builder.rawLookup(rawStep) === TokenType.WHITE_SPACE) {
            start = builder.rawTokenTypeStart(rawStep)
            rawStep--
        }
        val source = builder.originalText
        return (start until offset).any { index ->
            val next = source.getOrNull(index + 1)
            source[index] in " \t\u000C" ||
                source[index] == '\\' && (next == '\r' || next == '\n')
        }
    }
}
