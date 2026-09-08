package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import de.magynhard.crystal.navigation.CrystalAccessorCoupling
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalBareMethodCallExpression
import de.magynhard.crystal.psi.createLeafFromText
import de.magynhard.crystal.psi.CrystalTypes
import com.intellij.psi.util.PsiTreeUtil

/**
 * Accessor-macro name arguments are rename declarations. `property foo, bar` in
 * a class body declares the implicit reader/setter chain for BOTH parameters —
 * the macro-generated code has no PSI of its own, so the argument is the
 * declaration and must implement PsiNameIdentifierOwner: without it, renaming
 * `foo` (forward direction) is a blind token rewrite and Kotlin's reverse
 * direction (renaming `@foo`, pulling the accessor along) has no element to
 * attach.
 *
 * Only arguments of accessor macro calls expose a name; every other argument
 * returns null for both accessors, so ordinary call arguments behave exactly
 * as before.
 */
abstract class CrystalAccessorArgumentMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    private fun accessorNameIdentifier(): PsiElement? {
        var current: PsiElement? = this.parent
        while (current != null && current !is CrystalMethodCallExpression &&
            current !is CrystalBareMethodCallExpression && current !is PsiFile
        ) {
            current = current.parent
        }
        if (current !is CrystalMethodCallExpression && current !is CrystalBareMethodCallExpression) return null
        if (!CrystalAccessorCoupling.isAccessorMacroCall(current)) return null
        return CrystalAccessorCoupling.accessorNameIdentifier(this)?.takeIf { ident ->
            PsiTreeUtil.isAncestor(this@CrystalAccessorArgumentMixin, ident, false)
        }
    }

    override fun getNameIdentifier(): PsiElement? = accessorNameIdentifier()

    override fun getName(): String? = getNameIdentifier()?.text

    override fun setName(newName: String): PsiElement {
        // Plain bare arguments bind the name inside a variable_reference
        // composite (`property foo, bar, baz`), typed arguments carry the
        // identifier directly — the coupling helper resolves the leaf for both
        // shapes, so the sigil-free rename reaches the actual name leaf.
        val named = CrystalAccessorCoupling.accessorNameIdentifier(this) ?: node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
            ?: return this
        val identNode = named.node
        val newLeaf = createLeafFromText(project, newName, CrystalTypes.IDENTIFIER) ?: return this
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return this
    }

    override fun getTextOffset(): Int = getNameIdentifier()?.textOffset ?: node.startOffset
}
