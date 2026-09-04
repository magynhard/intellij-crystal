package de.magynhard.crystal.psi.impl

import com.intellij.lang.ASTNode
import de.magynhard.crystal.psi.CrystalAssignment
import de.magynhard.crystal.psi.CrystalHeredocBodies
import de.magynhard.crystal.psi.CrystalPostfixModifier

abstract class CrystalConditionAssignmentMixin(node: ASTNode) : CrystalAssignmentMixin(node) {
    override fun getAssignment(): CrystalAssignment? = null

    override fun getHeredocBodies(): CrystalHeredocBodies? = null

    override fun getPostfixModifier(): CrystalPostfixModifier? = null
}
