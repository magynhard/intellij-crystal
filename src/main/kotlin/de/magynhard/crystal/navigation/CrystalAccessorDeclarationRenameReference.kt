package de.magynhard.crystal.navigation

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
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
) : PsiReferenceBase<PsiElement>(element, identifierRange(element), true) {
    companion object {
        /**
         * The highlight range must cover ONLY the accessor name — the whole
         * argument composite of a typed declaration (`getter? in_loop : Bool`)
         * would also mark the ` : Bool` annotation. Falls back to the full
         * composite only when the identifier leaf cannot be resolved.
         */
        private fun identifierRange(element: PsiElement): TextRange {
            val ident = CrystalAccessorCoupling.accessorNameIdentifier(element)?.node
                ?: return TextRange(0, element.textLength)
            return TextRange(ident.startOffset - element.node.startOffset, ident.textLength)
        }
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        // The identifier leaf lives inside the `variable_reference` wrapper
        // for untyped arguments — resolve it through the coupling instead of
        // a direct child lookup, which only handles the typed shape.
        val identNode = CrystalAccessorCoupling.accessorNameIdentifier(element)?.node
            ?: return element
        val bareName = newElementName.removePrefix("@@").removePrefix("@")
        val newLeaf = createLeafFromText(element.project, bareName, identNode.elementType) ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun resolve(): PsiElement? = null

    override fun getVariants(): Array<Any> = emptyArray()
}
