package de.magynhard.crystal.navigation

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReferenceBase
import de.magynhard.crystal.psi.createLeafFromText

/**
 * A rename usage that is a plain setter member assignment target:
 * `obj.foo = value` binds the member-assignment postfix inline (there is no
 * call PSI), so a transient reference on the method-name leaf makes the site
 * rename together with the accessor declaration. Only the name leaf is
 * replaced — the assignment operator and the right-hand side stay untouched.
 *
 * The accessors' declaration argument is the rename schooling: the
 * declaration-flagged ALWAYS references the argument PSI and not a synthesized
 * method.
 */
class CrystalMemberAssignUsageReference(
    element: PsiElement,
) : PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun handleElementRename(newElementName: String): PsiElement {
        val newLeaf = createLeafFromText(
            element.project,
            newElementName,
            element.node.elementType,
        ) ?: return element
        element.node.treeParent.replaceChild(element.node, newLeaf)
        return element
    }

    override fun resolve(): PsiElement? = null

    override fun getVariants(): Array<Any> = emptyArray()
}
