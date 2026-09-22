package de.magynhard.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import de.magynhard.crystal.psi.CrystalFunDefinition
import de.magynhard.crystal.psi.CrystalMacroDefinition
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.CrystalParameterList
import de.magynhard.crystal.psi.CrystalTopLevelFun
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Reports a required positional parameter declared after an optional positional
 * parameter (`def foo(a = 1, b)`), which Crystal rejects with "parameter must
 * have a default value". Named-only parameters after a bare `*` or a splat may
 * still be required (`def foo(*a, b)` and `def foo(*, b)` are valid).
 */
class CrystalParameterOrderInspection : LocalInspectionTool() {

    override fun getDescriptionFileName(): String = "$shortName.html"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val parameterList = when (element) {
                    is CrystalMethodDefinition -> element.parameterList
                    is CrystalMacroDefinition -> element.parameterList
                    is CrystalTopLevelFun -> element.parameterList
                    is CrystalFunDefinition -> element.parameterList
                    else -> null
                } ?: return
                checkOrdering(parameterList, holder)
            }
        }
    }

    private fun checkOrdering(parameterList: CrystalParameterList, holder: ProblemsHolder) {
        var sawOptionalPositional = false
        var namedOnly = false
        for (child in parameterList.node.getChildren(null)) {
            when (child.elementType) {
                CrystalTypes.STAR, CrystalTypes.DOUBLE_STAR -> namedOnly = true
                else -> {
                    val param = child.psi as? CrystalParameter ?: continue
                    // Macro-spliced fragments expand to an unknown parameter
                    // shape; stop reporting after them to avoid false positives.
                    if (param.node.findChildByType(CrystalTypes.MACRO_INTERPOLATION) != null) {
                        namedOnly = true
                        continue
                    }
                    val isSplat = param.node.findChildByType(CrystalTypes.STAR) != null ||
                        param.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null
                    val isBlock = param.node.findChildByType(CrystalTypes.AMPERSAND) != null
                    if (isSplat || isBlock) {
                        namedOnly = true
                        continue
                    }
                    if (namedOnly) continue
                    if (param.expression != null) {
                        sawOptionalPositional = true
                    } else if (sawOptionalPositional) {
                        holder.registerProblem(
                            param,
                            "Required parameter must have a default value",
                            ProblemHighlightType.GENERIC_ERROR
                        )
                    }
                }
            }
        }
    }
}
