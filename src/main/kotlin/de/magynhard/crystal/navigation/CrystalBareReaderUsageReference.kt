package de.magynhard.crystal.navigation

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import de.magynhard.crystal.psi.createLeafFromText

/**
 * Transient usage reference for a bare implicit-self reader call that joins
 * the accessor coupling (`in_loop` / `in_loop?` inside the declaring type's
 * method body). The hit is a single IDENTIFIER leaf whose text ends with the
 * optional `?` reader suffix (the lexer folds the suffix into the token),
 * so rewriting keeps the suffix and changes only the accessor name.
 */
class CrystalBareReaderUsageReference(
    element: PsiElement,
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun handleElementRename(newElementName: String): PsiElement {
        val bareName = newElementName.removePrefix("@@").removePrefix("@")
        val suffix = if (element.text.endsWith("?")) "?" else ""
        val newLeaf = createLeafFromText(element.project, bareName + suffix, element.node.elementType) ?: return element
        val parent = element.node.treeParent ?: return element
        parent.replaceChild(element.node, newLeaf)
        return element
    }

    override fun getRangeInElement(): TextRange = TextRange(0, element.textLength)

    override fun resolve(): PsiElement? = null

    override fun getVariants(): Array<Any> = emptyArray()
}
