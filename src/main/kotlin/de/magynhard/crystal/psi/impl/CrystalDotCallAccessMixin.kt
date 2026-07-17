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
        // The method-name token is the child immediately after the DOT. It may be an
        // IDENTIFIER (`tanzen`), a CONSTANT, or a keyword used as a method name
        // (`next`, `yield`, … via `keyword_as_method`).
        val dot = node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.DOT) ?: return null
        var nameNode = dot.treeNext
        while (nameNode != null &&
            (nameNode.elementType == com.intellij.psi.TokenType.WHITE_SPACE ||
                nameNode.elementType == de.magynhard.crystal.psi.CrystalTypes.NEWLINE)) {
            nameNode = nameNode.treeNext
        }
        if (nameNode == null) return null
        val name = nameNode.text
        if (name.isBlank()) return null
        // Setter call (`obj.foo = bar` => `foo=`): a trailing ASSIGN token is part of the call.
        val setter = node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.ASSIGN) != null
        val methodName = if (setter) "$name=" else name
        val startOffset = nameNode.startOffset - node.startOffset
        return CrystalDotCallReference(this, methodName, startOffset, nameNode.textLength)
    }

    override fun getReferences(): Array<PsiReference> =
        reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}