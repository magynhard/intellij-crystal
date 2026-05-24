package de.magynhard.crystal.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.*

/**
 * Handles Go to Definition (Ctrl+Click / Ctrl+B) for identifiers after DOT,
 * e.g. "Apfel.tanzen" → jumps to "def self.tanzen" or "def tanzen".
 *
 * This complements the PSI mixin-based references (which handle variable_reference,
 * method_call_expression, etc.) by covering leaf IDENTIFIER tokens in postfix_op
 * positions that don't have their own composite PSI wrapper.
 */
class CrystalGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        val elementType = sourceElement.node.elementType
        if (elementType != CrystalTypes.IDENTIFIER && elementType != CrystalTypes.CONSTANT) {
            return null
        }

        val name = sourceElement.text
        if (name.isBlank()) return null

        // Check if this identifier is after a DOT (dot-call like "obj.method" or "Class.method")
        val prev = skipWhitespaceBefore(sourceElement)
        if (prev != null && prev.node.elementType == CrystalTypes.DOT) {
            val results = CrystalDefinitionFinder.findDefinitions(name, sourceElement.project)
            return if (results.isNotEmpty()) results.toTypedArray() else null
        }

        return null
    }

    private fun skipWhitespaceBefore(element: PsiElement): PsiElement? {
        var prev = element.prevSibling
        while (prev != null && prev.node.elementType.toString() == "WHITE_SPACE") {
            prev = prev.prevSibling
        }
        return prev
    }
}
