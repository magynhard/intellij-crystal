package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

/** Method-local type parameters bind throughout the signature and body. */
internal object CrystalForallBinding {
    fun findPathRootDeclaration(path: String, context: PsiElement): PsiElement? =
        if (path.startsWith("::")) null else findDeclaration(path.substringBefore("::"), context)

    fun findDeclaration(name: String, context: PsiElement): PsiElement? {
        if (name.contains("::")) return null
        var current: PsiElement? = context
        while (current != null && current !is PsiFile) {
            if (current is CrystalMethodDefinition) {
                var token = current.node.findChildByType(CrystalTypes.FORALL)?.treeNext
                while (token != null) {
                    when {
                        token.elementType == CrystalTypes.CONSTANT -> if (token.text == name) return token.psi
                        token.elementType == CrystalTypes.COMMA || token.psi is PsiWhiteSpace -> Unit
                        else -> return null
                    }
                    token = token.treeNext
                }
                return null
            }
            // A nested declaration must not capture an outer method's type parameter.
            if (current is CrystalMacroDefinition || CrystalPsiUtils.isTypeDefinition(current)) return null
            current = current.parent
        }
        return null
    }
}
