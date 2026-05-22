package de.magynhard.crystal

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class CrystalTypedHandler : TypedHandlerDelegate() {

    private val PAIRS = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
    )

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.fileType != CrystalFileType) return Result.CONTINUE

        val closing = PAIRS[c]
        if (closing != null) {
            val offset = editor.caretModel.offset
            val document = editor.document
            val text = document.charsSequence
            // Only auto-close if next char is whitespace, newline, closing bracket, or end of file
            if (offset >= text.length || text[offset].let { it.isWhitespace() || it == ')' || it == ']' || it == '}' || it == ',' || it == ';' }) {
                EditorModificationUtil.insertStringAtCaret(editor, closing.toString(), false, 0)
            }
        }
        return Result.CONTINUE
    }
}
