package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import de.magynhard.crystal.psi.CrystalInClause
import de.magynhard.crystal.psi.CrystalPsiUtils

/**
 * Inspection that reports bare identifiers in `case ... in` patterns.
 *
 * The compiler only accepts constants, generic types, bool/nil literals,
 * and question methods in `in` position; a bare identifier is always
 * rejected, so it is flagged exactly where it stands instead of silently
 * participating in name resolution.
 */
class CrystalInvalidInPatternInspection : LocalInspectionTool() {

    // Wires the inspectionDescriptions/<shortName>.html resource into the
    // platform's description loading; without it the Inspect Code results
    // view crashes when a result node is selected.
    override fun getDescriptionFileName(): String = "$shortName.html"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!CrystalInspectionScope.isProjectSource(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is CrystalInClause) {
                    checkInClause(element, holder)
                }
            }
        }
    }

    private fun checkInClause(clause: CrystalInClause, holder: ProblemsHolder) {
        for (child in clause.expressionList.children) {
            if (CrystalPsiUtils.isBareIdentifier(child)) {
                holder.registerProblem(
                    child,
                    "Invalid 'in' pattern: expected a constant, generic type, bool/nil literal, or question method",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }
}
