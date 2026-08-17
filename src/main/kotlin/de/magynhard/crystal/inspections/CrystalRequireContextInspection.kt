package de.magynhard.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalRequireSemantics
import de.magynhard.crystal.psi.CrystalMacroControl
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.psi.CrystalTypes

/** Reports Crystal compiler errors for `require` outside file scope. */
class CrystalRequireContextInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalRequireStatement -> {
                        val message = CrystalRequireSemantics.errorMessage(element) ?: return
                        val keyword = element.node.findChildByType(CrystalTypes.REQUIRE)?.psi ?: element
                        holder.registerProblem(keyword, message, ProblemHighlightType.GENERIC_ERROR)
                    }
                    is CrystalMacroControl -> checkMacroControl(element, holder)
                }
            }
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
