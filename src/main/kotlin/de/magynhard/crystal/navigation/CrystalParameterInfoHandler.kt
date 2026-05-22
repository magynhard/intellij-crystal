package de.magynhard.crystal.navigation

import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*

class CrystalParameterInfoHandler : ParameterInfoHandler<CrystalCallArgs, CrystalMethodDefinition> {

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): CrystalCallArgs? {
        val callArgs = findCallArgs(context.file, context.offset) ?: return null
        val methodName = findMethodNameForCall(callArgs) ?: return null

        // Find matching method definitions in the file
        val file = context.file
        val methods = PsiTreeUtil.findChildrenOfType(file, CrystalMethodDefinition::class.java)
            .filter { it.methodName?.text == methodName }
            .toTypedArray()

        if (methods.isEmpty()) return null
        context.itemsToShow = methods
        return callArgs
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): CrystalCallArgs? {
        return findCallArgs(context.file, context.offset)
    }

    override fun showParameterInfo(element: CrystalCallArgs, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun updateParameterInfo(parameterOwner: CrystalCallArgs, context: UpdateParameterInfoContext) {
        val offset = context.offset
        val text = parameterOwner.text
        val relativeOffset = offset - parameterOwner.textRange.startOffset

        // Count commas before cursor to determine current parameter index
        var index = 0
        var depth = 0
        for (i in 0 until minOf(relativeOffset, text.length)) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 1) index++
            }
        }
        context.setCurrentParameter(index)
    }

    override fun updateUI(method: CrystalMethodDefinition?, context: ParameterInfoUIContext) {
        if (method == null) {
            context.isUIComponentEnabled = false
            return
        }

        val paramList = method.parameterList?.parameterList ?: emptyList()
        if (paramList.isEmpty()) {
            context.setupUIComponentPresentation(
                "<no parameters>",
                -1, -1,
                false, false, false,
                context.defaultParameterColor
            )
            return
        }

        val params = paramList.map { formatParameter(it) }
        val text = params.joinToString(", ")

        // Calculate highlight range for current parameter
        val currentIndex = context.currentParameterIndex
        var startHighlight = -1
        var endHighlight = -1

        if (currentIndex in params.indices) {
            startHighlight = params.take(currentIndex).sumOf { it.length + 2 }
            endHighlight = startHighlight + params[currentIndex].length
        }

        context.setupUIComponentPresentation(
            text,
            startHighlight, endHighlight,
            false, false, false,
            context.defaultParameterColor
        )
    }

    private fun formatParameter(param: CrystalParameter): String {
        val text = param.text.trim()
        return text
    }

    private fun findCallArgs(file: PsiFile, offset: Int): CrystalCallArgs? {
        val element = file.findElementAt(offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, CrystalCallArgs::class.java)
    }

    private fun findMethodNameForCall(callArgs: CrystalCallArgs): String? {
        val parent = callArgs.parent
        if (parent is CrystalMethodCallExpression) {
            // First child should be the identifier
            val firstChild = parent.firstChild
            return firstChild?.text
        }
        return null
    }
}
