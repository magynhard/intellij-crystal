package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import de.magynhard.crystal.CrystalFile
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
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalMethodDefinition -> analyzeScope(element, holder, skipNestedMethods = false)
                    is CrystalFile -> analyzeScope(element, holder, skipNestedMethods = true)
                }
            }
        }
    }

    private fun analyzeScope(scope: PsiElement, holder: ProblemsHolder, skipNestedMethods: Boolean) {
        // Collect all assignments to local variables (IDENTIFIER targets only)
        val assignments = mutableListOf<AssignmentInfo>()
        val references = mutableListOf<ReferenceInfo>()

        collectElements(scope, assignments, references, skipNestedMethods)

        // For each assignment, check if its value is ever read
        for ((index, assignment) in assignments.withIndex()) {
            // Skip _-prefixed variables
            if (assignment.name.startsWith("_")) continue

            // Compound assignments (+=, ||=, etc.) are implicit reads — skip
            if (assignment.isCompound) continue

            val assignOffset = assignment.offset

            // Find the next non-compound assignment to the same variable (if any)
            val nextAssignOffset = assignments
                .filter { it.name == assignment.name && it.offset > assignOffset && !it.isCompound }
                .minByOrNull { it.offset }?.offset ?: Int.MAX_VALUE

            // Check if there's any read of this variable between this assignment and the next one
            // (or to the end of scope if no next assignment)
            val hasRead = references.any { ref ->
                ref.name == assignment.name && ref.offset > assignOffset && ref.offset < nextAssignOffset
            }

            // Also check if there's any read AFTER the last assignment (for the last assignment in chain)
            val hasLaterRead = if (nextAssignOffset == Int.MAX_VALUE) {
                references.any { ref ->
                    ref.name == assignment.name && ref.offset > assignOffset
                }
            } else {
                hasRead
            }

            if (!hasLaterRead) {
                // Determine message based on whether there are other assignments to same variable
                val otherAssignments = assignments.count { it.name == assignment.name }
                val message = if (otherAssignments > 1) {
                    "Value assigned to '${assignment.name}' is never used"
                } else {
                    "Variable '${assignment.name}' is never used"
                }
                holder.registerProblem(
                    assignment.identifierElement,
                    message,
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL
                )
            }
        }
    }

    private fun extractLocalAssignment(assignment: CrystalAssignment): AssignmentInfo? {
        // Skip instance/class var assignments
        if (assignment.instanceVarAccess != null) return null
        if (assignment.classVarAccess != null) return null

        // Find the IDENTIFIER token (local variable name)
        val identifierNode = assignment.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return null
        val name = identifierNode.text

        // Skip global vars (shouldn't happen since grammar handles them, but safety)
        if (name.startsWith("$")) return null

        // Check if compound assignment
        val assignOp = assignment.node.findChildByType(CrystalTypes.ASSIGN)
        val isCompound = assignOp == null // If no plain ASSIGN, it's a compound op

        val identifierPsi = identifierNode.psi

        return AssignmentInfo(
            name = name,
            offset = identifierPsi.textOffset,
            identifierElement = identifierPsi,
            isCompound = isCompound
        )
    }

    private fun extractVariableReferenceName(ref: CrystalVariableReference): String? {
        val node = ref.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return null
        return node.text
    }

    private fun collectElements(
        element: PsiElement,
        assignments: MutableList<AssignmentInfo>,
        references: MutableList<ReferenceInfo>,
        skipNestedMethods: Boolean
    ) {
        var child = element.firstChild
        while (child != null) {
            // When analyzing file-level, don't descend into method bodies
            if (skipNestedMethods && child is CrystalMethodDefinition) {
                child = child.nextSibling
                continue
            }
            when (child) {
                is CrystalAssignment -> {
                    val info = extractLocalAssignment(child)
                    if (info != null) {
                        assignments.add(info)
                        if (info.isCompound) {
                            references.add(ReferenceInfo(info.name, info.offset))
                        }
                    }
                    // Also collect references inside the assignment's expression
                    collectElements(child, assignments, references, skipNestedMethods)
                }
                is CrystalVariableReference -> {
                    val name = extractVariableReferenceName(child)
                    if (name != null) {
                        references.add(ReferenceInfo(name, child.textOffset))
                    }
                }
                else -> collectElements(child, assignments, references, skipNestedMethods)
            }
            child = child.nextSibling
        }
    }

    data class AssignmentInfo(
        val name: String,
        val offset: Int,
        val identifierElement: PsiElement,
        val isCompound: Boolean
    )

    data class ReferenceInfo(
        val name: String,
        val offset: Int
    )
}
