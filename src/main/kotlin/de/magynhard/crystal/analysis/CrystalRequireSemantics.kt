package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalClassBody
import de.magynhard.crystal.psi.CrystalMacroInterpolation
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.psi.CrystalTopLevelFun

internal object CrystalRequireSemantics {
    fun errorMessage(statement: CrystalRequireStatement): String? {
        if (statement.stringExpression == null) return null
        if (PsiTreeUtil.getParentOfType(statement, CrystalMethodDefinition::class.java) != null) {
            return "Can't require inside def"
        }
        if (PsiTreeUtil.getParentOfType(statement, CrystalTopLevelFun::class.java) != null) {
            return "Can't require inside fun"
        }
        if (PsiTreeUtil.getParentOfType(statement, CrystalClassBody::class.java) != null) {
            return "Can't require inside type declarations"
        }
        if (PsiTreeUtil.getParentOfType(statement, CrystalMacroInterpolation::class.java) != null) {
            return "Can't execute Require in a macro"
        }
        var topLevelElement: PsiElement = statement
        while (topLevelElement.parent !is PsiFile) {
            topLevelElement = topLevelElement.parent ?: return "Can't require dynamically"
        }
        return if (topLevelElement.textRange == statement.textRange) null else "Can't require dynamically"
    }

    fun isValidTopLevel(statement: CrystalRequireStatement): Boolean = errorMessage(statement) == null
}
