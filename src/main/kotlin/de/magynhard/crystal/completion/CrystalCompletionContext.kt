package de.magynhard.crystal.completion

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalClassBody
import de.magynhard.crystal.psi.CrystalMethodBody
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Computes the completion prefix from the raw document text before the caret,
 * treating a leading `@` or `@@` as part of the variable name.
 */
internal fun computeCompletionPrefix(editor: Editor, offset: Int): String {
    val text = editor.document.charsSequence.subSequence(0, offset).toString()
    val match = "([@]@?[A-Za-z0-9_]*)$".toRegex().find(text)
    return match?.groupValues?.get(1) ?: ""
}

internal fun getPreviousNonWhitespaceLeaf(element: PsiElement): PsiElement? {
    var prev = PsiTreeUtil.prevLeaf(element)
    while (prev != null && prev.text.isBlank()) {
        prev = PsiTreeUtil.prevLeaf(prev)
    }
    return prev
}

internal fun isAfterDefKeywordInClassBody(position: PsiElement): Boolean {
    val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
    if (prev.node.elementType != CrystalTypes.DEF) return false

    val classBody = PsiTreeUtil.getParentOfType(position, CrystalClassBody::class.java)
    if (classBody != null) return true
    val structDef = PsiTreeUtil.getParentOfType(position, CrystalStructDefinition::class.java)
    return structDef != null
}

internal fun isInTypeAnnotationContext(position: PsiElement): Boolean {
    val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
    if (prev.node.elementType == CrystalTypes.COLON) {
        val beforeColon = getPreviousNonWhitespaceLeaf(prev) ?: return false
        val elementType = beforeColon.node.elementType
        return elementType == CrystalTypes.IDENTIFIER ||
            elementType == CrystalTypes.RPAREN ||
            elementType == CrystalTypes.INSTANCE_VAR ||
            elementType == CrystalTypes.CLASS_VAR
    }
    if (prev.node.elementType == CrystalTypes.PIPE) {
        val beforePipe = getPreviousNonWhitespaceLeaf(prev) ?: return false
        val elementType = beforePipe.node.elementType
        return elementType == CrystalTypes.CONSTANT ||
            elementType == CrystalTypes.QUESTION ||
            elementType == CrystalTypes.RPAREN
    }
    if (prev.node.elementType == CrystalTypes.LPAREN) {
        val beforeParen = getPreviousNonWhitespaceLeaf(prev) ?: return false
        return beforeParen.node.elementType == CrystalTypes.CONSTANT
    }
    if (prev.node.elementType == CrystalTypes.COMMA) {
        val parent = prev.parent
        return parent?.node?.elementType == CrystalTypes.TYPE_ARGUMENTS
    }
    return false
}

internal fun isInClassBodyNotMethod(position: PsiElement): Boolean {
    PsiTreeUtil.getParentOfType(position, CrystalClassBody::class.java) ?: return false
    val methodBody = PsiTreeUtil.getParentOfType(position, CrystalMethodBody::class.java)
    if (methodBody != null) return false
    val prev = getPreviousNonWhitespaceLeaf(position)
    if (prev != null && prev.node.elementType == CrystalTypes.DEF) return false
    return true
}

internal fun isInAnnotationContext(position: PsiElement): Boolean {
    val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
    if (prev.node.elementType != CrystalTypes.LBRACKET) return false
    val beforeBracket = getPreviousNonWhitespaceLeaf(prev) ?: return false
    return beforeBracket.node.elementType == CrystalTypes.AT
}

internal fun isAfterNumericLiteral(position: PsiElement): Boolean {
    val prev = getPreviousNonWhitespaceLeaf(position) ?: return false
    val tokenType = prev.node?.elementType
    if (tokenType != CrystalTypes.INTEGER_LITERAL && tokenType != CrystalTypes.FLOAT_LITERAL) return false
    val prevLine = prev.containingFile?.viewProvider?.document?.getLineNumber(prev.textRange.endOffset)
    val posLine = position.containingFile?.viewProvider?.document?.getLineNumber(position.textRange.startOffset)
    return prevLine == posLine
}

internal fun isInsideStringLiteral(position: PsiElement): Boolean {
    val tokenType = position.node?.elementType
    if (tokenType == CrystalTypes.STRING_LITERAL) return true

    val parent = position.parent
    if (parent?.node?.elementType == CrystalTypes.STRING_EXPRESSION) {
        var sibling = position.prevSibling
        while (sibling != null) {
            val sibType = sibling.node?.elementType
            if (sibType == CrystalTypes.STRING_INTERPOLATION_BEGIN) return false
            if (sibType == CrystalTypes.STRING_INTERPOLATION_END) break
            sibling = sibling.prevSibling
        }
        return true
    }
    return false
}
