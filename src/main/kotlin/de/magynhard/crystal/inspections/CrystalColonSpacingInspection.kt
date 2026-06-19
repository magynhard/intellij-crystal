package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import de.magynhard.crystal.psi.*

/**
 * Inspection that reports missing spaces before/after colon in parameter type
 * annotations and return type annotations.
 *
 * Crystal requires: `speed : String` (space before AND after colon).
 * Flags: `speed: String`, `speed :String`, `speed:String`
 *
 * Exception: colon after `=` (default value) is exempt, e.g. `= :name` is valid.
 */
class CrystalColonSpacingInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is CrystalMethodDefinition) {
                    checkMethodDefinition(element, holder)
                }
            }
        }
    }

    private fun checkMethodDefinition(method: CrystalMethodDefinition, holder: ProblemsHolder) {
        val paramList = method.parameterList ?: return
        for (param in paramList.parameterList) {
            checkParameterColonSpacing(param, holder)
        }
        checkReturnTypeColon(method, holder)
    }

    private fun checkParameterColonSpacing(param: CrystalParameter, holder: ProblemsHolder) {
        val children = param.node.getChildren(null)
        for (i in children.indices) {
            if (children[i].elementType != CrystalTypes.COLON) continue
            if (isAfterEquals(children, i)) continue
            checkColonSpacing(children, i, holder)
        }
    }

    private fun checkReturnTypeColon(method: CrystalMethodDefinition, holder: ProblemsHolder) {
        val children = method.node.getChildren(null)
        for (i in children.indices) {
            if (children[i].elementType != CrystalTypes.COLON) continue
            if (!isReturnTypeColon(children, i)) continue
            checkColonSpacing(children, i, holder)
        }
    }

    private fun isAfterEquals(children: Array<ASTNode>, colonIndex: Int): Boolean {
        for (j in colonIndex - 1 downTo 0) {
            val prev = children[j]
            if (prev is PsiWhiteSpace) continue
            return prev.elementType == CrystalTypes.ASSIGN
        }
        return false
    }

    private fun isReturnTypeColon(children: Array<ASTNode>, colonIndex: Int): Boolean {
        for (j in colonIndex - 1 downTo 0) {
            val prev = children[j]
            if (prev is PsiWhiteSpace) continue
            if (prev.elementType == CrystalTypes.RPAREN) return true
            if (prev.elementType == CrystalTypes.TYPE_REFERENCE) return false
            return false
        }
        return false
    }

    private fun checkColonSpacing(children: Array<ASTNode>, colonIndex: Int, holder: ProblemsHolder) {
        val colon = children[colonIndex]

        val hasSpaceBefore = colonIndex > 0 && children[colonIndex - 1] is PsiWhiteSpace
        val hasSpaceAfter = colonIndex < children.size - 1 && children[colonIndex + 1] is PsiWhiteSpace

        if (!hasSpaceBefore || !hasSpaceAfter) {
            val message = buildString {
                append("Space required around colon in type annotation")
                if (!hasSpaceBefore && !hasSpaceAfter) append(" (before and after)")
                else if (!hasSpaceBefore) append(" (before colon)")
                else append(" (after colon)")
            }
            holder.registerProblem(
                colon.psi,
                message,
                ProblemHighlightType.GENERIC_ERROR,
                ColonSpacingQuickFix(colon, !hasSpaceBefore, !hasSpaceAfter)
            )
        }
    }

    private class ColonSpacingQuickFix(
        private val colonNode: ASTNode,
        private val insertBefore: Boolean,
        private val insertAfter: Boolean
    ) : LocalQuickFix {

        override fun getName(): String = "Insert space(s) around colon"
        override fun getFamilyName(): String = "Crystal Colon Spacing"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val psiFile = colonNode.psi.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
            val colonOffset = colonNode.startOffset

            if (insertAfter) {
                document.insertString(colonOffset + 1, " ")
            }
            if (insertBefore) {
                document.insertString(colonOffset, " ")
            }
        }
    }
}
