package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import de.magynhard.crystal.psi.CrystalAssignment
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for CrystalAssignment PSI elements (e.g. `sas = Senf.new`, `x = 1`, `x += 1`).
 *
 * Implements PsiNameIdentifierOwner so that CrystalReference.resolve() can return
 * the CrystalAssignment composite, enabling IntelliJ's TargetElementUtil to resolve
 * PSI_ELEMENT to a PsiNameIdentifierOwner for rename and other refactoring operations.
 *
 * The assignment variable (left-hand side) is the IDENTIFIER, INSTANCE_VAR, or CLASS_VAR
 * at the beginning of the assignment.
 */
abstract class CrystalAssignmentMixin(node: ASTNode) : ASTWrapperPsiElement(node), CrystalAssignment, PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? {
        // The assignment variable is the first child of type IDENTIFIER, INSTANCE_VAR, or CLASS_VAR.
        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                CrystalTypes.IDENTIFIER, CrystalTypes.INSTANCE_VAR, CrystalTypes.CLASS_VAR -> {
                    return child.psi
                }
            }
            child = child.treeNext
        }
        return null
    }

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        val ident = nameIdentifier ?: return this
        val tokenType = ident.node.elementType
        val bareName = name.removePrefix("@").removePrefix("@")
        val fixedName = when (tokenType) {
            CrystalTypes.INSTANCE_VAR -> "@$bareName"
            CrystalTypes.CLASS_VAR -> "@@$bareName"
            else -> bareName
        }
        val newNode = de.magynhard.crystal.psi.createLeafFromText(project, fixedName, tokenType) ?: return this
        ident.node.treeParent.replaceChild(ident.node, newNode)
        return this
    }
}
