package de.magynhard.crystal.ameba

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.psi.CrystalArrayLiteral
import de.magynhard.crystal.psi.CrystalHashLiteral
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Whether [issue] earns the collection fix at [file]/[offset]: a syntax-rule
 * finding on an empty, untyped `[]`/`{}` literal in a plain Crystal file.
 * The rule check is loose across Ameba versions (`Lint/Syntax` or bare
 * `Syntax`); the PSI shape makes it precise (blocks never qualify).
 * `.ecr` is excluded: template coordinates have no reliable fix target.
 */
internal fun shouldOfferCollectionFix(issue: AmebaIssue, file: PsiFile, offset: Int): Boolean {
    if (file.fileType != CrystalFileType) return false
    if (!issue.ruleName.substringAfterLast('/').equals("Syntax", ignoreCase = true)) return false
    return findEmptyUntypedCollection(file, offset) != null
}

/**
 * The empty, untyped array/hash literal enclosing [offset], or null.
 * Mirrors the built-in empty-collection contract: no `of` annotation and no
 * content beyond brackets, whitespace, and newlines.
 */
internal fun findEmptyUntypedCollection(file: PsiFile, offset: Int): PsiElement? {
    val leaf = file.findElementAt(offset) ?: return null
    val literal: PsiElement = PsiTreeUtil.getParentOfType(leaf, CrystalArrayLiteral::class.java, false)
        ?: PsiTreeUtil.getParentOfType(leaf, CrystalHashLiteral::class.java, false)
        ?: return null
    var child = literal.firstChild
    while (child != null) {
        val type = child.elementType
        if (type == CrystalTypes.OF) return null
        if (type != null &&
            type != CrystalTypes.LBRACKET && type != CrystalTypes.RBRACKET &&
            type != CrystalTypes.LBRACE && type != CrystalTypes.RBRACE &&
            type != CrystalTypes.NEWLINE && type != com.intellij.psi.TokenType.WHITE_SPACE
        ) {
            return null
        }
        child = child.nextSibling
    }
    return literal
}

/**
 * Editor intention and batch quickfix inserting the missing collection type
 * keyword (`[]`/`{}` → `[] of <caret>`) and opening code completion. The
 * contributor offers classes only; picking one from the hash chain inserts
 * ` => ` and immediately reopens completion for the value type.
 * Attached per Ameba syntax annotation (same popup, Alt+Enter); executed
 * only where [findEmptyUntypedCollection] resolves — anywhere else it
 * safely does nothing.
 */
class EmptyCollectionTypeFix : LocalQuickFix, IntentionAction {

    override fun getName(): String = "Add collection type annotation"

    override fun getFamilyName(): String = "Add collection type annotation"

    override fun getText(): String = name

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        return file.fileType == CrystalFileType
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val caretOffset = editor?.caretModel?.offset ?: return
        applyAtOffset(project, file, editor, caretOffset)
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val file = element.containingFile ?: return
        val range = descriptor.textRangeInElement ?: return
        val base = element.textRange?.startOffset ?: 0
        val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor
        applyAtOffset(project, file, editor, base + range.startOffset)
    }

    private fun applyAtOffset(project: Project, file: PsiFile, editor: Editor?, offset: Int) {
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile ?: return) ?: return
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val literal = findEmptyUntypedCollection(file, offset) ?: return
        val endOffset = literal.textRange.endOffset
        WriteCommandAction.runWriteCommandAction(project, "Add collection type annotation", null, {
            document.insertString(endOffset, " of ")
            editor?.caretModel?.moveToOffset(endOffset + 4)
        })
        if (editor != null) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
    }
}
