package de.magynhard.crystal

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes

class CrystalFoldingBuilder : FoldingBuilderEx() {

    companion object {
        private val BLOCK_START_TOKENS = setOf(
            CrystalTokenTypes.DEF,
            CrystalTokenTypes.CLASS,
            CrystalTokenTypes.MODULE,
            CrystalTokenTypes.STRUCT,
            CrystalTokenTypes.ENUM,
            CrystalTokenTypes.DO,
            CrystalTokenTypes.BEGIN,
            CrystalTokenTypes.IF,
            CrystalTokenTypes.UNLESS,
            CrystalTokenTypes.WHILE,
            CrystalTokenTypes.UNTIL,
            CrystalTokenTypes.CASE,
            CrystalTokenTypes.MACRO,
            CrystalTokenTypes.LIB,
            CrystalTokenTypes.FUN,
            CrystalTokenTypes.ANNOTATION,
        )
    }

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        val elements = PsiTreeUtil.collectElements(root) { true }

        // Stack-based matching of block-start keywords to 'end'
        val startStack = mutableListOf<PsiElement>()

        for (element in elements) {
            val tokenType = element.node?.elementType ?: continue
            if (tokenType in BLOCK_START_TOKENS) {
                startStack.add(element)
            } else if (tokenType == CrystalTokenTypes.END && startStack.isNotEmpty()) {
                val start = startStack.removeAt(startStack.lastIndex)
                val startLine = document.getLineNumber(start.textRange.startOffset)
                val endLine = document.getLineNumber(element.textRange.endOffset)
                // Only fold if it spans multiple lines
                if (endLine > startLine) {
                    val range = TextRange(start.textRange.endOffset, element.textRange.endOffset)
                    if (range.length > 0) {
                        descriptors.add(FoldingDescriptor(start.node, range))
                    }
                }
            }
        }

        // Fold multi-line comments (consecutive comment lines)
        var commentStart: PsiElement? = null
        var commentEnd: PsiElement? = null
        for (element in elements) {
            val tokenType = element.node?.elementType ?: continue
            if (tokenType == CrystalTokenTypes.LINE_COMMENT) {
                if (commentStart == null) {
                    commentStart = element
                }
                commentEnd = element
            } else if (tokenType != CrystalTokenTypes.NEWLINE && tokenType != CrystalTokenTypes.WHITE_SPACE) {
                if (commentStart != null && commentEnd != null && commentStart != commentEnd) {
                    val startLine = document.getLineNumber(commentStart.textRange.startOffset)
                    val endLine = document.getLineNumber(commentEnd.textRange.endOffset)
                    if (endLine > startLine) {
                        val range = TextRange(
                            commentStart.textRange.startOffset,
                            commentEnd.textRange.endOffset
                        )
                        descriptors.add(FoldingDescriptor(commentStart.node, range))
                    }
                }
                commentStart = null
                commentEnd = null
            }
        }

        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String {
        return when (node.elementType) {
            CrystalTokenTypes.LINE_COMMENT -> "# ..."
            CrystalTokenTypes.DEF -> "..."
            CrystalTokenTypes.CLASS -> "..."
            CrystalTokenTypes.MODULE -> "..."
            CrystalTokenTypes.STRUCT -> "..."
            CrystalTokenTypes.ENUM -> "..."
            else -> "..."
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
