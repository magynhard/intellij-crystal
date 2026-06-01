package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.elementType
import de.magynhard.crystal.psi.CrystalArrayLiteral
import de.magynhard.crystal.psi.CrystalHashLiteral
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Inspection that reports empty array literals `[]` and empty hash literals `{}`
 * without type annotations. In Crystal, empty collections require explicit type
 * information: `[] of Type` or `{} of KeyType => ValueType`.
 */
class CrystalEmptyCollectionInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                when (element) {
                    is CrystalArrayLiteral -> checkArrayLiteral(element, holder)
                    is CrystalHashLiteral -> checkHashLiteral(element, holder)
                }
            }
        }
    }

    private fun checkArrayLiteral(element: CrystalArrayLiteral, holder: ProblemsHolder) {
        // If it has 'of' type annotation, it's fine
        if (hasOfKeyword(element)) return
        // If it has elements, it's fine
        if (hasElements(element)) return

        holder.registerProblem(
            element,
            "Empty array literal requires type: use '[] of Type'",
            ProblemHighlightType.GENERIC_ERROR,
            AddArrayTypeAnnotationFix()
        )
    }

    private fun checkHashLiteral(element: CrystalHashLiteral, holder: ProblemsHolder) {
        // If it has 'of' type annotation, it's fine
        if (hasOfKeyword(element)) return
        // If it has entries, it's fine
        if (hasElements(element)) return

        holder.registerProblem(
            element,
            "Empty hash literal requires type: use '{} of KeyType => ValueType'",
            ProblemHighlightType.GENERIC_ERROR,
            AddHashTypeAnnotationFix()
        )
    }

    /**
     * Checks if the literal has any child PSI elements (expression_list, hash_entry_list, etc.)
     * beyond just brackets/braces and whitespace/newlines.
     */
    private fun hasElements(element: PsiElement): Boolean {
        var child = element.firstChild
        while (child != null) {
            val type = child.elementType
            if (type != null
                && type != CrystalTypes.LBRACKET && type != CrystalTypes.RBRACKET
                && type != CrystalTypes.LBRACE && type != CrystalTypes.RBRACE
                && type != CrystalTypes.NEWLINE && type != CrystalTypes.OF
                && type != com.intellij.psi.TokenType.WHITE_SPACE) {
                return true
            }
            child = child.nextSibling
        }
        return false
    }

    private fun hasOfKeyword(element: PsiElement): Boolean {
        var child = element.firstChild
        while (child != null) {
            if (child.elementType == CrystalTypes.OF) return true
            child = child.nextSibling
        }
        return false
    }

    private class AddArrayTypeAnnotationFix : LocalQuickFix {
        override fun getName() = "Add type annotation ([] of Type)"
        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return
            val document = element.containingFile?.viewProvider?.document ?: return
            val endOffset = element.textRange.endOffset
            val insertText = " of "
            document.insertString(endOffset, insertText)
            // Move cursor after " of "
            getEditor(project)?.caretModel?.moveToOffset(endOffset + insertText.length)
        }
    }

    private class AddHashTypeAnnotationFix : LocalQuickFix {
        override fun getName() = "Add type annotation ({} of KeyType => ValueType)"
        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement ?: return
            val document = element.containingFile?.viewProvider?.document ?: return
            val endOffset = element.textRange.endOffset
            val insertText = " of  => "
            document.insertString(endOffset, insertText)
            // Move cursor after " of " (before " => ")
            getEditor(project)?.caretModel?.moveToOffset(endOffset + 4)
        }
    }

    companion object {
        private fun getEditor(project: Project): Editor? {
            val fileEditor = FileEditorManager.getInstance(project).selectedEditor
            return (fileEditor as? TextEditor)?.editor
        }
    }
}
