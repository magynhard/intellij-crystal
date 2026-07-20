package de.magynhard.crystal.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.CrystalTypes

internal object CrystalBareCallParameterLocator {

    private val STOP_OPERATORS: TokenSet = TokenSet.create(
        CrystalTypes.ASSIGN,
        CrystalTypes.PLUS_ASSIGN, CrystalTypes.MINUS_ASSIGN,
        CrystalTypes.STAR_ASSIGN, CrystalTypes.SLASH_ASSIGN,
        CrystalTypes.PERCENT_ASSIGN, CrystalTypes.AMPERSAND_ASSIGN,
        CrystalTypes.PIPE_ASSIGN, CrystalTypes.CARET_ASSIGN,
        CrystalTypes.DOUBLE_STAR_ASSIGN, CrystalTypes.LSHIFT_ASSIGN,
        CrystalTypes.RSHIFT_ASSIGN, CrystalTypes.OR_OR_ASSIGN,
        CrystalTypes.AND_AND_ASSIGN,
        CrystalTypes.EQ, CrystalTypes.NEQ,
        CrystalTypes.LT, CrystalTypes.GT,
        CrystalTypes.LTE, CrystalTypes.GTE, CrystalTypes.SPACESHIP,
        CrystalTypes.CASE_EQ,
        CrystalTypes.AND_AND, CrystalTypes.OR_OR,
        CrystalTypes.LSHIFT, CrystalTypes.RSHIFT,
        CrystalTypes.ARROW, CrystalTypes.DOUBLE_ARROW,
        CrystalTypes.DOTDOT, CrystalTypes.DOTDOTDOT,
        CrystalTypes.SEMICOLON
    )

    private val STRUCTURAL_KEYWORDS: TokenSet = TokenSet.create(
        CrystalTypes.DEF, CrystalTypes.CLASS, CrystalTypes.MODULE,
        CrystalTypes.END, CrystalTypes.DO,
        CrystalTypes.ABSTRACT, CrystalTypes.STRUCT, CrystalTypes.ENUM,
        CrystalTypes.LIB, CrystalTypes.FUN, CrystalTypes.MACRO,
        CrystalTypes.ANNOTATION
    )

    private const val MAX_BACKTRACK_TOKENS = 200

    internal fun scanBackwardsForBareCall(file: PsiFile, offset: Int): PsiElement? {
        if (offset > 0) {
            val leafAtOffset = file.findElementAt(offset - 1)
            if (leafAtOffset != null) {
                val leafType = leafAtOffset.node?.elementType
                if (leafType == CrystalTypes.IDENTIFIER || leafType == CrystalTypes.CONSTANT) {
                    val nameEnd = leafAtOffset.textRange.endOffset
                    if (offset >= nameEnd) {
                        val fileText = file.text
                        val between = fileText.substring(nameEnd, offset)
                        if (between.isEmpty() || (between.isNotEmpty() && !between.contains('\n') && between.isBlank())) {
                            val prev = PsiTreeUtil.prevLeaf(leafAtOffset)
                            val prevNonWs = if (prev is PsiWhiteSpace || prev?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
                                PsiTreeUtil.prevLeaf(prev)
                            } else prev
                            val prevType = prevNonWs?.node?.elementType
                            if (prevType == null || (!STRUCTURAL_KEYWORDS.contains(prevType) && prevType != CrystalTypes.DOT)) {
                                val stmtEnd = findStatementEndOffset(file, offset)
                                return CrystalParameterInfoHandler.CrystalParameterInfoAnchor(file.manager, leafAtOffset, stmtEnd)
                            }
                            if (prevType == CrystalTypes.DOT) {
                                val stmtEnd = findStatementEndOffset(file, offset)
                                return CrystalParameterInfoHandler.CrystalParameterInfoAnchor(file.manager, leafAtOffset, stmtEnd)
                            }
                        }
                    }
                }
            }
        }

        val leaves = mutableListOf<PsiElement>()
        var current: PsiElement? = if (offset > 0) file.findElementAt(offset - 1) else return null
        if (current == null && offset > 1) current = file.findElementAt(offset - 2)
        if (current == null) return null

        var depth = 0
        var tokenCount = 0

        while (current != null && tokenCount < MAX_BACKTRACK_TOKENS) {
            tokenCount++
            val type = current.node?.elementType

            when {
                current is PsiWhiteSpace || type == CrystalTokenTypes.WHITE_SPACE -> {
                    if (current.text.contains('\n') && depth == 0) break
                    leaves.add(current)
                }

                type == CrystalTypes.RPAREN || type == CrystalTypes.RBRACKET || type == CrystalTypes.RBRACE -> {
                    depth++
                    leaves.add(current)
                }

                type == CrystalTypes.LPAREN || type == CrystalTypes.LBRACKET || type == CrystalTypes.LBRACE -> {
                    if (depth > 0) {
                        depth--
                        leaves.add(current)
                    } else {
                        break
                    }
                }

                depth == 0 -> {
                    when {
                        type == CrystalTypes.NEWLINE -> break
                        STOP_OPERATORS.contains(type) -> break
                        STRUCTURAL_KEYWORDS.contains(type) -> break
                        else -> leaves.add(current)
                    }
                }

                else -> leaves.add(current)
            }

            current = PsiTreeUtil.prevLeaf(current)
        }

        if (leaves.isEmpty()) return null
        leaves.reverse()

        val nameToken = findMethodNameInLeaves(leaves, file, offset) ?: return null
        val stmtEnd = findStatementEndOffset(file, offset)
        return CrystalParameterInfoHandler.CrystalParameterInfoAnchor(file.manager, nameToken, stmtEnd)
    }

    internal fun findBrokenParenArgsHolder(
        file: PsiFile,
        offset: Int,
        findArgsParent: (PsiElement?) -> PsiElement?
    ): PsiElement? {
        val lparenOffset = findUnmatchedLparen(file.text, offset)
        if (lparenOffset < 0) return null

        val lparenElement = file.findElementAt(lparenOffset) ?: return null
        val callArgs = findArgsParent(lparenElement)
        if (callArgs != null) return callArgs

        val parent = lparenElement.parent
        if (parent != null && parent !is PsiFile) return parent

        val nameToken = findIdentifierBeforeLparen(file, lparenOffset) ?: return null
        val stmtEnd = findStatementEndOffset(file, offset)
        return CrystalParameterInfoHandler.CrystalParameterInfoAnchor(
            file.manager, nameToken, stmtEnd, lparenOffset
        )
    }

    internal fun findBrokenArgumentsStart(text: String, offset: Int): Int? {
        val lparenOffset = findUnmatchedLparen(text, offset)
        return if (lparenOffset >= 0) lparenOffset + 1 else null
    }

    private fun findUnmatchedLparen(text: String, offset: Int): Int {
        var depth = 0
        var i = (offset - 1).coerceAtMost(text.length - 1)
        while (i >= 0) {
            when (text[i]) {
                ')' -> depth++
                '(' -> {
                    if (depth == 0) return i
                    depth--
                }
                '\n' -> if (depth == 0) return -1
            }
            i--
        }
        return -1
    }

    private fun findMethodNameInLeaves(
        leaves: List<PsiElement>,
        file: PsiFile,
        cursorOffset: Int
    ): PsiElement? {
        for (i in leaves.indices.reversed()) {
            val type = leaves[i].node?.elementType

            if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
                val next = leaves.getOrNull(i + 1)
                val nextIsWhitespace = next is PsiWhiteSpace
                    || next?.node?.elementType == CrystalTokenTypes.WHITE_SPACE

                if (!nextIsWhitespace) continue

                val prev = leaves.getOrNull(i - 1)
                val prevType = prev?.node?.elementType
                if (prevType == CrystalTypes.COMMA) continue
                if (prevType == CrystalTypes.COLON) continue

                val nameEnd = leaves[i].textRange.endOffset
                val textBetween = file.text.substring(nameEnd, cursorOffset)
                if (textBetween.contains('(')) continue

                return leaves[i]
            }
        }

        return null
    }

    private fun findStatementEndOffset(file: PsiFile, cursorOffset: Int): Int {
        val text = file.text
        val newlinePos = text.indexOf('\n', cursorOffset)
        val end = if (newlinePos >= 0) newlinePos else text.length
        return maxOf(end, cursorOffset + 1).coerceAtMost(text.length)
    }

    private fun findIdentifierBeforeLparen(file: PsiFile, lparenOffset: Int): PsiElement? {
        var leaf = if (lparenOffset > 0) file.findElementAt(lparenOffset - 1) else return null
        while (leaf is PsiWhiteSpace || leaf?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        if (leaf == null) return null
        val type = leaf.node?.elementType
        return if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) leaf else null
    }
}
