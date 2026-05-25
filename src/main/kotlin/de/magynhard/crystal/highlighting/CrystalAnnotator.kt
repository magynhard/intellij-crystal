package de.magynhard.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*

/**
 * Semantic highlighter for Crystal.
 *
 * CONSTANT and IDENTIFIER tokens are NOT highlighted by the lexer (EMPTY_KEYS).
 * All context-sensitive coloring happens here, giving the annotator full control
 * over which color each token gets — no conflicts with lexer-level highlighting.
 */
class CrystalAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val elementType = element.node.elementType

        // Handle leaf tokens — CONSTANT and IDENTIFIER
        if (elementType == CrystalTypes.CONSTANT) {
            annotateConstantToken(element, holder)
            return
        }
        if (elementType == CrystalTypes.IDENTIFIER) {
            annotateIdentifierToken(element, holder)
            return
        }
    }

    /**
     * Context-sensitive highlighting for CONSTANT tokens:
     * - Inside type_name (class/module/struct/enum definition) → CLASS_DECLARATION
     * - Inside method_name (def self.Foo) → FUNCTION_DECLARATION
     * - Everywhere else → CONSTANT (type references, standalone constants)
     */
    private fun annotateConstantToken(element: PsiElement, holder: AnnotationHolder) {
        val parent = element.parent
        if (parent is CrystalTypeName) {
            apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
        } else if (parent is CrystalMethodName) {
            apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
        } else {
            apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
        }
    }

    /**
     * Context-sensitive highlighting for IDENTIFIER tokens:
     * - Inside method_name (def greet, def self.greet) → FUNCTION_DECLARATION
     * - Inside parameter definition → PARAMETER
     * - Usage of a parameter inside method body → PARAMETER
     * - Everything else → IDENTIFIER (default)
     */
    private fun annotateIdentifierToken(element: PsiElement, holder: AnnotationHolder) {
        val parent = element.parent

        // Method/macro name definition
        if (parent is CrystalMethodName) {
            apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
            return
        }

        // Parameter definition
        if (parent is CrystalParameter) {
            apply(holder, element, CrystalSyntaxHighlighter.PARAMETER)
            return
        }

        // Parameter usage inside method/macro body
        if (isParameterUsage(element)) {
            apply(holder, element, CrystalSyntaxHighlighter.PARAMETER)
            return
        }

        // Default: normal identifier
        apply(holder, element, CrystalSyntaxHighlighter.IDENTIFIER)
    }

    /**
     * Check if an IDENTIFIER token is a usage of a method/macro parameter.
     */
    private fun isParameterUsage(element: PsiElement): Boolean {
        val name = element.text
        if (name.isBlank()) return false

        // Find enclosing method or macro
        val methodDef = PsiTreeUtil.getParentOfType(
            element,
            CrystalMethodDefinition::class.java,
            CrystalMacroDefinition::class.java
        ) ?: return false

        // Get parameter names
        val paramList = when (methodDef) {
            is CrystalMethodDefinition -> methodDef.parameterList
            is CrystalMacroDefinition -> methodDef.parameterList
            else -> null
        } ?: return false

        val paramNames = paramList.parameterList.mapNotNull { param ->
            param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
        }.toSet()

        return name in paramNames
    }

    private fun apply(holder: AnnotationHolder, element: PsiElement, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(key)
            .create()
    }
}
