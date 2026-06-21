package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for CrystalParameter PSI elements (e.g. `loud : Bool` in `def tanzen(loud : Bool)`).
 *
 * Implements PsiNameIdentifierOwner so that CrystalReference.resolve() can return
 * the CrystalParameter composite, enabling IntelliJ's TargetElementUtil to resolve
 * PSI_ELEMENT to a PsiNameIdentifierOwner for rename and other refactoring operations.
 */
abstract class CrystalParameterMixin(node: ASTNode) : ASTWrapperPsiElement(node), CrystalParameter, PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? {
        // Walk children to find the IDENTIFIER token (internal parameter name).
        // Crystal convention: `external internal : Type` — the LAST IDENTIFIER is the name.
        var lastIdent: PsiElement? = null
        var child = node.firstChildNode
        while (child != null) {
            if (child.elementType == CrystalTypes.IDENTIFIER) {
                lastIdent = child.psi
            }
            child = child.treeNext
        }
        return lastIdent
    }

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        val ident = nameIdentifier ?: return this
        val factory = com.intellij.psi.PsiFileFactory.getInstance(project)
        val dummyFile = factory.createFileFromText("dummy.cr", de.magynhard.crystal.CrystalLanguage, name)
        val newNode = dummyFile.node.firstChildNode ?: return this
        ident.node.treeParent.replaceChild(ident.node, newNode)
        return this
    }
}
