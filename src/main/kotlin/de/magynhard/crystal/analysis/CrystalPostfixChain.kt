package de.magynhard.crystal.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import de.magynhard.crystal.psi.*

internal object CrystalPostfixChain {
    fun components(access: CrystalDotCallAccess, beforeOffset: Int? = null): List<PsiElement?> {
        val result = mutableListOf<PsiElement?>(access)
        val argument = access.bareArgumentList?.bareArgumentList?.singleOrNull() ?: return result
        val children = significantChildren(argument)
        val implicit = children.firstOrNull() as? CrystalImplicitObjectCall ?: return result
        if (!isAdjacent(access, implicit)) return result
        append(children, beforeOffset, result)
        return result
    }

    fun dotOffset(call: PsiElement): Int =
        call.node.findChildByType(CrystalTypes.DOT)?.startOffset ?: Int.MAX_VALUE

    private fun append(elements: List<PsiElement>, beforeOffset: Int?, result: MutableList<PsiElement?>) {
        for (element in elements) {
            if (element is CrystalImplicitObjectCall) {
                if (beforeOffset == null || dotOffset(element) < beforeOffset) result.add(element)
                continue
            }
            if (element is CrystalDotCallAccess) {
                if (beforeOffset != null && dotOffset(element) >= beforeOffset) continue
                result.addAll(components(element, beforeOffset))
                continue
            }
            if (beforeOffset == null || element.textRange.startOffset < beforeOffset) result.add(null)
        }
    }

    private fun isAdjacent(access: CrystalDotCallAccess, implicit: CrystalImplicitObjectCall): Boolean {
        val dot = implicit.node.findChildByType(CrystalTypes.DOT)?.psi ?: return false
        val gap = access.containingFile.text.substring(access.textRange.startOffset, dot.textRange.startOffset)
        return gap.lastOrNull()?.isWhitespace() != true
    }

    private fun significantChildren(element: PsiElement): List<PsiElement> =
        element.node.getChildren(null).map { it.psi }.filterNot {
            it is PsiWhiteSpace || it.node.elementType == CrystalTypes.NEWLINE
        }
}
