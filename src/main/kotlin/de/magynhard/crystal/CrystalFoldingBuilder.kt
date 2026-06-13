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
            CrystalTypes.FOR,
            CrystalTypes.CASE,
            CrystalTypes.MACRO,
            CrystalTypes.LIB,
            CrystalTypes.ANNOTATION,
            CrystalTypes.VERBATIM,
        )

        private val CONDITIONAL_KEYWORDS = setOf(
            CrystalTypes.ANNOTATION,
            CrystalTypes.BEGIN,
            CrystalTypes.CASE,
            CrystalTypes.CLASS,
            CrystalTypes.DEF,
            CrystalTypes.DO,
            CrystalTypes.ENUM,
            CrystalTypes.FOR,
            CrystalTypes.IF,
            CrystalTypes.LIB,
            CrystalTypes.MACRO,
            CrystalTypes.MODULE,
            CrystalTypes.STRUCT,
            CrystalTypes.UNLESS,
            CrystalTypes.UNTIL,
            CrystalTypes.VERBATIM,
            CrystalTypes.WHILE,
        )
    }

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        val elements = PsiTreeUtil.collectElements(root) { true }

        // Stack-based matching of block-start keywords to 'end'
        data class FoldEntry(val element: PsiElement, val foldStartOffset: Int)
        val startStack = mutableListOf<FoldEntry>()

        var i = 0
        while (i < elements.size) {
            val element = elements[i]
            val tokenType = element.node?.elementType ?: continue
            if (tokenType in BLOCK_START_TOKENS) {
                val foldStartOffset = if (tokenType in CONDITIONAL_KEYWORDS) {
                    // For if/unless/while/until: fold starts after the condition (first NEWLINE/THEN/SEMICOLON)
                    var j = i + 1
                    while (j < elements.size) {
                        val nextType = elements[j].node?.elementType
                        if (nextType == CrystalTypes.NEWLINE || nextType == CrystalTypes.THEN || nextType == CrystalTypes.SEMICOLON) {
                            break
                        }
                        j++
                    }
                    if (j < elements.size) elements[j].textRange.startOffset else element.textRange.endOffset
                } else {
                    element.textRange.endOffset
                }
                startStack.add(FoldEntry(element, foldStartOffset))
            } else if (tokenType == CrystalTypes.END && startStack.isNotEmpty()) {
                val entry = startStack.removeAt(startStack.lastIndex)
                val startLine = document.getLineNumber(entry.foldStartOffset)
                val endLine = document.getLineNumber(element.textRange.endOffset)
                // Only fold if it spans multiple lines
                if (endLine > startLine) {
                    val range = TextRange(entry.foldStartOffset, element.textRange.endOffset)
                    if (range.length > 0) {
                        descriptors.add(FoldingDescriptor(entry.element.node, range))
                    }
                }
            }
            i++
        }

        // Fold multi-line arrays [...] and hashes {...}
        val bracketStack = mutableListOf<PsiElement>()
        for (element in elements) {
            val tokenType = element.node?.elementType ?: continue
            when (tokenType) {
                CrystalTypes.LBRACKET, CrystalTypes.LBRACE -> {
                    bracketStack.add(element)
                }
                CrystalTypes.RBRACKET, CrystalTypes.RBRACE -> {
                    if (bracketStack.isNotEmpty()) {
                        val start = bracketStack.removeAt(bracketStack.lastIndex)
                        val openType = start.node.elementType
                        // Only fold matching pairs: [] or {}
                        val isMatchingPair = (tokenType == CrystalTypes.RBRACKET && openType == CrystalTypes.LBRACKET) ||
                            (tokenType == CrystalTypes.RBRACE && openType == CrystalTypes.LBRACE)
                        if (isMatchingPair) {
                            val startLine = document.getLineNumber(start.textRange.startOffset)
                            val endLine = document.getLineNumber(element.textRange.endOffset)
                            if (endLine > startLine) {
                                val range = TextRange(start.textRange.startOffset, element.textRange.endOffset)
                                if (range.length > 0) {
                                    descriptors.add(FoldingDescriptor(start.node, range))
                                }
                            }
                        }
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
            CrystalTypes.ANNOTATION -> " ... end"
            CrystalTypes.BEGIN -> " ... end"
            CrystalTypes.CASE -> " ... end"
            CrystalTypes.CLASS -> " ... end"
            CrystalTypes.DEF -> " ... end"
            CrystalTypes.DO -> " ... end"
            CrystalTypes.ENUM -> " ... end"
            CrystalTypes.FOR -> " ... end"
            CrystalTypes.IF -> " ... end"
            CrystalTypes.LIB -> " ... end"
            CrystalTypes.MACRO -> " ... end"
            CrystalTypes.MODULE -> " ... end"
            CrystalTypes.STRUCT -> " ... end"
            CrystalTypes.UNLESS -> " ... end"
            CrystalTypes.UNTIL -> " ... end"
            CrystalTypes.VERBATIM -> " ... end"
            CrystalTypes.WHILE -> " ... end"
            CrystalTypes.LBRACKET -> "[ ... ]"
            CrystalTypes.LBRACE -> "{ ... }"
            else -> "..."
        }
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
