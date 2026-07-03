package de.magynhard.crystal

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.psi.CrystalTypes

class CrystalBraceMatcher : PairedBraceMatcher {

    companion object {
        private val PAIRS = arrayOf(
            BracePair(CrystalTypes.LPAREN, CrystalTypes.RPAREN, false),
            BracePair(CrystalTypes.LBRACKET, CrystalTypes.RBRACKET, false),
            BracePair(CrystalTypes.LBRACE, CrystalTypes.RBRACE, true),
            BracePair(CrystalTypes.PERCENT_LITERAL_BEGIN, CrystalTypes.PERCENT_LITERAL_END, false),
            BracePair(CrystalTypes.DEF, CrystalTypes.END, true),
            BracePair(CrystalTypes.CLASS, CrystalTypes.END, true),
            BracePair(CrystalTypes.MODULE, CrystalTypes.END, true),
            BracePair(CrystalTypes.STRUCT, CrystalTypes.END, true),
            BracePair(CrystalTypes.ENUM, CrystalTypes.END, true),
            BracePair(CrystalTypes.ANNOTATION, CrystalTypes.END, true),
            BracePair(CrystalTypes.LIB, CrystalTypes.END, true),
            BracePair(CrystalTypes.MACRO, CrystalTypes.END, true),
            BracePair(CrystalTypes.VERBATIM, CrystalTypes.END, true),
            BracePair(CrystalTypes.IF, CrystalTypes.END, true),
            BracePair(CrystalTypes.UNLESS, CrystalTypes.END, true),
            BracePair(CrystalTypes.WHILE, CrystalTypes.END, true),
            BracePair(CrystalTypes.UNTIL, CrystalTypes.END, true),
            BracePair(CrystalTypes.FOR, CrystalTypes.END, true),
            BracePair(CrystalTypes.CASE, CrystalTypes.END, true),
            BracePair(CrystalTypes.DO, CrystalTypes.END, true),
            BracePair(CrystalTypes.BEGIN, CrystalTypes.END, true),
        )
    }

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
