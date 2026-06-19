package de.magynhard.crystal.inspections

import com.intellij.codeInspection.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import de.magynhard.crystal.psi.*

/**
 * Inspection that reports missing spaces before/after colon in parameter type
 * annotations and return type annotations.
 *
 * Crystal requires: `speed : String` (space before AND after colon).
 * Flags: `speed: String`, `speed :String`, `speed:String`
 *
 * Handles two cases:
 * 1. PSI-based: when parser succeeds and COLON token exists (e.g. `speed: String`)
 * 2. Text-based: when `:String` is lexed as a symbol literal (e.g. `speed :String`)
 *
 * Exception: colon after `=` (default value) is exempt, e.g. `= :name` is valid.
 */
class CrystalColonSpacingInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is CrystalMethodDefinition) {
                    checkMethodDefinition(element, holder)
                }
            }
        }
    }

    // ==================== File-level text scan ====================
    // Catches `:IDENTIFIER` patterns where the lexer produced a symbol literal
    // instead of COLON + IDENTIFIER (e.g. `speed :String` → SYMBOL_COLON)
    // Only scans within method definitions to avoid false positives on method calls.

    private fun scanMethodForMissingSpaceAfterColon(method: CrystalMethodDefinition, holder: ProblemsHolder) {
        val file = method.containingFile ?: return
        val fileText = file.text
        val methodStart = method.textOffset
        val methodEnd = methodStart + method.textLength

        // Only scan the DEF line (header), not the method body
        // Find the end of the first line of the method definition
        val firstNewline = fileText.indexOf('\n', methodStart)
        val headerEnd = if (firstNewline >= 0 && firstNewline < methodEnd) firstNewline else methodEnd

        val regex = Regex(":(?=[A-Za-z_])")
        var offset = methodStart
        while (offset < headerEnd) {
            val match = regex.find(fileText, offset) ?: break
            val colonOffset = match.range.first

            // Exception: preceded by `=` (default value colon)
            if (isPrecededByEquals(fileText, colonOffset)) {
                offset = match.range.last + 1
                continue
            }

            // Exception: preceded by another identifier (hash key like `hash[:key]`)
            if (isPrecededByIdentifier(fileText, colonOffset)) {
                offset = match.range.last + 1
                continue
            }

            // Check if already reported by PSI-based check
            if (isAlreadyReported(file, colonOffset)) {
                offset = match.range.last + 1
                continue
            }

            // Report error on the colon
            val elementAtColon = file.findElementAt(colonOffset)
            if (elementAtColon != null) {
                holder.registerProblem(
                    elementAtColon,
                    "Space required after colon in type annotation",
                    ProblemHighlightType.GENERIC_ERROR,
                    InsertSpaceAfterColonQuickFix(colonOffset)
                )
            }

            offset = match.range.last + 1
        }
    }

    private fun isPrecededByEquals(text: String, colonOffset: Int): Boolean {
        var i = colonOffset - 1
        while (i >= 0 && text[i] == ' ') i--
        return i >= 0 && text[i] == '='
    }

    private fun isPrecededByIdentifier(text: String, colonOffset: Int): Boolean {
        if (colonOffset == 0) return false
        val prevChar = text[colonOffset - 1]
        return prevChar.isLetterOrDigit() || prevChar == '_'
    }

    private fun isAlreadyReported(file: PsiFile, colonOffset: Int): Boolean {
        // Check if this colon is already flagged by the PSI-based check
        val element = file.findElementAt(colonOffset) ?: return false
        // If the element is a COLON token (PSI-based found it), it's already reported
        return element.node.elementType == CrystalTypes.COLON
    }

    // ==================== PSI-based checks ====================

    private fun checkMethodDefinition(method: CrystalMethodDefinition, holder: ProblemsHolder) {
        val paramList = method.parameterList ?: return
        for (param in paramList.parameterList) {
            checkParameterColonSpacing(param, holder)
        }
        checkReturnTypeColon(method, holder)
        scanMethodForMissingSpaceAfterColon(method, holder)
    }

    private fun checkParameterColonSpacing(param: CrystalParameter, holder: ProblemsHolder) {
        val children = param.node.getChildren(null)
        for (i in children.indices) {
            if (children[i].elementType != CrystalTypes.COLON) continue
            if (isAfterEquals(children, i)) continue
            checkColonSpacing(children, i, holder)
        }
    }

    private fun checkReturnTypeColon(method: CrystalMethodDefinition, holder: ProblemsHolder) {
        val children = method.node.getChildren(null)
        for (i in children.indices) {
            if (children[i].elementType != CrystalTypes.COLON) continue
            if (!isReturnTypeColon(children, i)) continue
            checkColonSpacing(children, i, holder)
        }
    }

    private fun isAfterEquals(children: Array<ASTNode>, colonIndex: Int): Boolean {
        for (j in colonIndex - 1 downTo 0) {
            val prev = children[j]
            if (prev is PsiWhiteSpace) continue
            return prev.elementType == CrystalTypes.ASSIGN
        }
        return false
    }

    private fun isReturnTypeColon(children: Array<ASTNode>, colonIndex: Int): Boolean {
        for (j in colonIndex - 1 downTo 0) {
            val prev = children[j]
            if (prev is PsiWhiteSpace) continue
            if (prev.elementType == CrystalTypes.RPAREN) return true
            if (prev.elementType == CrystalTypes.TYPE_REFERENCE) return false
            return false
        }
        return false
    }

    private fun checkColonSpacing(children: Array<ASTNode>, colonIndex: Int, holder: ProblemsHolder) {
        val colon = children[colonIndex]

        val hasSpaceBefore = colonIndex > 0 && children[colonIndex - 1] is PsiWhiteSpace
        val hasSpaceAfter = colonIndex < children.size - 1 && children[colonIndex + 1] is PsiWhiteSpace

        if (!hasSpaceBefore || !hasSpaceAfter) {
            val message = buildString {
                append("Space required around colon in type annotation")
                if (!hasSpaceBefore && !hasSpaceAfter) append(" (before and after)")
                else if (!hasSpaceBefore) append(" (before colon)")
                else append(" (after colon)")
            }
            holder.registerProblem(
                colon.psi,
                message,
                ProblemHighlightType.GENERIC_ERROR,
                ColonSpacingQuickFix(colon, !hasSpaceBefore, !hasSpaceAfter)
            )
        }
    }

    // ==================== Quick-fixes ====================

    private class InsertSpaceAfterColonQuickFix(private val colonOffset: Int) : LocalQuickFix {
        override fun getName(): String = "Insert space after colon"
        override fun getFamilyName(): String = "Crystal Colon Spacing"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val psiFile = descriptor.psiElement?.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
            document.insertString(colonOffset + 1, " ")
        }
    }

    private class ColonSpacingQuickFix(
        private val colonNode: ASTNode,
        private val insertBefore: Boolean,
        private val insertAfter: Boolean
    ) : LocalQuickFix {

        override fun getName(): String = "Insert space(s) around colon"
        override fun getFamilyName(): String = "Crystal Colon Spacing"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val psiFile = colonNode.psi.containingFile ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
            val colonOffset = colonNode.startOffset

            if (insertAfter) {
                document.insertString(colonOffset + 1, " ")
            }
            if (insertBefore) {
                document.insertString(colonOffset, " ")
            }
        }
    }
}
