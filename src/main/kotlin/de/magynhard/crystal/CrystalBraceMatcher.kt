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
        )
    }

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
