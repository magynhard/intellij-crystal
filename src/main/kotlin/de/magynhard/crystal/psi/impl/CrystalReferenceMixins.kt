package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiReference
import de.magynhard.crystal.psi.*

/**
 * Mixin classes for PSI elements that need Go to Definition support.
 * These override getReference() to provide CrystalReference instances
 * that resolve identifiers to their definitions via StubIndex.
 */

private fun createCrystalReference(element: ASTWrapperPsiElement): CrystalReference? {
    // Skip if part of a definition name
    val parent = element.parent
    if (parent is CrystalNamedElement) return null
    val grandParent = parent?.parent
    if (grandParent is CrystalNamedElement) return null
    if (parent is CrystalTypeName) return null
    if (parent is CrystalMethodName) return null

    val identNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
        ?: element.node.findChildByType(CrystalTypes.CONSTANT)
        ?: return null

    val name = identNode.text
    if (name.isBlank()) return null

    val startOffset = identNode.startOffset - element.node.startOffset
    return CrystalReference(element, name, startOffset, identNode.textLength)
}

abstract class CrystalVariableReferenceMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}

abstract class CrystalMethodCallExpressionMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}

abstract class CrystalBareMethodCallExpressionMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}

abstract class CrystalTypePathMixin(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY
}
