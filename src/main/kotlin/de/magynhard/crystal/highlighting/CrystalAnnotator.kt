package de.magynhard.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.*

class CrystalAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is CrystalMethodDefinition -> {
                val methodName = element.methodName ?: return
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(methodName)
                    .textAttributes(CrystalSyntaxHighlighter.FUNCTION_DECLARATION)
                    .create()
            }
            is CrystalClassDefinition -> {
                val typeName = element.typeName ?: return
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(typeName)
                    .textAttributes(CrystalSyntaxHighlighter.CLASS_DECLARATION)
                    .create()
            }
            is CrystalModuleDefinition -> {
                val typeName = element.typeName ?: return
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(typeName)
                    .textAttributes(CrystalSyntaxHighlighter.CLASS_DECLARATION)
                    .create()
            }
            is CrystalStructDefinition -> {
                val typeName = element.typeName ?: return
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(typeName)
                    .textAttributes(CrystalSyntaxHighlighter.CLASS_DECLARATION)
                    .create()
            }
            is CrystalEnumDefinition -> {
                val typeName = element.typeName ?: return
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(typeName)
                    .textAttributes(CrystalSyntaxHighlighter.CLASS_DECLARATION)
                    .create()
            }
        }
    }
}
