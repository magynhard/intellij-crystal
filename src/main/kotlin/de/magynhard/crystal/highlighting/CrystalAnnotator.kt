package de.magynhard.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*

class CrystalAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is CrystalMethodDefinition -> annotateMethodDefinition(element, holder)
            is CrystalClassDefinition -> annotateTypeDefinition(element.typeName, holder)
            is CrystalModuleDefinition -> annotateTypeDefinition(element.typeName, holder)
            is CrystalStructDefinition -> annotateTypeDefinition(element.typeName, holder)
            is CrystalEnumDefinition -> annotateTypeDefinition(element.typeName, holder)
            is CrystalParameter -> annotateParameter(element, holder)
        }

        // Highlight parameter usages inside method/macro bodies
        if (element.node.elementType == CrystalTypes.IDENTIFIER) {
            annotateParameterUsage(element, holder)
        }
    }

    private fun annotateMethodDefinition(element: CrystalMethodDefinition, holder: AnnotationHolder) {
        val methodName = element.methodName ?: return
        applyEnforced(holder, methodName, CrystalSyntaxHighlighter.FUNCTION_DECLARATION)
    }

    /**
     * Highlight the CONSTANT leaf tokens inside a type_name composite.
     * Uses enforcedTextAttributes to override the lexer-level CONSTANT highlighting.
     */
    private fun annotateTypeDefinition(typeName: CrystalTypeName?, holder: AnnotationHolder) {
        if (typeName == null) return
        var child = typeName.firstChild
        while (child != null) {
            if (child.node.elementType == CrystalTypes.CONSTANT) {
                applyEnforced(holder, child, CrystalSyntaxHighlighter.CLASS_DECLARATION)
            }
            child = child.nextSibling
        }
    }

    /**
     * Highlight the IDENTIFIER token inside a parameter definition.
     */
    private fun annotateParameter(element: CrystalParameter, holder: AnnotationHolder) {
        val ident = element.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return
        applyEnforced(holder, ident.psi, CrystalSyntaxHighlighter.PARAMETER)
    }

    /**
     * If an IDENTIFIER inside a method/macro body matches a parameter name,
     * highlight it as a parameter usage.
     */
    private fun annotateParameterUsage(element: PsiElement, holder: AnnotationHolder) {
        val name = element.text
        if (name.isBlank()) return

        // Skip identifiers that are already inside a parameter definition
        if (PsiTreeUtil.getParentOfType(element, CrystalParameter::class.java) != null) return

        // Find enclosing method or macro
        val methodDef = PsiTreeUtil.getParentOfType(
            element,
            CrystalMethodDefinition::class.java,
            CrystalMacroDefinition::class.java
        ) ?: return

        // Get parameter names
        val paramList = when (methodDef) {
            is CrystalMethodDefinition -> methodDef.parameterList
            is CrystalMacroDefinition -> methodDef.parameterList
            else -> null
        } ?: return

        val paramNames = paramList.parameterList.mapNotNull { param ->
            param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
        }.toSet()

        if (name in paramNames) {
            applyEnforced(holder, element, CrystalSyntaxHighlighter.PARAMETER)
        }
    }

    /**
     * Apply highlighting using enforcedTextAttributes so that it overrides
     * the lexer-level token highlighting (e.g. CONSTANT, IDENTIFIER).
     * Falls back to textAttributes if the color scheme lookup fails.
     */
    private fun applyEnforced(holder: AnnotationHolder, element: PsiElement, key: TextAttributesKey) {
        val scheme = EditorColorsManager.getInstance().globalScheme
        val attrs = scheme.getAttributes(key)
        if (attrs != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .enforcedTextAttributes(attrs)
                .create()
        } else {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(key)
                .create()
        }
    }
}
