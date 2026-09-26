package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import de.magynhard.crystal.psi.CrystalBracketCallAccess
import de.magynhard.crystal.psi.CrystalBracketCallReference
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for `bracket_call_access` PSI elements (`Foo[]`, `foo[0]`).
 *
 * Provides a [CrystalBracketCallReference] via [getReference], enabling
 * identifier highlighting, hover documentation, and Go to Definition (Ctrl+B)
 * for `[]` calls — the same PsiReference mechanism that
 * [CrystalDotCallAccessMixin] uses for DOT-calls. The brackets (+ optional
 * args) are children of this composite; the receiver is the prevSibling in
 * the flattened postfix sequence.
 */
abstract class CrystalBracketCallAccessMixin(node: ASTNode) :
    ASTWrapperPsiElement(node), CrystalBracketCallAccess {

    override fun getReference(): PsiReference? {
        val openNode = node.findChildByType(CrystalTypes.LBRACKET) ?: return null
        val closeNode = node.findChildByType(CrystalTypes.RBRACKET) ?: return null
        val startOffset = openNode.startOffset - node.startOffset
        val length = closeNode.startOffset + closeNode.textLength - openNode.startOffset
        if (length <= 0) return null
        return CrystalBracketCallReference(this, startOffset, length)
    }

    override fun getReferences(): Array<PsiReference> =
        reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}
