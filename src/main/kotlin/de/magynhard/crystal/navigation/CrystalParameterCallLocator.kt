package de.magynhard.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalBareArgumentList
import de.magynhard.crystal.psi.CrystalCallArgs
import de.magynhard.crystal.psi.CrystalTypes

internal object CrystalParameterCallLocator {

    internal fun findArgsHolder(file: PsiFile, offset: Int): PsiElement? {
        val quickCheckAnchor = CrystalBareCallParameterLocator.scanBackwardsForBareCall(file, offset)
        if (quickCheckAnchor is CrystalParameterInfoHandler.CrystalParameterInfoAnchor) {
            val nameToken = quickCheckAnchor.nameToken
            val prev = PsiTreeUtil.prevLeaf(nameToken)
            val prevNonWs = if (prev is PsiWhiteSpace || prev?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
                PsiTreeUtil.prevLeaf(prev)
            } else prev
            val prevType = prevNonWs?.node?.elementType
            if (prevType == CrystalTypes.DOT || prevType == null) return quickCheckAnchor
        }

        val elementAtOffset = file.findElementAt(offset)
        val primary = findArgsParent(elementAtOffset)
        if (primary != null) return primary

        if (offset > 0) {
            val elementBefore = file.findElementAt(offset - 1)
            val fallbackA = findArgsParent(elementBefore)
            if (fallbackA != null) return fallbackA
        }

        val brokenParenHolder = CrystalBareCallParameterLocator.findBrokenParenArgsHolder(
            file,
            offset,
            ::findArgsParent
        )
        if (brokenParenHolder != null) return brokenParenHolder

        val bareCallAnchor = CrystalBareCallParameterLocator.scanBackwardsForBareCall(file, offset)
        if (bareCallAnchor != null) return bareCallAnchor

        if (elementAtOffset != null && elementAtOffset.node?.elementType == CrystalTypes.RPAREN) {
            val parent = elementAtOffset.parent
            if (parent is CrystalCallArgs) return parent
            return parent
        }

        return null
    }

    private fun findArgsParent(element: PsiElement?): PsiElement? {
        if (element == null) return null
        val callArgs = PsiTreeUtil.getParentOfType(element, CrystalCallArgs::class.java, false)
        if (callArgs != null) return callArgs
        val bareArgs = PsiTreeUtil.getParentOfType(element, CrystalBareArgumentList::class.java, false)
        if (bareArgs != null) return bareArgs
        if (element is CrystalCallArgs) return element
        if (element is CrystalBareArgumentList) return element
        return null
    }
}
