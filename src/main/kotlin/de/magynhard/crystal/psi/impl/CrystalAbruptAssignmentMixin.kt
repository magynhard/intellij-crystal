package de.magynhard.crystal.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalAbruptAssignment
import de.magynhard.crystal.psi.CrystalAssignment
import de.magynhard.crystal.psi.CrystalHeredocBodies
import de.magynhard.crystal.psi.CrystalPostfixModifier

abstract class CrystalAbruptAssignmentMixin(node: ASTNode) : CrystalAssignmentMixin(node) {
    override fun getAssignment(): CrystalAssignment? =
        PsiTreeUtil.getChildOfType(this, CrystalAbruptAssignment::class.java)

    override fun getHeredocBodies(): CrystalHeredocBodies? = null

    override fun getPostfixModifier(): CrystalPostfixModifier? = null
}
