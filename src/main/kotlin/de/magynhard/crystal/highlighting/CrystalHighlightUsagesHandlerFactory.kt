package de.magynhard.crystal.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalMacroDefinition
import de.magynhard.crystal.psi.CrystalNamedElement
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Factory for highlighting all usages of a Crystal definition when the cursor
 * is on its name (e.g. `module Kann`, `class Foo`, `def bar`).
 *
 * Without this factory, clicking on a definition name produces no highlighting
 * because the CONSTANT/IDENTIFIER leaf inside the definition has no PsiReference
 * (createCrystalReference returns null for CrystalNamedElement parents). The
 * platform's default fallback (TargetElementUtilBase.findTargetElement) also fails
 * because the leaf itself is not a PsiNamedElement — only the parent composite is.
 *
 * This factory detects the definition-name case and delegates to
 * [CrystalHighlightUsagesHandler], which uses ReferencesSearch to find all usages
 * in the current file.
 */
class CrystalHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactory {

    override fun createHighlightUsagesHandler(editor: Editor, psiFile: PsiFile): HighlightUsagesHandlerBase<*>? {
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return null

        val elementType = element.node?.elementType
        if (elementType != CrystalTypes.CONSTANT && elementType != CrystalTypes.IDENTIFIER) {
            return null
        }

        // Check if this leaf is inside a definition name.
        // For types (class/module/struct/enum): parent is the definition (CrystalNamedElement).
        // For methods/macros: parent may be an intermediate node; walk up to the definition.
        val target = resolveToNamedElement(element) ?: return null

        // Don't highlight if cursor is on a keyword (class/def/module etc.) — only on the name.
        if (!isNameLeaf(element, target)) return null

        return CrystalHighlightUsagesHandler(editor, psiFile, target)
    }

    /**
     * Walk up from a CONSTANT/IDENTIFIER leaf to find the enclosing CrystalNamedElement.
     * Handles both direct children (types) and nested structures (methods with self.).
     */
    private fun resolveToNamedElement(element: com.intellij.psi.PsiElement): CrystalNamedElement? {
        var current: com.intellij.psi.PsiElement? = element.parent
        while (current != null) {
            if (current is CrystalNamedElement) return current
            // Stop at file boundary
            if (current is com.intellij.psi.PsiFile) return null
            current = current.parent
        }
        return null
    }

    /**
     * Verify that the clicked leaf is the actual name of the definition,
     * not a keyword like `class`, `def`, `module`, etc.
     */
    private fun isNameLeaf(element: com.intellij.psi.PsiElement, definition: CrystalNamedElement): Boolean {
        val nameId = definition.nameIdentifier ?: return false
        return element === nameId
    }
}
