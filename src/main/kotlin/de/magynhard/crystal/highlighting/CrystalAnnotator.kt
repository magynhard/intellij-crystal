package de.magynhard.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
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
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(methodName)
            .textAttributes(CrystalSyntaxHighlighter.FUNCTION_DECLARATION)
            .create()
    }

    /**
     * Highlight the CONSTANT leaf tokens inside a type_name composite.
     * This ensures "class Apfel" highlights "Apfel" with CLASS_DECLARATION color,
     * overriding the default CONSTANT highlighting from the lexer.
     */
    private fun annotateTypeDefinition(typeName: CrystalTypeName?, holder: AnnotationHolder) {
        if (typeName == null) return
        // Walk leaf children to find all CONSTANT tokens (handles Foo::Bar::Baz)
        var child = typeName.firstChild
        while (child != null) {
            if (child.node.elementType == CrystalTypes.CONSTANT) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(child)
                    .textAttributes(CrystalSyntaxHighlighter.CLASS_DECLARATION)
                    .create()
            }
            child = child.nextSibling
        }
    }

    /**
     * Highlight the IDENTIFIER token inside a parameter definition.
     */
    private fun annotateParameter(element: CrystalParameter, holder: AnnotationHolder) {
        val ident = element.node.findChildByType(CrystalTypes.IDENTIFIER) ?: return
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(ident.psi)
            .textAttributes(CrystalSyntaxHighlighter.PARAMETER)
            .create()
    }

    /**
     * If an IDENTIFIER inside a method/macro body matches a parameter name,
     * highlight it as a parameter usage.
     */
    private fun annotateParameterUsage(element: PsiElement, holder: AnnotationHolder) {
        // Only for IDENTIFIER leaf tokens
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
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(CrystalSyntaxHighlighter.PARAMETER)
                .create()
        }
    }
}
