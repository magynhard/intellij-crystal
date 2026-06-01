package de.magynhard.crystal.navigation

import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalAsmExpression
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Provides parameter info (Ctrl+P) for Crystal asm() expressions.
 * Shows the 5 sections: template : outputs : inputs : clobbers : options
 * and highlights the current section based on cursor position (colon count).
 */
class CrystalAsmParameterInfoHandler : ParameterInfoHandler<CrystalAsmExpression, CrystalAsmParameterInfoHandler.AsmInfo> {

    data class AsmInfo(val sections: List<String> = listOf("template", "outputs", "inputs", "clobbers", "options"))

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): CrystalAsmExpression? {
        val asmExpr = findAsmExpression(context.file, context.offset) ?: return null
        context.itemsToShow = arrayOf(AsmInfo())
        return asmExpr
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): CrystalAsmExpression? {
        return findAsmExpression(context.file, context.offset)
    }

    override fun showParameterInfo(element: CrystalAsmExpression, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset + 4, this) // after "asm("
    }

    override fun updateParameterInfo(parameterOwner: CrystalAsmExpression, context: UpdateParameterInfoContext) {
        val index = computeCurrentSection(parameterOwner, context.offset)
        context.setCurrentParameter(index)
    }

    override fun updateUI(info: AsmInfo?, context: ParameterInfoUIContext) {
        if (info == null) {
            context.isUIComponentEnabled = false
            return
        }

        val sections = info.sections
        val text = sections.joinToString(" : ")
        val currentIndex = context.currentParameterIndex

        var startHighlight = -1
        var endHighlight = -1

        if (currentIndex in sections.indices) {
            startHighlight = sections.take(currentIndex).sumOf { it.length + 3 }
            endHighlight = startHighlight + sections[currentIndex].length
        }

        context.setupUIComponentPresentation(
            text,
            startHighlight, endHighlight,
            false, false, false,
            context.defaultParameterColor
        )
    }

    private fun findAsmExpression(file: com.intellij.psi.PsiFile, offset: Int): CrystalAsmExpression? {
        val element = file.findElementAt(offset) ?: file.findElementAt(offset - 1) ?: return null
        return PsiTreeUtil.getParentOfType(element, CrystalAsmExpression::class.java)
    }

    /**
     * Determines which section the cursor is in by counting COLON and DOUBLE_COLON tokens
     * before the cursor offset within the asm expression.
     */
    private fun computeCurrentSection(asmExpr: CrystalAsmExpression, offset: Int): Int {
        var colonCount = 0
        var child = asmExpr.firstChild
        while (child != null) {
            if (child.textRange.startOffset >= offset) break
            when (child.node.elementType) {
                CrystalTypes.COLON -> colonCount++
                CrystalTypes.DOUBLE_COLON -> colonCount += 2
            }
            child = child.nextSibling
        }
        return colonCount.coerceAtMost(4)
    }
}
