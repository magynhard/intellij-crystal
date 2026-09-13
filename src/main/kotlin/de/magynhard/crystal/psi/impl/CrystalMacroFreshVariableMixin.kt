package de.magynhard.crystal.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Mixin for macro fresh variables (`%value`, `%var{key.id}`).
 *
 * Fresh variables are macro-internal bindings resolved by Crystal's macro
 * expansion; the braces key is expansion data. The name is the MACRO_FRESH_VAR
 * token itself (`%value`), exactly as written before expansion, so navigation
 * and highlighting target the real token while references inside the braces
 * stay ordinary child expressions.
 *
 * No [com.intellij.psi.PsiReference]: the name only exists after macro
 * expansion (compiler Burn: MacroVar), so a name-index lookup would resolve
 * wrongly like it would for other macro-generated names.
 */
abstract class CrystalMacroFreshVariableMixin(node: ASTNode) : ASTWrapperPsiElement(node), PsiNameIdentifierOwner {

    override fun getNameIdentifier(): PsiElement? = node.findChildByType(CrystalTypes.MACRO_FRESH_VAR)?.psi

    override fun getName(): String? = nameIdentifier?.text

    override fun setName(name: String): PsiElement {
        val ident = nameIdentifier ?: return this
        val bareName = name.removePrefix("%")
        if (bareName.isBlank()) return this
        val newNode = de.magynhard.crystal.psi.createLeafFromText(project, "%$bareName", CrystalTypes.MACRO_FRESH_VAR)
            ?: return this
        ident.node.treeParent.replaceChild(ident.node, newNode)
        return this
    }
}
