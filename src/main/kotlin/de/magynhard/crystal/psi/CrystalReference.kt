package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Reference from an identifier usage to its definition (class/module/struct/enum/method/macro).
 * Resolves via StubIndex for project-wide lookup, with local-scope fallback.
 */
class CrystalReference(element: PsiElement, private val name: String) :
    PsiReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)

        // 1. Try type index (classes, modules, structs, enums are all indexed under CrystalClassIndex)
        val types = StubIndex.getElements(
            CrystalClassIndex.KEY, name, project, scope,
            CrystalClassDefinition::class.java
        )
        if (types.isNotEmpty()) return types.first()

        // 2. Try method index
        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, name, project, scope,
            CrystalMethodDefinition::class.java
        )
        if (methods.isNotEmpty()) return methods.first()

        // 3. Local scope fallback: walk up PSI tree to find local variable assignments or parameters
        return resolveLocal()
    }

    private fun resolveLocal(): PsiElement? {
        var scope: PsiElement? = element.parent
        while (scope != null) {
            // Walk siblings before the reference looking for assignments like "name = ..."
            var sibling = scope.prevSibling
            while (sibling != null) {
                // Check assignment: IDENTIFIER ASSIGN ...
                val firstChild = sibling.firstChild
                if (firstChild != null && firstChild.text == name &&
                    firstChild.node.elementType == CrystalTypes.IDENTIFIER) {
                    return firstChild
                }
                sibling = sibling.prevSibling
            }
            // Check parameters if we're inside a method
            if (scope is CrystalMethodDefinition || scope is CrystalMacroDefinition) {
                val paramList = when (scope) {
                    is CrystalMethodDefinition -> scope.parameterList
                    is CrystalMacroDefinition -> scope.parameterList
                    else -> null
                }
                paramList?.parameterList?.forEach { param ->
                    val paramIdent = param.node.findChildByType(CrystalTypes.IDENTIFIER)
                    if (paramIdent?.text == name) return paramIdent.psi
                }
                break // Don't look beyond method boundaries for locals
            }
            scope = scope.parent
        }
        return null
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
