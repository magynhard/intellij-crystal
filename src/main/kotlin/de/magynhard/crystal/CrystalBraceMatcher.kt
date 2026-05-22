package de.magynhard.crystal

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.lexer.CrystalTokenTypes

class CrystalBraceMatcher : PairedBraceMatcher {

    companion object {
        private val PAIRS = arrayOf(
            BracePair(CrystalTokenTypes.LPAREN, CrystalTokenTypes.RPAREN, false),
            BracePair(CrystalTokenTypes.LBRACKET, CrystalTokenTypes.RBRACKET, false),
            BracePair(CrystalTokenTypes.LBRACE, CrystalTokenTypes.RBRACE, false),
            BracePair(CrystalTokenTypes.PERCENT_LITERAL_BEGIN, CrystalTokenTypes.PERCENT_LITERAL_END, false),
        )
    }

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
