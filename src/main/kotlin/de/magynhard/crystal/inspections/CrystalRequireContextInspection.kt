package de.magynhard.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalClassBody
import de.magynhard.crystal.psi.CrystalMacroControl
import de.magynhard.crystal.psi.CrystalMacroInterpolation
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.psi.CrystalTopLevelFun
import de.magynhard.crystal.psi.CrystalTypes

/** Reports Crystal compiler errors for `require` outside file scope. */
class CrystalRequireContextInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalRequireStatement -> {
                        val message = errorMessage(element) ?: return
                        val keyword = element.node.findChildByType(CrystalTypes.REQUIRE)?.psi ?: element
                        holder.registerProblem(keyword, message, ProblemHighlightType.GENERIC_ERROR)
                    }
                    is CrystalMacroControl -> checkMacroControl(element, holder)
                }
            }
        }

    private fun errorMessage(statement: CrystalRequireStatement): String? {
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
        if (topLevelElement.textRange == statement.textRange) return null

        return "Can't require dynamically"
    }

    private fun checkMacroControl(control: CrystalMacroControl, holder: ProblemsHolder) {
        val requireTokens = PsiTreeUtil.collectElements(control) {
            it.node.elementType == CrystalTypes.REQUIRE
        }
        for (keyword in requireTokens) {
            var previous = PsiTreeUtil.prevLeaf(keyword)
            while (previous != null && previous.text.isBlank()) {
                previous = PsiTreeUtil.prevLeaf(previous)
            }
            if (previous?.node?.elementType == CrystalTypes.DOT) continue

            holder.registerProblem(
                keyword,
                "Can't execute Require in a macro",
                ProblemHighlightType.GENERIC_ERROR,
            )
        }
    }
}
