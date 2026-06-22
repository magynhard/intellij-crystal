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
        // Walk children to find the name token (internal parameter name).
        // Crystal convention: `external internal : Type` — the LAST IDENTIFIER is the name.
        // For instance var parameters: `def initialize(@x : Int32)` — the instance_var_access
        // child wraps the INSTANCE_VAR token, so check for it as a composite.
        var lastIdent: PsiElement? = null
        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                CrystalTypes.IDENTIFIER -> {
                    lastIdent = child.psi
                }
                CrystalTypes.INSTANCE_VAR_ACCESS -> {
                    // instance_var_access wraps INSTANCE_VAR — find the leaf inside
                    val instanceVar = child.findChildByType(CrystalTypes.INSTANCE_VAR)
                    if (instanceVar != null) lastIdent = instanceVar.psi
                }
            }
            child = child.treeNext
        }
        return lastIdent
    }

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        val ident = nameIdentifier ?: return this
        val tokenType = ident.node.elementType
        // Strip any @/@@ prefix the user may have typed, then re-apply from original token type.
        // The variable type (instance var, class var, local var) never changes during rename.
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
