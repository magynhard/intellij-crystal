package de.magynhard.crystal.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import de.magynhard.crystal.lexer.CrystalTokenTypes

class CrystalRefactoringSupportProvider : RefactoringSupportProvider() {

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        val tokenType = element.node?.elementType
        return tokenType == CrystalTokenTypes.IDENTIFIER || tokenType == CrystalTokenTypes.CONSTANT
    }
}
