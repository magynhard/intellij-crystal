package de.magynhard.crystal.navigation

import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Provides parameter info (Ctrl+P) for Crystal method calls.
 * Supports:
 * - Parenthesized calls: foo(a, b)
 * - Bare (parenthesis-free) calls: foo a, b
 * - Dot-calls with parens: obj.method(a, b)
 * - Dot-calls bare: obj.method a, b
 * - Class method calls: Foo.bar(a, b)
 *
 * Handles cursor positions after comma with/without whitespace,
 * including incomplete expressions where the PSI tree is broken,
 * and bare calls where no argument has been typed yet.
 */
class CrystalParameterInfoHandler : ParameterInfoHandler<PsiElement, CrystalMethodDefinition> {

    /**
     * Synthetic anchor element for parameter info that provides an extended text range
     * covering from the method name token to the end of the statement (or cursor position).
     * This ensures IntelliJ's ParameterInfoController considers the cursor "inside" the anchor.
     */
    class CrystalParameterInfoAnchor(
        psiManager: PsiManager,
        /** The IDENTIFIER/CONSTANT leaf token representing the method name. */
        val nameToken: PsiElement,
        /** End offset of the call statement (typically next newline or file end). */
        private val endOffset: Int,
        /** The offset of the unmatched LPAREN, or -1 for bare calls. */
        val lparenOffset: Int = -1
    ) : LightElement(psiManager, CrystalLanguage) {
        override fun getTextRange(): TextRange = TextRange(nameToken.textRange.startOffset, endOffset)
        override fun getContainingFile(): PsiFile = nameToken.containingFile
        override fun toString(): String = "CrystalParameterInfoAnchor(${nameToken.text})"
        override fun isValid(): Boolean = nameToken.isValid
        override fun getText(): String {
            val file = nameToken.containingFile ?: return nameToken.text
            val fileText = file.text ?: return nameToken.text
            val start = nameToken.textRange.startOffset.coerceIn(0, fileText.length)
            val end = endOffset.coerceIn(start, fileText.length)
            return fileText.substring(start, end)
        }
        override fun getTextOffset(): Int = nameToken.textOffset
        override fun getStartOffsetInParent(): Int = 0

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CrystalParameterInfoAnchor) return false
            return nameToken.textOffset == other.nameToken.textOffset
                && nameToken.containingFile == other.nameToken.containingFile
                && lparenOffset == other.lparenOffset
        }

        override fun hashCode(): Int {
            var result = nameToken.textOffset
            result = 31 * result + lparenOffset
            return result
        }
    }

    companion object {
        /** Operators that stop the backwards scan (bare-call boundary). */
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

        /** Structural keywords that mark statement boundaries. */
        private val STRUCTURAL_KEYWORDS: TokenSet = TokenSet.create(
            CrystalTypes.DEF, CrystalTypes.CLASS, CrystalTypes.MODULE,
            CrystalTypes.END, CrystalTypes.DO,
            CrystalTypes.ABSTRACT, CrystalTypes.STRUCT, CrystalTypes.ENUM,
            CrystalTypes.LIB, CrystalTypes.FUN, CrystalTypes.MACRO,
            CrystalTypes.ANNOTATION
        )

        /** Tokens allowed as "arguments" between method name and cursor in bare calls. */
        private val BARE_ARG_TOKENS: TokenSet = TokenSet.create(
            CrystalTypes.IDENTIFIER, CrystalTypes.CONSTANT,
            CrystalTypes.INTEGER_LITERAL, CrystalTypes.FLOAT_LITERAL,
            CrystalTypes.STRING_LITERAL,
            CrystalTypes.STRING_INTERPOLATION_BEGIN, CrystalTypes.STRING_INTERPOLATION_END,
            CrystalTypes.CHAR_LITERAL, CrystalTypes.SYMBOL_LITERAL,
            CrystalTypes.TRUE, CrystalTypes.FALSE, CrystalTypes.NIL,
            CrystalTypes.COMMA, CrystalTypes.COLON,
            CrystalTypes.LPAREN, CrystalTypes.RPAREN,
            CrystalTypes.LBRACKET, CrystalTypes.RBRACKET,
            CrystalTypes.LBRACE, CrystalTypes.RBRACE,
            CrystalTypes.PLUS, CrystalTypes.MINUS, CrystalTypes.STAR,
            CrystalTypes.SLASH, CrystalTypes.PERCENT,
            CrystalTypes.AMPERSAND, CrystalTypes.PIPE, CrystalTypes.CARET,
            CrystalTypes.TILDE, CrystalTypes.DOUBLE_STAR, CrystalTypes.BANG,
            CrystalTypes.QUESTION, CrystalTypes.DOT, CrystalTypes.DOUBLE_COLON,
            CrystalTypes.HASH
        )

        /** Max tokens to scan backwards (performance limit). */
        private const val MAX_BACKTRACK_TOKENS = 200
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val argsHolder = findArgsHolder(context.file, context.offset) ?: return null
        val methodName = findMethodNameForArgs(argsHolder) ?: return null

        val project = context.project
        val scope = GlobalSearchScope.allScope(project)

        var methods = StubIndex.getElements(
            CrystalMethodIndex.KEY,
            methodName,
            project,
            scope,
            CrystalMethodDefinition::class.java
        ).toTypedArray()

        // Filter by receiver type for DOT-calls on CONSTANT receivers.
        // This prevents stdlib methods like ENV.fetch from showing params of Hash#fetch etc.
        val receiverName = findReceiverNameFromSiblings(argsHolder)
        if (receiverName != null && receiverName.isNotEmpty() && receiverName[0].isUpperCase()) {
            methods = methods.filter { method ->
                findEnclosingTypeName(method) == receiverName
            }.toTypedArray()
            if (methods.isEmpty()) return null
        }

        // Special case: "new" on a class → resolve to "initialize" parameters
        if (methods.isEmpty() && methodName == "new") {
            val className = findClassNameBeforeNew(argsHolder)
            if (className != null) {
                val initMethod = CrystalCompletionHelper.getInitializeMethod(className, project, argsHolder.containingFile)
                if (initMethod != null) {
                    methods = arrayOf(initMethod)
                }
            }
        }

        if (methods.isEmpty()) return null
        context.itemsToShow = methods
        return argsHolder
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
        var result = findArgsHolder(context.file, context.offset)
        if (result == null && context.offset > 0) {
            // IntelliJ sometimes calls with offset-1; try offset+1
            result = findArgsHolder(context.file, context.offset + 1)
        }
        return result
    }

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        val offset = context.offset
        val index = computeCurrentParameterIndex(parameterOwner, offset)
        context.setCurrentParameter(index)
    }

    override fun updateUI(method: CrystalMethodDefinition?, context: ParameterInfoUIContext) {
        if (method == null) {
            context.isUIComponentEnabled = false
            return
        }

        val paramList = method.parameterList?.parameterList ?: emptyList()
        if (paramList.isEmpty()) {
            context.setupUIComponentPresentation(
                "<no parameters>",
                -1, -1,
                false, false, false,
                context.defaultParameterColor
            )
            return
        }

        val params = paramList.map { it.text.trim() }
        val text = params.joinToString(", ")

        val currentIndex = context.currentParameterIndex
        var startHighlight = -1
        var endHighlight = -1

        if (currentIndex in params.indices) {
            startHighlight = params.take(currentIndex).sumOf { it.length + 2 }
            endHighlight = startHighlight + params[currentIndex].length
        }

        context.setupUIComponentPresentation(
            text,
            startHighlight, endHighlight,
            false, false, false,
            context.defaultParameterColor
        )
    }

    // ==================== Anchor Search ====================

    /**
     * Finds the argument-list PSI node that contains or is adjacent to the given offset.
     *
     * Search order:
     * 1. PSI-based: CrystalCallArgs or CrystalBareArgumentList at offset/offset-1
     * 2. Unmatched LPAREN (for broken PSI in paren-calls)
     * 3. Bare-call backtracking (for bare calls with no/incomplete args)
     * 4. RPAREN edge case
     */
    fun findArgsHolder(file: PsiFile, offset: Int): PsiElement? {
        // Primary: try element at offset
        val elementAtOffset = file.findElementAt(offset)
        val primary = findArgsParent(elementAtOffset)
        if (primary != null) return primary

        // Fallback A: try offset - 1
        if (offset > 0) {
            val elementBefore = file.findElementAt(offset - 1)
            val fallbackA = findArgsParent(elementBefore)
            if (fallbackA != null) return fallbackA
        }

        // Fallback B: text-based — find the unmatched LPAREN going backwards
        val text = file.text
        val lparenOffset = findUnmatchedLparen(text, offset)
        if (lparenOffset >= 0) {
            val lparenElement = file.findElementAt(lparenOffset)
            if (lparenElement != null) {
                val callArgs = findArgsParent(lparenElement)
                if (callArgs != null) return callArgs
                val parent = lparenElement.parent
                if (parent != null && parent !is PsiFile) return parent
                // Parent is PsiFile (broken PSI): create synthetic anchor by finding method name before LPAREN
                val nameToken = findIdentifierBeforeLparen(file, lparenOffset)
                if (nameToken != null) {
                    val stmtEnd = findStatementEndOffset(file, offset)
                    return CrystalParameterInfoAnchor(
                        file.manager, nameToken, stmtEnd, lparenOffset
                    )
                }
            }
        }

        // Fallback C: bare-call backtracking
        val bareCallAnchor = scanBackwardsForBareCall(file, offset)
        if (bareCallAnchor != null) return bareCallAnchor

        // Fallback D: element is RPAREN
        if (elementAtOffset != null && elementAtOffset.node?.elementType == CrystalTypes.RPAREN) {
            val parent = elementAtOffset.parent
            if (parent is CrystalCallArgs) return parent
            return parent
        }

        return null
    }

    private fun findArgsParent(element: PsiElement?): PsiElement? {
        if (element == null) return null
        val callArgs = PsiTreeUtil.getParentOfType(element, CrystalCallArgs::class.java, false)
        if (callArgs != null) return callArgs
        val bareArgs = PsiTreeUtil.getParentOfType(element, CrystalBareArgumentList::class.java, false)
        if (bareArgs != null) return bareArgs
        if (element is CrystalCallArgs) return element
        if (element is CrystalBareArgumentList) return element
        return null
    }

    /**
     * Scans backwards from offset to find the position of an unmatched '(' character.
     */
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
                '\n' -> if (depth == 0) return -1 // Don't cross line boundaries for paren search
            }
            i--
        }
        return -1
    }

    // ==================== Bare-Call Backtracking ====================

    /**
     * Scans backwards from the cursor position to detect a bare method call.
     *
     * Returns the IDENTIFIER/CONSTANT token of the method name wrapped in a
     * CrystalParameterInfoAnchor (with extended range), or null if no bare call is detected.
     *
     * Algorithm:
     * 1. Walk backwards from cursor, collecting leaf tokens until a statement boundary.
     * 2. Analyze collected tokens from left to right (source order).
     * 3. First IDENTIFIER/CONSTANT followed by whitespace (without LPAREN) = method name.
     */
    fun scanBackwardsForBareCall(file: PsiFile, offset: Int): PsiElement? {
        // Quick check: if element at offset-1 is an IDENTIFIER/CONSTANT and the text
        // between its end and offset is only whitespace (no newline), it might be a bare call.
        // This handles the case where IntelliJ calls with offset slightly before cursor.
        if (offset > 0) {
            val leafAtOffset = file.findElementAt(offset - 1)
            if (leafAtOffset != null) {
                val leafType = leafAtOffset.node?.elementType
                if (leafType == CrystalTypes.IDENTIFIER || leafType == CrystalTypes.CONSTANT) {
                    val nameEnd = leafAtOffset.textRange.endOffset
                    if (offset >= nameEnd) {
                        // Cursor is at or after end of identifier — check if only whitespace follows
                        val fileText = file.text
                        val between = fileText.substring(nameEnd, offset)
                        if (between.isEmpty() || (between.isNotEmpty() && !between.contains('\n') && between.isBlank())) {
                            // Verify not preceded by structural keyword (def, class, etc.)
                            val prev = PsiTreeUtil.prevLeaf(leafAtOffset)
                            val prevNonWs = if (prev is PsiWhiteSpace || prev?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
                                PsiTreeUtil.prevLeaf(prev!!)
                            } else prev
                            val prevType = prevNonWs?.node?.elementType
                            if (prevType == null || (!STRUCTURAL_KEYWORDS.contains(prevType) && prevType != CrystalTypes.DOT)) {
                                // It's a bare call with no args yet
                                val stmtEnd = findStatementEndOffset(file, offset)
                                return CrystalParameterInfoAnchor(file.manager, leafAtOffset, stmtEnd)
                            }
                            if (prevType == CrystalTypes.DOT) {
                                // Dot-call bare: obj.method <caret>
                                val stmtEnd = findStatementEndOffset(file, offset)
                                return CrystalParameterInfoAnchor(file.manager, leafAtOffset, stmtEnd)
                            }
                        }
                    }
                }
            }
        }

        // Collect leaf tokens going backwards from cursor
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
                // Whitespace: check for newline at top level (statement boundary)
                current is PsiWhiteSpace || type == CrystalTokenTypes.WHITE_SPACE -> {
                    if (current.text.contains('\n') && depth == 0) {
                        // Statement boundary reached — stop collecting
                        break
                    }
                    leaves.add(current)
                }

                // Closing brackets: increase depth (we're going backwards)
                type == CrystalTypes.RPAREN || type == CrystalTypes.RBRACKET || type == CrystalTypes.RBRACE -> {
                    depth++
                    leaves.add(current)
                }

                // Opening brackets: decrease depth
                type == CrystalTypes.LPAREN || type == CrystalTypes.LBRACKET || type == CrystalTypes.LBRACE -> {
                    if (depth > 0) {
                        depth--
                        leaves.add(current)
                    } else {
                        // Unmatched opening bracket — stop
                        break
                    }
                }

                // At top level: check stop conditions
                depth == 0 -> {
                    when {
                        type == CrystalTypes.NEWLINE -> break // Statement boundary
                        STOP_OPERATORS.contains(type) -> break
                        STRUCTURAL_KEYWORDS.contains(type) -> break
                        else -> leaves.add(current)
                    }
                }

                // Inside brackets: collect everything
                else -> leaves.add(current)
            }

            current = PsiTreeUtil.prevLeaf(current)
        }

        if (leaves.isEmpty()) return null

        // Reverse to get source order (left to right)
        leaves.reverse()

        // Find method name: first IDENTIFIER/CONSTANT that is followed by whitespace
        // and not preceded by anything that makes it NOT a method name (like another identifier
        // without DOT separator).
        // Also handle dot-calls: ... DOT IDENTIFIER WHITESPACE ...
        val nameToken = findMethodNameInLeaves(leaves, file, offset) ?: return null
        val stmtEnd = findStatementEndOffset(file, offset)
        return CrystalParameterInfoAnchor(file.manager, nameToken, stmtEnd)
    }

    /**
     * Analyzes collected leaves (in source order) to find the method name.
     * Returns the IDENTIFIER/CONSTANT PsiElement that is the method name, or null.
     */
    private fun findMethodNameInLeaves(leaves: List<PsiElement>, file: PsiFile, cursorOffset: Int): PsiElement? {
        // Pattern we're looking for:
        // [DOT] IDENTIFIER WHITESPACE [args...]
        // The IDENTIFIER followed by whitespace is the method name.

        for (i in leaves.indices) {
            val type = leaves[i].node?.elementType

            if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
                // Check if next token is whitespace (meaning bare call)
                val next = leaves.getOrNull(i + 1)
                val nextIsWhitespace = next is PsiWhiteSpace
                    || next?.node?.elementType == CrystalTokenTypes.WHITE_SPACE

                if (!nextIsWhitespace) continue

                // Check that the whitespace doesn't contain a newline (same-line call)
                // Actually for bare calls the space between name and args must NOT have newline
                // But we already stopped at newline during collection, so any whitespace here is same-line

                // Verify it's not preceded by something that disqualifies it
                val prev = leaves.getOrNull(i - 1)
                val prevType = prev?.node?.elementType

                // If preceded by DOT → this is a dot-call method name (valid)
                // If preceded by nothing (start of collected tokens) → statement start (valid)
                // If preceded by whitespace → could be start of statement after newline break (valid)
                // If preceded by IDENTIFIER/CONSTANT without DOT → ambiguous; the FIRST one is the call
                //   Actually we want the FIRST qualifying IDENTIFIER, so we return immediately

                // Disqualify if preceded by COMMA (this would make it an argument, not method name)
                if (prevType == CrystalTypes.COMMA) continue

                // Disqualify if preceded by COLON (named arg label)
                if (prevType == CrystalTypes.COLON) continue

                // Accept: verify there's no LPAREN between this identifier and cursor
                // (if there's an LPAREN, it's a paren-call handled elsewhere)
                val nameEnd = leaves[i].textRange.endOffset
                val textBetween = file.text.substring(nameEnd, cursorOffset)
                if (textBetween.contains('(')) continue

                return leaves[i]
            }
        }

        return null
    }

    // ==================== Method Name Resolution ====================

    /**
     * Resolves the method name for a given args holder by examining its PSI context.
     */
    fun findMethodNameForArgs(argsHolder: PsiElement): String? {
        // Case: synthetic anchor from bare-call or broken paren-call
        if (argsHolder is CrystalParameterInfoAnchor) {
            return argsHolder.nameToken.text
        }

        // Case: bare-call backtracking returned the IDENTIFIER/CONSTANT leaf as anchor (legacy)
        val holderType = argsHolder.node?.elementType
        if (holderType == CrystalTypes.IDENTIFIER || holderType == CrystalTypes.CONSTANT) {
            return argsHolder.text
        }

        val parent = argsHolder.parent

        // Case: proper CrystalCallArgs/BareArgumentList inside method_call_expression
        if (argsHolder is CrystalCallArgs || argsHolder is CrystalBareArgumentList) {
            if (parent is CrystalMethodCallExpression) {
                return extractIdentifierFromCallExpression(parent)
            }
            if (parent is CrystalBareMethodCallExpression) {
                return extractIdentifierFromCallExpression(parent)
            }
            val fromSiblings = findMethodNameFromSiblings(argsHolder)
            if (fromSiblings != null) return fromSiblings
        }

        // Case: broken PSI
        val fromSiblings = findMethodNameFromSiblings(argsHolder)
        if (fromSiblings != null) return fromSiblings

        if (argsHolder is CrystalMethodCallExpression) {
            return extractIdentifierFromCallExpression(argsHolder)
        }

        // Look for IDENTIFIER child before LPAREN
        var child = argsHolder.firstChild
        var lastIdentifier: String? = null
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
                lastIdentifier = child.text
            }
            if (type == CrystalTypes.LPAREN) {
                return lastIdentifier
            }
            child = child.nextSibling
        }

        // Try parent as method_call_expression
        if (parent is CrystalMethodCallExpression) {
            return extractIdentifierFromCallExpression(parent)
        }
        if (parent is CrystalBareMethodCallExpression) {
            return extractIdentifierFromCallExpression(parent)
        }

        return null
    }

    private fun extractIdentifierFromCallExpression(expression: PsiElement): String? {
        // For dot-calls (Foo.bar, obj.method): return the IDENTIFIER after the last DOT
        var child = expression.firstChild
        var foundDot = false
        var lastNameBeforeDot: String? = null
        while (child != null) {
            val type = child.node?.elementType
            if (type == CrystalTypes.DOT) {
                foundDot = true
            } else if (foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                return child.text
            } else if (!foundDot && (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT)) {
                lastNameBeforeDot = child.text
            }
            child = child.nextSibling
        }
        return lastNameBeforeDot
    }

    /**
     * For dot-calls: look at siblings before the args holder to find DOT + IDENTIFIER.
     */
    private fun findMethodNameFromSiblings(argsHolder: PsiElement): String? {
        var sibling = argsHolder.prevSibling
        while (sibling is PsiWhiteSpace) {
            sibling = sibling.prevSibling
        }
        if (sibling == null) return null

        val type = sibling.node?.elementType
        if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
            var beforeName = sibling.prevSibling
            while (beforeName is PsiWhiteSpace) {
                beforeName = beforeName.prevSibling
            }
            if (beforeName?.node?.elementType == CrystalTypes.DOT) {
                return sibling.text
            }
            return sibling.text
        }
        return null
    }

    /**
     * For dot-calls: find the receiver name (e.g., "ENV" in ENV.fetch(...)).
     */
    private fun findReceiverNameFromSiblings(argsHolder: PsiElement): String? {
        var sibling = argsHolder.prevSibling
        while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
        if (sibling == null) return null

        val methodNameType = sibling.node?.elementType
        if (methodNameType != CrystalTypes.IDENTIFIER && methodNameType != CrystalTypes.CONSTANT) return null

        var beforeName = sibling.prevSibling
        while (beforeName is PsiWhiteSpace) beforeName = beforeName.prevSibling
        if (beforeName?.node?.elementType != CrystalTypes.DOT) return null

        var receiver = beforeName.prevSibling
        while (receiver is PsiWhiteSpace) receiver = receiver.prevSibling
        if (receiver == null) return null

        if (receiver.node?.elementType == CrystalTypes.CONSTANT) return receiver.text
        return null
    }

    private fun findEnclosingTypeName(method: CrystalMethodDefinition): String? {
        var parent = method.parent
        while (parent != null) {
            val typeName = when (parent) {
                is CrystalClassDefinition -> parent.typeName
                is CrystalModuleDefinition -> parent.typeName
                is CrystalStructDefinition -> parent.typeName
                else -> null
            }
            if (typeName != null) {
                var child = typeName.firstChild
                while (child != null) {
                    if (child.node?.elementType == CrystalTypes.CONSTANT) {
                        return child.text
                    }
                    child = child.nextSibling
                }
            }
            parent = parent.parent
        }
        return null
    }

    // ==================== Parameter Index Computation ====================

    /**
     * Computes which parameter index the cursor is currently at.
     */
    fun computeCurrentParameterIndex(argsHolder: PsiElement, offset: Int): Int {
        // Case: synthetic anchor
        if (argsHolder is CrystalParameterInfoAnchor) {
            val fileText = argsHolder.containingFile?.text ?: return 0
            val argsStart = if (argsHolder.lparenOffset >= 0) {
                argsHolder.lparenOffset + 1 // skip '('
            } else {
                argsHolder.nameToken.textRange.endOffset // bare call: after method name
            }
            return countTopLevelCommas(fileText, argsStart, offset)
        }

        // Case: bare-call backtracking anchor (IDENTIFIER/CONSTANT leaf = method name) — legacy
        val holderType = argsHolder.node?.elementType
        if (holderType == CrystalTypes.IDENTIFIER || holderType == CrystalTypes.CONSTANT) {
            // Count commas in file text between method name end and cursor
            val fileText = argsHolder.containingFile?.text ?: return 0
            val argsStart = argsHolder.textRange.endOffset
            return countTopLevelCommas(fileText, argsStart, offset)
        }

        val holderText = argsHolder.text
        val startOffset = argsHolder.textRange.startOffset
        val relativeOffset = (offset - startOffset).coerceIn(0, holderText.length)

        val startPos: Int
        if (argsHolder is CrystalCallArgs) {
            startPos = 1 // skip '('
        } else if (argsHolder is CrystalBareArgumentList) {
            startPos = 0
        } else {
            // Broken PSI fallback: find the unmatched LPAREN relative to cursor in file text
            val fileText = argsHolder.containingFile?.text ?: holderText
            val lparenFileOffset = findUnmatchedLparen(fileText, offset)
            if (lparenFileOffset >= 0) {
                return countTopLevelCommas(fileText, lparenFileOffset + 1, offset)
            }
            val parenPos = holderText.indexOf('(')
            startPos = if (parenPos >= 0 && parenPos < relativeOffset) parenPos + 1 else 0
        }

        return countTopLevelCommas(holderText, startPos, relativeOffset)
    }

    private fun countTopLevelCommas(text: String, from: Int, to: Int): Int {
        var index = 0
        var depth = 0
        for (i in from until to.coerceAtMost(text.length)) {
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ',' -> if (depth == 0) index++
            }
        }
        return index
    }

    // ==================== Helper Functions ====================

    /**
     * Finds the end offset of the current statement (next newline or file end).
     * Guarantees the returned offset is strictly greater than cursorOffset so that
     * TextRange.contains(cursorOffset) is true.
     */
    private fun findStatementEndOffset(file: PsiFile, cursorOffset: Int): Int {
        val text = file.text
        val newlinePos = text.indexOf('\n', cursorOffset)
        val end = if (newlinePos >= 0) newlinePos else text.length
        // Ensure range end is strictly greater than cursor so contains() works
        return maxOf(end, cursorOffset + 1).coerceAtMost(text.length)
    }

    /**
     * Finds the IDENTIFIER/CONSTANT token immediately before an LPAREN at the given offset.
     * Skips whitespace and handles dot-calls (e.g., obj.method( or Foo.bar().
     */
    private fun findIdentifierBeforeLparen(file: PsiFile, lparenOffset: Int): PsiElement? {
        var leaf = if (lparenOffset > 0) file.findElementAt(lparenOffset - 1) else return null
        // Skip whitespace
        while (leaf is PsiWhiteSpace || leaf?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
            leaf = PsiTreeUtil.prevLeaf(leaf!!)
        }
        if (leaf == null) return null
        val type = leaf.node?.elementType
        if (type == CrystalTypes.IDENTIFIER || type == CrystalTypes.CONSTANT) {
            return leaf
        }
        return null
    }

    /**
     * For "ClassName.new(...)" — finds the class name (CONSTANT) before ".new".
     * Returns the class name string, or null if not a class constructor call.
     */
    private fun findClassNameBeforeNew(argsHolder: PsiElement): String? {
        // Find the "new" token — look backwards from the args holder
        val newToken = when {
            argsHolder is CrystalParameterInfoAnchor -> argsHolder.nameToken
            argsHolder is CrystalCallArgs || argsHolder is CrystalBareArgumentList -> {
                // Find IDENTIFIER("new") before the args
                var sibling: PsiElement? = argsHolder.prevSibling
                while (sibling is PsiWhiteSpace) sibling = sibling.prevSibling
                if (sibling?.node?.elementType == CrystalTypes.IDENTIFIER && sibling.text == "new") {
                    sibling
                } else null
            }
            else -> null
        } ?: return null

        // Look backwards: newToken -> DOT -> CONSTANT
        var prev = PsiTreeUtil.prevLeaf(newToken)
        while (prev is PsiWhiteSpace || prev?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
            prev = PsiTreeUtil.prevLeaf(prev!!)
        }
        if (prev?.node?.elementType != CrystalTypes.DOT) return null

        prev = PsiTreeUtil.prevLeaf(prev!!)
        while (prev is PsiWhiteSpace || prev?.node?.elementType == CrystalTokenTypes.WHITE_SPACE) {
            prev = PsiTreeUtil.prevLeaf(prev!!)
        }
        // Handle both raw CONSTANT tokens and wrapped elements (e.g. CrystalVariableReferenceImpl)
        if (prev?.node?.elementType == CrystalTypes.CONSTANT) {
            return prev.text
        }
        val constantChild = prev?.node?.findChildByType(CrystalTypes.CONSTANT)
        if (constantChild != null) {
            return constantChild.text
        }
        return null
    }
}
