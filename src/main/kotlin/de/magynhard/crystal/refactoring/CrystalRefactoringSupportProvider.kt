package de.magynhard.crystal.refactoring

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalTypes

class CrystalRefactoringSupportProvider : RefactoringSupportProvider() {

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        val tokenType = element.node?.elementType
        return tokenType == CrystalTypes.IDENTIFIER || tokenType == CrystalTypes.CONSTANT
    }
}
