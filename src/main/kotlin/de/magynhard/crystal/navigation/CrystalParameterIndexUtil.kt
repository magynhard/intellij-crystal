package de.magynhard.crystal.navigation

import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalBareArgumentList
import de.magynhard.crystal.psi.CrystalCallArgs
import de.magynhard.crystal.psi.CrystalTypes

internal object CrystalParameterIndexUtil {

    internal fun computeCurrentParameterIndex(argsHolder: PsiElement, offset: Int): Int {
        if (argsHolder is CrystalParameterInfoHandler.CrystalParameterInfoAnchor) {
            val fileText = argsHolder.containingFile.text
            val argsStart = if (argsHolder.lparenOffset >= 0) {
                argsHolder.lparenOffset + 1
            } else {
                argsHolder.nameToken.textRange.endOffset
            }
            return countTopLevelCommas(fileText, argsStart, offset)
        }

        val holderType = argsHolder.node?.elementType
        if (holderType == CrystalTypes.IDENTIFIER || holderType == CrystalTypes.CONSTANT) {
            val fileText = argsHolder.containingFile?.text ?: return 0
            val argsStart = argsHolder.textRange.endOffset
            return countTopLevelCommas(fileText, argsStart, offset)
        }

        val holderText = argsHolder.text
        val startOffset = argsHolder.textRange.startOffset
        val relativeOffset = (offset - startOffset).coerceIn(0, holderText.length)

        val startPos: Int
        if (argsHolder is CrystalCallArgs) {
            startPos = 1
        } else if (argsHolder is CrystalBareArgumentList) {
            startPos = 0
        } else {
            val fileText = argsHolder.containingFile?.text ?: holderText
            val argsStart = CrystalBareCallParameterLocator.findBrokenArgumentsStart(fileText, offset)
            if (argsStart != null) return countTopLevelCommas(fileText, argsStart, offset)
            val parenPos = holderText.indexOf('(')
            startPos = if (parenPos >= 0 && parenPos < relativeOffset) parenPos + 1 else 0
        }

        return countTopLevelCommas(holderText, startPos, relativeOffset)
    }

    private fun countTopLevelCommas(text: String, from: Int, to: Int): Int {
        var index = 0
        var depth = 0
        for (i in from until to.coerceAtMost(text.length)) {
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) index++
            }
        }
        return index
    }
}
