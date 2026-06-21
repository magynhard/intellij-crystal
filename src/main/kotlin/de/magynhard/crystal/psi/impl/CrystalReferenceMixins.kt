package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
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

/**
 * Mixin for variable_reference PSI elements.
 *
 * Implements PsiNameIdentifierOwner so that CrystalReference.resolve() returns
 * a PsiNameIdentifierOwner composite (not just the IDENTIFIER leaf). This enables
 * IntelliJ's TargetElementUtil to resolve PSI_ELEMENT to a renameable element.
 * Without this, resolve() returns IDENTIFIER leaves which are NOT PsiNameIdentifierOwner,
 * causing MemberInplaceRenameHandler to fail its `element instanceof PsiNameIdentifierOwner`
 * check — and TokenInplaceRenameHandler steps aside because a custom renamePsiElementProcessor
 * is registered — leaving rename completely grayed out.
 *
 * getNameIdentifier() returns the IDENTIFIER or CONSTANT leaf child.
 */
abstract class CrystalVariableReferenceMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {
    override fun getReference(): PsiReference? = createCrystalReference(this)
    override fun getReferences(): Array<PsiReference> = reference?.let { arrayOf(it) } ?: PsiReference.EMPTY_ARRAY

    override fun getNameIdentifier(): PsiElement? {
        return node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
            ?: node.findChildByType(CrystalTypes.CONSTANT)?.psi
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
