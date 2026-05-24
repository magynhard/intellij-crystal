package de.magynhard.crystal.psi

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

/**
 * Provides references for IDENTIFIER and CONSTANT tokens in Crystal files,
 * enabling Go to Definition (Ctrl+Click / Ctrl+B).
 */
class CrystalReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    // Only provide references for leaf IDENTIFIER and CONSTANT tokens
                    val elementType = element.node.elementType
                    if (elementType != CrystalTypes.IDENTIFIER && elementType != CrystalTypes.CONSTANT) {
                        return PsiReference.EMPTY_ARRAY
                    }

                    // Skip if this element IS the definition name (inside a type_name or method_name)
                    val parent = element.parent ?: return PsiReference.EMPTY_ARRAY
                    if (parent is CrystalNamedElement) return PsiReference.EMPTY_ARRAY
                    val grandParent = parent.parent
                    if (grandParent is CrystalNamedElement) return PsiReference.EMPTY_ARRAY

                    val name = element.text
                    if (name.isBlank()) return PsiReference.EMPTY_ARRAY

                    return arrayOf(CrystalReference(element, name))
                }
            }
        )
    }
}
