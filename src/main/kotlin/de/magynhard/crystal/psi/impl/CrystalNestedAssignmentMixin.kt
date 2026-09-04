package de.magynhard.crystal.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalAssignment
import de.magynhard.crystal.psi.CrystalHeredocBodies
import de.magynhard.crystal.psi.CrystalNestedAssignment
import de.magynhard.crystal.psi.CrystalPostfixModifier

abstract class CrystalNestedAssignmentMixin(node: ASTNode) : CrystalAssignmentMixin(node) {
    override fun getAssignment(): CrystalAssignment? =
        PsiTreeUtil.getChildOfType(this, CrystalNestedAssignment::class.java)

    override fun getHeredocBodies(): CrystalHeredocBodies? = null

    override fun getPostfixModifier(): CrystalPostfixModifier? = null
}
