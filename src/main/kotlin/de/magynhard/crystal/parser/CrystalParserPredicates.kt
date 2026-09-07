package de.magynhard.crystal.parser

import com.intellij.lang.PsiBuilder
import com.intellij.psi.TokenType
import de.magynhard.crystal.psi.CrystalTypes

object CrystalParserPredicates {
    @JvmStatic
    fun isRecordDeclaration(
        builder: PsiBuilder,
        @Suppress("UNUSED_PARAMETER") level: Int,
    ): Boolean = builder.tokenType === CrystalTypes.IDENTIFIER && builder.tokenText == "record"

    /**
     * Distinguishes the queued heredoc BODY opener from a header marker. The lexer
     * emits both as HEREDOC_START, but the body opener's token text is the newline
     * it consumes while a header carries the delimiter text (`<<-MSG`). Nested bare
     * calls (`fail <<-MSG, file, line`) must not bind the body opener as their own
     * argument; the bodies attach at statement level instead.
     */
    @JvmStatic
    fun isHeredocBodyOpener(
        builder: PsiBuilder,
        @Suppress("UNUSED_PARAMETER") level: Int,
    ): Boolean = builder.tokenType === CrystalTypes.HEREDOC_START && builder.tokenText?.startsWith("\n") == true

    /**
     * True when the current token starts immediately after the previous raw token
     * with no whitespace or newline in between. Macro-generated names concatenate
     * textually (`{{ type.capitalize }}Def`) while macro-call arguments are
     * whitespace separated (`{{ method.id }} path`), so fragment suffixes bind
     * only when tight.
     */
    @JvmStatic
    fun isTokenTightAfterPreviousToken(
        builder: PsiBuilder,
        @Suppress("UNUSED_PARAMETER") level: Int,
    ): Boolean {
        val previous = builder.rawLookup(-1) ?: return false
        return previous !== TokenType.WHITE_SPACE && previous !== CrystalTypes.NEWLINE
    }

    /**
     * Binary-operator lookahead for dot-call bare arguments, honoring Crystal's
     * whitespace rule for unary operators: `Time.monotonic - start` is the
     * binary minus (spaced after the operator), while `file.seek -ZIP_TAIL_SIZE`
     * and `shift -span.to_i` use the tight minus as the unary negation of the
     * first bare argument. The range operators (`..`, `...`) are binary only —
     * they always bind to the left expression (`0.seconds..1.day` is a Range,
     * never `0.seconds(..1.day)`); Crystal rejects a leading `..` in bare
     * argument position ("wrong number of arguments").
     */
    @JvmStatic
    fun isDotBareArgsBinaryOp(
        builder: PsiBuilder,
        @Suppress("UNUSED_PARAMETER") level: Int,
    ): Boolean = when (builder.tokenType) {
        CrystalTypes.PLUS, CrystalTypes.STAR, CrystalTypes.SLASH,
        CrystalTypes.DOUBLE_SLASH, CrystalTypes.PERCENT, CrystalTypes.DOUBLE_STAR,
        CrystalTypes.DOTDOT, CrystalTypes.DOTDOTDOT,
        -> true
        CrystalTypes.MINUS -> {
            // Spaced minus = binary operator; tight minus (`-ZIP_TAIL_SIZE`,
            // `-span.to_i`) = the unary negation of the first bare argument.
            val next = builder.originalText.getOrNull(builder.currentOffset + 1)
            next == ' ' || next == '\t' || next == '\n' || next == '\r'
        }
        else -> false
    }


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
