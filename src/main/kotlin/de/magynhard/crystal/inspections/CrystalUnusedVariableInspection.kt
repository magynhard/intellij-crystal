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
        val assignments = mutableListOf<AssignmentInfo>()
        val references = mutableListOf<ReferenceInfo>()

        collectElements(scope, assignments, references, skipNestedMethods)

        for ((index, assignment) in assignments.withIndex()) {
            if (assignment.name.startsWith("_")) continue
            if (assignment.isCompound) continue

            val assignOffset = assignment.offset
            val nextAssign = assignments
                .filter { it.name == assignment.name && it.offset > assignOffset && !it.isCompound }
                .minByOrNull { it.offset }

            // Use the end offset of the next assignment as upper bound, so that
            // references on its RHS (e.g. `abc = abc.upcase`) are counted as reads.
            val nextAssignEndOffset = nextAssign?.identifierElement?.parent?.textRange?.endOffset ?: Int.MAX_VALUE

            val hasRead = references.any { ref ->
                ref.name == assignment.name && ref.offset > assignOffset && ref.offset < nextAssignEndOffset
            }

            val hasLaterRead = if (nextAssign == null) {
                references.any { ref ->
                    ref.name == assignment.name && ref.offset > assignOffset
                }
            } else {
                hasRead
            }

            if (!hasLaterRead) {
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
                    collectElements(child, assignments, references, skipNestedMethods)
                }
                is CrystalVariableReference -> {
                    val name = extractVariableReferenceName(child)
                    if (name != null) {
                        references.add(ReferenceInfo(name, child.textOffset))
                    }
                }
                is CrystalMethodCallExpression -> {
                    val methodNameNode = child.node.findChildByType(CrystalTypes.IDENTIFIER)
                    if (methodNameNode != null) {
                        references.add(ReferenceInfo(methodNameNode.text, methodNameNode.psi.textOffset))
                    }
                    collectElements(child, assignments, references, skipNestedMethods)
                }
                is CrystalStringExpression -> {
                    val astChildren = child.node.getChildren(null)
                    for (astChild in astChildren) {
                        val psi = astChild.psi
                        collectElements(psi, assignments, references, skipNestedMethods)
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
