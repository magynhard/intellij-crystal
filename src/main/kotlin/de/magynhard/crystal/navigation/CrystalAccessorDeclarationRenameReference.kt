package de.magynhard.crystal.navigation

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.psi.createLeafFromText

/**
 * The declaration rename reference for an accessor argument fetched from the
 * variable side of the coupling (`@active`-rename → `property? active
 * enabled`): the referenced element is an accessor-macro argument composite
 * (CrystalAccessorArgumentMixin), whose identifier leaf rewrites with the new
 * bare name while the sigil keeps the variable conventions side.
 */
class CrystalAccessorDeclarationRenameReference(
    element: PsiElement,
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode = element.node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.IDENTIFIER)
            ?: return element
        val bareName = newElementName.removePrefix("@@").removePrefix("@")
        val newLeaf = createLeafFromText(element.project, bareName, identNode.elementType) ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun resolve(): PsiElement? = null

    override fun getVariants(): Array<Any> = emptyArray()
}
