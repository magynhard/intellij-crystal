package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import de.magynhard.crystal.inspections.CrystalBracketCallResolver

/**
 * Polyvariant reference for `[]` calls (`Foo[]`, `foo[0]`), resolving the
 * bracket pair to the exact static `def self.[]` targets through
 * [CrystalBracketCallResolver]. Unknown, ambiguous, macro-backed (`Int64[]`),
 * and instance receivers resolve to nothing — the call stays suppressed
 * instead of guessing.
 */
class CrystalBracketCallReference(
    element: PsiElement,
    rangeStart: Int,
    rangeLength: Int
) : PsiPolyVariantReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {

    override fun resolve(): PsiElement? = multiResolve(false).singleOrNull()?.element

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val access = element as? CrystalBracketCallAccess ?: return ResolveResult.EMPTY_ARRAY
        return CrystalBracketCallResolver.resolveMethods(access)
            .map(::PsiElementResolveResult)
            .toTypedArray()
    }
}
