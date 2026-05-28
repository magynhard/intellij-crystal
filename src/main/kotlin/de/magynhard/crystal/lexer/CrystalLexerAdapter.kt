package de.magynhard.crystal.lexer

import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.FlexLexer

/**
 * Lexer adapter that encodes interpolationDepth into the lexer state.
 * This ensures IntelliJ's incremental re-lexing correctly restores the
 * interpolation nesting level when starting mid-file.
 *
 * State encoding: lower 16 bits = JFlex yystate, upper 16 bits = interpolationDepth
 */
class CrystalLexerAdapter : FlexAdapter(CrystalLexer(null)) {

    override fun getState(): Int {
        val flexLexer = flex as CrystalLexer
        val baseState = super.getState() and 0xFFFF
        val depth = flexLexer.interpolationDepth
        return baseState or (depth shl 16)
    }

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        val baseState = initialState and 0xFFFF
        val depth = initialState ushr 16
        super.start(buffer, startOffset, endOffset, baseState)
        (flex as CrystalLexer).interpolationDepth = depth
    }
}
