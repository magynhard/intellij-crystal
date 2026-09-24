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

    // Wires the inspectionDescriptions/<shortName>.html resource into the
    // platform's description loading; without it the Inspect Code results
    // view crashes when a result node is selected.
    override fun getDescriptionFileName(): String = "$shortName.html"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!CrystalInspectionScope.isProjectSource(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
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
