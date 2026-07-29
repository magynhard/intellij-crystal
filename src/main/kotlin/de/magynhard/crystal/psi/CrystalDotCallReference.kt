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
        val newLeaf = createLeafFromText(element.project, newElementName, identNode.elementType)
            ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
