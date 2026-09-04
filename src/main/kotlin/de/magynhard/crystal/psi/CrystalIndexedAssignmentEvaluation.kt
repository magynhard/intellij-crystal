package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement

internal data class CrystalIndexedAssignmentEvaluation(
    val targetElements: List<PsiElement>,
    val rhs: PsiElement?,
    val compound: Boolean,
    val shortCircuit: Boolean,
)

internal fun CrystalIndexedAssignment.evaluationComponents(): CrystalIndexedAssignmentEvaluation {
    val rhs = nestedAssignment ?: expression
    return CrystalIndexedAssignmentEvaluation(
        targetElements = children.takeWhile { it !== rhs }.filter { it !== postfixModifier },
        rhs = rhs,
        compound = node.findChildByType(CrystalTypes.ASSIGN) == null,
        shortCircuit = node.findChildByType(CrystalTypes.OR_OR_ASSIGN) != null ||
            node.findChildByType(CrystalTypes.AND_AND_ASSIGN) != null,
    )
}
