package de.magynhard.crystal.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.*

/**
 * Handles Go to Definition (Ctrl+Click / Ctrl+B) for:
 * 1. Identifiers after DOT (e.g. "Apfel.tanzen" → jumps to "def self.tanzen" or "def tanzen")
 * 2. Instance variables (@name) and class variables (@@name) → jumps to property declaration or shows all usages
 */
class CrystalGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        val elementType = sourceElement.node.elementType

        // Handle instance variables (@name) and class variables (@@name)
        if (elementType == CrystalTypes.INSTANCE_VAR || elementType == CrystalTypes.CLASS_VAR) {
            // The leaf token's parent should be the CrystalInstanceVarAccess/CrystalClassVarAccess composite
            val varAccess = sourceElement.parent
            if (varAccess is CrystalInstanceVarAccess || varAccess is CrystalClassVarAccess) {
                val varName = varAccess.text
                val targets = CrystalInstanceVarFinder.findDefinitionTargets(varName, varAccess)
                if (targets.isNotEmpty()) return targets.toTypedArray()
                val usages = CrystalInstanceVarFinder.findAllUsages(varName, varAccess)
                    .filter { it !== varAccess }
                return if (usages.isNotEmpty()) usages.toTypedArray() else null
            }
            // Fallback for @name in property_declaration or parameter (still leaf tokens)
            val varName = sourceElement.text
            val targets = CrystalInstanceVarFinder.findDefinitionTargets(varName, sourceElement)
            if (targets.isNotEmpty()) return targets.toTypedArray()
            val usages = CrystalInstanceVarFinder.findAllUsages(varName, sourceElement)
                .filter { it !== sourceElement }
            return if (usages.isNotEmpty()) usages.toTypedArray() else null
        }

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
