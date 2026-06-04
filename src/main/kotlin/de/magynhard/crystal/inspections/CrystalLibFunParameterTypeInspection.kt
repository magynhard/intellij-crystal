package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import de.magynhard.crystal.psi.CrystalFunDefinition
import de.magynhard.crystal.psi.CrystalParameter

/**
 * Inspection that reports parameters without type annotations in lib fun definitions.
 * In Crystal, all parameters in lib fun declarations must have explicit types.
 */
class CrystalLibFunParameterTypeInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is CrystalFunDefinition) {
                    checkFunDefinition(element, holder)
                }
            }
        }
    }

    private fun checkFunDefinition(funDef: CrystalFunDefinition, holder: ProblemsHolder) {
        val paramList = funDef.parameterList ?: return
        for (child in paramList.children) {
            if (child is CrystalParameter) {
                if (child.typeReference == null) {
                    holder.registerProblem(
                        child,
                        "Parameter in lib fun must have a type annotation",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }
        }
    }
}
