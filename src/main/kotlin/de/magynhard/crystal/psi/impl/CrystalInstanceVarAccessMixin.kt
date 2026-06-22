package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import de.magynhard.crystal.psi.CrystalInstanceVarAccess
import de.magynhard.crystal.psi.CrystalNamedElement
import de.magynhard.crystal.psi.CrystalInstanceVarReference
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for instance_var_access PSI elements (@name).
 * Implements PsiNamedElement so Find Usages works via the standard platform mechanism.
 * Every @name occurrence has a PsiReference that resolves to the first @name in the same class.
 */
abstract class CrystalInstanceVarAccessMixin(node: ASTNode) : ASTWrapperPsiElement(node), CrystalInstanceVarAccess {

    override fun getName(): String = text

    override fun setName(name: String): PsiElement {
        val identNode = node.findChildByType(CrystalTypes.INSTANCE_VAR) ?: return this
        val bareName = name.removePrefix("@").removePrefix("@")
        val fixedName = "@$bareName"
        val newNode = de.magynhard.crystal.psi.createLeafFromText(project, fixedName, CrystalTypes.INSTANCE_VAR) ?: return this
        identNode.treeParent.replaceChild(identNode, newNode)
        return this
    }

    override fun getNameIdentifier(): PsiElement? = node.findChildByType(CrystalTypes.INSTANCE_VAR)?.psi

    override fun getReference(): PsiReference? = CrystalInstanceVarReference(this)

    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY

    override fun getTextOffset(): Int = node.startOffset
}
