package de.magynhard.crystal.navigation

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import de.magynhard.crystal.lexer.CrystalLexerAdapter
import de.magynhard.crystal.psi.*

class CrystalFindUsagesProvider : FindUsagesProvider {

    override fun getWordsScanner() = DefaultWordsScanner(
        CrystalLexerAdapter(),
        TokenSet.create(CrystalTypes.IDENTIFIER, CrystalTypes.CONSTANT, CrystalTypes.INSTANCE_VAR, CrystalTypes.CLASS_VAR),
        TokenSet.create(CrystalTypes.LINE_COMMENT),
        TokenSet.create(CrystalTypes.STRING_LITERAL)
    )

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        if (psiElement is CrystalNamedElement) return true
        val tokenType = psiElement.node?.elementType
        return tokenType == CrystalTypes.IDENTIFIER || tokenType == CrystalTypes.CONSTANT
    }

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String {
        return when (element) {
            is CrystalInstanceVarAccess -> "instance variable"
            is CrystalClassVarAccess -> "class variable"
            is CrystalClassDefinition -> "class"
            is CrystalModuleDefinition -> "module"
            is CrystalStructDefinition -> "struct"
            is CrystalEnumDefinition -> "enum"
            is CrystalMethodDefinition -> "method"
            is CrystalMacroDefinition -> "macro"
            is CrystalConstantAssignment -> "constant"
            else -> when (element.node?.elementType) {
                CrystalTypes.CONSTANT -> "type"
                CrystalTypes.IDENTIFIER -> "symbol"
                else -> "element"
            }
        }
    }

    override fun getDescriptiveName(element: PsiElement): String = element.text

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = element.text
}
