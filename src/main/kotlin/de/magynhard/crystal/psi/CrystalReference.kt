package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*

/**
 * Reference from an identifier usage to its definition (class/module/struct/enum/method/macro).
 * Resolves via CrystalDefinitionFinder (StubIndex + FileTypeIndex fallback) for project-wide
 * lookup, with local-scope fallback for variables and parameters.
 */
class CrystalReference(
    element: PsiElement,
    private val name: String,
    rangeStart: Int,
    rangeLength: Int
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {

    override fun resolve(): PsiElement? {
        // 1. Project-wide definition lookup (StubIndex + FileTypeIndex fallback)
        val definitions = CrystalDefinitionFinder.findDefinitions(name, element.project)
        if (definitions.isNotEmpty()) return definitions.first()

        // 2. Local scope fallback: variables and parameters
        return resolveLocal()
    }

    private fun resolveLocal(): PsiElement? {
        var scope: PsiElement? = element.parent
        while (scope != null) {
            // Walk siblings before the reference looking for assignments like "name = ..."
            var sibling = scope.prevSibling
            while (sibling != null) {
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
