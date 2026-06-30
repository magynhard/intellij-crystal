package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalDotCallReference

/**
 * Mixin for `dot_call_access` PSI elements (e.g. `.tanzen`, `.new(arg)`, `.method arg`).
 *
 * Provides a [CrystalDotCallReference] via [getReference], enabling identifier
 * highlighting, hover documentation, and Go to Definition (Ctrl+B) for DOT-call
 * method names — the same PsiReference mechanism that [CrystalVariableReferenceMixin]
 * uses for top-level calls like `sahne`.
 *
 * The DOT and method-name token are children of this composite. The receiver
 * expression (e.g. `Apfel` in `Apfel.tanzen`) is the prevSibling in the flattened
 * `postfix_expression` sequence; the reference walks prevSibling to find it.
 */
abstract class CrystalDotCallAccessMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), CrystalDotCallAccess {

    override fun getReference(): PsiReference? {
        val identNode = node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.IDENTIFIER)
            ?: node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.CONSTANT)
            ?: return null
        val name = identNode.text
        if (name.isBlank()) return null
        val startOffset = identNode.startOffset - node.startOffset
        return CrystalDotCallReference(this, name, startOffset, identNode.textLength)
    }

    override fun getReferences(): Array<PsiReference> =
        reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}