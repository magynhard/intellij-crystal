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
import de.magynhard.crystal.psi.CrystalTypes

class CrystalFoldingBuilder : FoldingBuilderEx() {

    companion object {
        private val BLOCK_START_TOKENS = setOf(
            CrystalTypes.DEF,
            CrystalTypes.CLASS,
            CrystalTypes.MODULE,
            CrystalTypes.STRUCT,
            CrystalTypes.ENUM,
            CrystalTypes.DO,
            CrystalTypes.BEGIN,
            CrystalTypes.IF,
            CrystalTypes.UNLESS,
            CrystalTypes.WHILE,
            CrystalTypes.UNTIL,
            CrystalTypes.CASE,
            CrystalTypes.MACRO,
            CrystalTypes.LIB,
            CrystalTypes.FUN,
            CrystalTypes.ANNOTATION,
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
            } else if (tokenType == CrystalTypes.END && startStack.isNotEmpty()) {
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
            if (tokenType == CrystalTypes.LINE_COMMENT) {
                if (commentStart == null) {
                    commentStart = element
                }
                commentEnd = element
            } else if (tokenType != CrystalTypes.NEWLINE && tokenType != CrystalTokenTypes.WHITE_SPACE) {
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
            CrystalTypes.LINE_COMMENT -> "# ..."
            CrystalTypes.DEF -> "..."
            CrystalTypes.CLASS -> "..."
            CrystalTypes.MODULE -> "..."
            CrystalTypes.STRUCT -> "..."
            CrystalTypes.ENUM -> "..."
            else -> "..."
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
