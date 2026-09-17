package de.magynhard.crystal.parser

import com.intellij.lang.PsiBuilder
import com.intellij.psi.TokenType
import de.magynhard.crystal.psi.CrystalTypes

object CrystalParserPredicates {
    private val SPLAT_FRAGMENT_SUFFIX = Regex("[\\s\\S]*\\.\\s*splat\\s*(\\([^()]*\\))?\\s*")

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
     * True when the previous significant token is an assignment operator
     * (`=`, `||=`, `+=`, …). Gates the stray-operator recovery alternative:
     * right after `variable assign_op`, a purely infix-capable operator can
     * never start a valid right-hand side, so the pinned assignment rule has
     * already recorded its error there and the stray token may be consumed
     * silently to resync parsing. Anywhere else (leading `&& x`, trailing
     * `a &&`, block-param bars) no upstream error exists, and swallowing the
     * token would mask a genuine syntax error — so the gate stays closed and
     * the previous error behavior is preserved. Only whitespace and newlines
     * are skipped looking back; comments or anything else fail closed.
     */
    @JvmStatic
    fun isAfterAssignOp(
        builder: PsiBuilder,
        @Suppress("UNUSED_PARAMETER") level: Int,
    ): Boolean {
        var step = -1
        while (true) {
            val token = builder.rawLookup(step) ?: return false
            if (token === TokenType.WHITE_SPACE || token === CrystalTypes.NEWLINE) {
                step--
                continue
            }
            return token === CrystalTypes.ASSIGN ||
                token === CrystalTypes.PLUS_ASSIGN ||
                token === CrystalTypes.MINUS_ASSIGN ||
                token === CrystalTypes.STAR_ASSIGN ||
                token === CrystalTypes.SLASH_ASSIGN ||
                token === CrystalTypes.PERCENT_ASSIGN ||
                token === CrystalTypes.AMPERSAND_ASSIGN ||
                token === CrystalTypes.PIPE_ASSIGN ||
                token === CrystalTypes.CARET_ASSIGN ||
                token === CrystalTypes.DOUBLE_STAR_ASSIGN ||
                token === CrystalTypes.DOUBLE_SLASH_ASSIGN ||
                token === CrystalTypes.LSHIFT_ASSIGN ||
                token === CrystalTypes.RSHIFT_ASSIGN ||
                token === CrystalTypes.OR_OR_ASSIGN ||
                token === CrystalTypes.AND_AND_ASSIGN
        }
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
    /**
     * Gates macro-generated parameter fragments: immediately after a
     * `macro_interpolation` element, accepts only fragments whose text ends
     * with `.splat` (optionally with an argument list), e.g.
     * `{{ properties.map do |field| ... end.splat }}` or
     * `{{ operands.splat(", ") }}`. Bare `{{ x }}` fragments stay syntax
     * errors, so the parameter rule keeps rejecting them.
     */
    @JvmStatic
    fun isMacroSplatFragment(
        builder: PsiBuilder,
        @Suppress("UNUSED_PARAMETER") level: Int,
    ): Boolean {
        var step = -1
        var token = builder.rawLookup(step)
        while (token === TokenType.WHITE_SPACE || token === CrystalTypes.NEWLINE) {
            step--
            token = builder.rawLookup(step)
        }
        if (token !== CrystalTypes.MACRO_INTERPOLATION_END) return false
        val fragmentEnd = builder.rawTokenTypeStart(step)
        var depth = 0
        while (true) {
            step--
            token = builder.rawLookup(step) ?: return false
            if (token === CrystalTypes.MACRO_INTERPOLATION_END) {
                depth++
            } else if (token === CrystalTypes.MACRO_INTERPOLATION_BEGIN) {
                if (depth == 0) {
                    // The begin token is always exactly `{{`.
                    val fragment = builder.originalText.subSequence(
                        builder.rawTokenTypeStart(step) + 2,
                        fragmentEnd
                    )
                    return SPLAT_FRAGMENT_SUFFIX.containsMatchIn(fragment)
                }
                depth--
            }
        }
    }

    @JvmStatic
    fun isDotBareArgsBinaryOp(
        builder: PsiBuilder,
        @Suppress("UNUSED_PARAMETER") level: Int,
    ): Boolean = when (builder.tokenType) {
        CrystalTypes.PLUS, CrystalTypes.SLASH,
        CrystalTypes.DOUBLE_SLASH, CrystalTypes.PERCENT,
        CrystalTypes.DOTDOT, CrystalTypes.DOTDOTDOT,
        -> true
        CrystalTypes.STAR, CrystalTypes.DOUBLE_STAR,
        CrystalTypes.MINUS -> {
            // Tight splats are call arguments (`start_attribute *args,
            // **nargs` in xml/builder.cr — the compiler only rejects `*`/`**`
            // followed by whitespace in parse_call_args_space_consumed); spaced
            // forms stay binary operators, and tight `a*b` still binds through
            // the index-postfix tightness guard, never the bare-argument path.
            // Tight minus behaves the same (`-span.to_i` negates the first
            // bare argument, ` - ` is binary).
            val tokenLength = builder.tokenText?.length ?: 1
            val next = builder.originalText.getOrNull(builder.currentOffset + tokenLength)
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
