package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import de.magynhard.crystal.inspections.CrystalDotCallTargetResolver
import de.magynhard.crystal.inspections.DotCallResolution

/** Resolves a DOT-call method name through neutral scoped receiver and hierarchy analysis. */
class CrystalDotCallReference(
    element: PsiElement,
    rangeStart: Int,
    rangeLength: Int
) : PsiPolyVariantReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {

    override fun resolve(): PsiElement? = multiResolve(false).singleOrNull()?.element

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val access = element as? CrystalDotCallAccess ?: return ResolveResult.EMPTY_ARRAY
        val targets: List<PsiElement> = when (val resolution = CrystalDotCallTargetResolver.resolve(access)) {
            is DotCallResolution.Methods -> resolution.methods
            is DotCallResolution.RecordFallback -> listOf(resolution.recordDefinition)
            // Accessor macros: the interface between the methodology is the
            // ARGUMENT (property foo declaration).
            is DotCallResolution.Accessor -> resolution.accessorArgs
            is DotCallResolution.ImplicitConstructor,
            DotCallResolution.Unresolved,
            DotCallResolution.Suppressed -> emptyList()
        }
        return targets.map(::PsiElementResolveResult).toTypedArray()
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
            ?: element.node.findChildByType(CrystalTypes.CONSTANT)
            ?: return element
        // Accessor-bound call sites keep their shape suffix: the passed renamer
        // name is the DECLARATION name (`property? on`), while the call site's
        // method leaf (`on?`, `on=` via member assignment, `on!`) derives its
        // suffix from the macro variant. Real def-renames pass the full name
        // and keep their own suffix in the declaration element name.
        val currentName = identNode.text
        val suffix = when {
            currentName.endsWith("?") && !newElementName.endsWith("?") -> "?"
            currentName.endsWith("!") && !newElementName.endsWith("!") -> "!"
            currentName.endsWith("=") && !newElementName.endsWith("=") -> "="
            else -> ""
        }
        val newLeaf = createLeafFromText(element.project, newElementName + suffix, identNode.elementType)
            ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
