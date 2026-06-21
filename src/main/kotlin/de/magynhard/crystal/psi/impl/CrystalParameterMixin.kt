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
        // Ensure the name has the correct prefix for the token type.
        // INSTANCE_VAR requires '@', CLASS_VAR requires '@@'.
        val fixedName = when (ident.node.elementType) {
            CrystalTypes.INSTANCE_VAR -> if (!name.startsWith("@")) "@$name" else name
            CrystalTypes.CLASS_VAR -> if (!name.startsWith("@@")) "@@$name" else name
            else -> name
        }
        val factory = com.intellij.psi.PsiFileFactory.getInstance(project)
        val dummyFile = factory.createFileFromText("dummy.cr", de.magynhard.crystal.CrystalLanguage, fixedName)
        val newNode = dummyFile.node.firstChildNode ?: return this
        ident.node.treeParent.replaceChild(ident.node, newNode)
        return this
    }
}
