package de.magynhard.crystal.navigation

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import de.magynhard.crystal.lexer.CrystalLexerAdapter
import de.magynhard.crystal.psi.CrystalTypes

class CrystalFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner() = DefaultWordsScanner(
        CrystalLexerAdapter(),
        TokenSet.create(CrystalTypes.IDENTIFIER, CrystalTypes.CONSTANT),
        TokenSet.create(CrystalTypes.LINE_COMMENT),
        TokenSet.create(CrystalTypes.STRING_LITERAL)
    )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        val tokenType = psiElement.node?.elementType
        return tokenType == CrystalTypes.IDENTIFIER || tokenType == CrystalTypes.CONSTANT
    }

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String {
        return when (element.node?.elementType) {
            CrystalTypes.CONSTANT -> "type"
            CrystalTypes.IDENTIFIER -> "symbol"
            else -> "element"
        }
    }

    override fun getDescriptiveName(element: PsiElement): String = element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = element.text
}
