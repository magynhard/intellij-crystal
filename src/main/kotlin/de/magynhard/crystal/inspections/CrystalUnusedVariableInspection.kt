package de.magynhard.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElementVisitor
import de.magynhard.crystal.CrystalFile
import de.magynhard.crystal.analysis.CrystalLocalUsageAnalyzer
import de.magynhard.crystal.psi.*

/**
 * Inspection that reports local variables that are assigned but never read.
 * Also reports individual assignments whose value is overwritten before being read.
 *
 * Conventions:
 * - Variables starting with '_' are intentionally unused (no warning)
 * - Method parameters are NOT checked
 * - Instance vars (@x), class vars (@@x), globals ($x) are NOT checked
 * - Compound assignments (+=, ||=, etc.) count as both read and write (no warning)
 */
class CrystalUnusedVariableInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val injectionManager = InjectedLanguageManager.getInstance(holder.project)
        if (injectionManager.isInjectedFragment(holder.file) &&
            injectionManager.shouldInspectionsBeLenient(holder.file)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                when (element) {
                    is CrystalMethodDefinition -> analyzeScope(element, holder)
                    is CrystalClassDefinition,
                    is CrystalModuleDefinition,
                    is CrystalStructDefinition,
                    is CrystalEnumDefinition -> analyzeScope(element, holder)
                    is CrystalFile -> analyzeScope(element, holder)
                }
            }
        }
    }

    private fun analyzeScope(scope: com.intellij.psi.PsiElement, holder: ProblemsHolder) {
        for (assignment in CrystalLocalUsageAnalyzer(scope).analyze()) {
            if (assignment.name.startsWith("_")) continue
            val message = if (assignment.hasOtherAssignments) {
                "Value assigned to '${assignment.name}' is never used"
            } else {
                "Variable '${assignment.name}' is never used"
            }
            holder.registerProblem(assignment.identifier, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL)
        }
    }
}
