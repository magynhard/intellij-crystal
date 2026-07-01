package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import de.magynhard.crystal.psi.CrystalNamespaceAccess
import de.magynhard.crystal.psi.CrystalNamespaceReference

/**
 * Mixin for `namespace_access` PSI elements (e.g. `::Unterklasse` in `Oberklasse::Unterklasse`).
 *
 * Provides a [CrystalNamespaceReference] via [getReference], enabling identifier
 * highlighting, hover documentation, and Go to Definition (Ctrl+B) for intermediate
 * namespace segments — the same PsiReference mechanism that [CrystalVariableReferenceMixin]
 * uses for the leading CONSTANT (e.g. `Oberklasse` in `Oberklasse::Unterklasse`).
 *
 * The DOUBLE_COLON and CONSTANT tokens are children of this composite. The preceding
 * part of the namespace path is the prevSibling in the flattened postfix_expression
 * sequence; the reference walks prevSibling to reconstruct the full path.
 */
abstract class CrystalNamespaceAccessMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), CrystalNamespaceAccess {

    override fun getReference(): PsiReference? {
        val constantNode = node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.CONSTANT)
            ?: return null
        val name = constantNode.text
        if (name.isBlank()) return null
        val startOffset = constantNode.startOffset - node.startOffset
        return CrystalNamespaceReference(this, name, startOffset, constantNode.textLength)
    }

    override fun getReferences(): Array<PsiReference> =
        reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}
