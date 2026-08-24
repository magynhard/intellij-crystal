package de.magynhard.crystal.highlighting

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import de.magynhard.crystal.psi.CrystalTypeReference
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Flags a dangling `|` at the end of a line inside a union type. Crystal rejects
 * multi-line unions without a backslash continuation ("expecting token 'CONST',
 * not 'NEWLINE'"), but reporting that through the parser would pin GrammarKit's
 * generic error marker to the enclosing keyword (e.g. the `rescue` keyword).
 *
 * This annotator reports precisely on the pipe instead, mirrors the compiler
 * message, and offers an "add backslash" quick fix.
 */
class CrystalUnionContinuationAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType != CrystalTypes.PIPE) return
        if (element.parent !is CrystalTypeReference) return

        var next: PsiElement? = element.nextSibling
        while (next is PsiWhiteSpace) next = next.nextSibling
        if (next == null || next.node.elementType != CrystalTypes.NEWLINE) return

        holder.newAnnotation(HighlightSeverity.ERROR, MESSAGE)
            .range(element)
            .withFix(AddBackslashContinuationFix())
            .create()
    }

    companion object {
        const val MESSAGE =
            "Expected a type after '|' — multi-line union requires a '\\' line continuation " +
                "(crystal: expecting token 'CONST', not 'NEWLINE')"
    }
}

/**
 * Inserts a `\` immediately before the terminating newline so the lexer's
 * line-continuation rule (`\` + newline → whitespace) engages.
 */
class AddBackslashContinuationFix : LocalQuickFix, IntentionAction {

    override fun getName(): String = "Add backslash line continuation"
    override fun getFamilyName(): String = name
    override fun getText(): String = name
    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: com.intellij.openapi.editor.Editor?, file: com.intellij.psi.PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: com.intellij.openapi.editor.Editor?, file: com.intellij.psi.PsiFile?) {
        val atCaret = file?.findElementAt(editor?.caretModel?.offset ?: return) ?: return
        val pipe = if (atCaret.node.elementType == CrystalTypes.PIPE) atCaret
        else atCaret.prevSibling?.takeIf { it.node.elementType == CrystalTypes.PIPE } ?: return
        applyAt(pipe)
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        applyAt(descriptor.psiElement ?: return)
    }

    private fun applyAt(pipe: PsiElement) {
        val document: Document =
            PsiDocumentManager.getInstance(pipe.project).getDocument(pipe.containingFile ?: return) ?: return

        var next: PsiElement? = pipe.nextSibling
        while (next != null && next.node.elementType != CrystalTypes.NEWLINE) next = next.nextSibling
        if (next == null) return

        val offset = next.textRange.startOffset
        WriteCommandAction.runWriteCommandAction(pipe.project) {
            document.insertString(offset, "\\")
            PsiDocumentManager.getInstance(pipe.project).commitDocument(document)
        }
    }
}
