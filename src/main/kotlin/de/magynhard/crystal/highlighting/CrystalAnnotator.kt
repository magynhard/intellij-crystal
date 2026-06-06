package de.magynhard.crystal.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.*

/**
 * Semantic highlighter for Crystal.
 *
 * CONSTANT and IDENTIFIER tokens are NOT highlighted by the lexer (EMPTY_KEYS).
 * All context-sensitive coloring happens here, giving the annotator full control
 * over which color each token gets — no conflicts with lexer-level highlighting.
 */
class CrystalAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val elementType = element.node.elementType

        // Handle leaf tokens — CONSTANT and IDENTIFIER
        if (elementType == CrystalTypes.CONSTANT) {
            annotateConstantToken(element, holder)
            return
        }
        if (elementType == CrystalTypes.IDENTIFIER) {
            annotateIdentifierToken(element, holder)
            return
        }

        // Highlight $0, $1, etc. inside asm template strings
        if (elementType == CrystalTypes.STRING_LITERAL) {
            annotateAsmOperandReferences(element, holder)
            return
        }

        // Highlight TODO/FIXME/NOTE in comments
        if (elementType == CrystalTypes.LINE_COMMENT) {
            annotateTodoComment(element, holder)
            return
        }

        // Highlight regex sub-patterns inside regex literals
        if (elementType == CrystalTypes.REGEX_LITERAL) {
            annotateRegexLiteral(element, holder)
            return
        }

        // Validate heredoc pairs: start must have matching end delimiter
        if (elementType == CrystalTypes.HEREDOC_START) {
            validateHeredocStart(element, holder)
            return
        }

        // Validate heredoc end delimiter indent
        if (elementType == CrystalTypes.HEREDOC_END) {
            validateHeredocEnd(element, holder)
            return
        }


    }

    /**
     * Context-sensitive highlighting for CONSTANT tokens:
     * - Inside type_name (class/module/struct/enum definition) → CLASS_DECLARATION
     * - Inside method_name (def self.Foo) → FUNCTION_DECLARATION
     * - Everywhere else → CONSTANT (type references, standalone constants)
     */
    private fun annotateConstantToken(element: PsiElement, holder: AnnotationHolder) {
        val parent = element.parent
        if (parent is CrystalTypeName) {
            apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
        } else if (parent is CrystalMethodName) {
            apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
        } else {
            apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
        }
    }

    /**
     * Context-sensitive highlighting for IDENTIFIER tokens:
     * - Inside method_name (def greet, def self.greet) → FUNCTION_DECLARATION
     * - Inside parameter definition → PARAMETER
     * - Usage of a parameter inside method body → PARAMETER
     * - Everything else → IDENTIFIER (default)
     */
    private fun annotateIdentifierToken(element: PsiElement, holder: AnnotationHolder) {
        val parent = element.parent

        // Method/macro name definition
        if (parent is CrystalMethodName) {
            apply(holder, element, CrystalSyntaxHighlighter.CONSTANT)
            return
        }

        // Parameter definition
        if (parent is CrystalParameter) {
            apply(holder, element, CrystalSyntaxHighlighter.PARAMETER)
            return
        }

        // Built-in macros (getter, setter, property, etc.) highlighted as keywords
        if (isBuiltinMacroCall(element)) {
            apply(holder, element, CrystalSyntaxHighlighter.KEYWORD)
            return
        }

        // Hash key in shorthand syntax (name: value) or named argument (key: value) → Symbol color (like Ruby)
        if (isHashShorthandKey(element) || isNamedArgumentKey(element)) {
            apply(holder, element, CrystalSyntaxHighlighter.SYMBOL)
            return
        }

        // Parameter usage inside method/macro body
        if (isParameterUsage(element)) {
            apply(holder, element, CrystalSyntaxHighlighter.PARAMETER)
            return
        }

        // Default: normal identifier
        apply(holder, element, CrystalSyntaxHighlighter.IDENTIFIER)
    }

    /**
     * Check if an IDENTIFIER token is a hash key in shorthand syntax (name: value).
     * In this form, the key acts like a symbol and should be colored accordingly.
     */
    private fun isHashShorthandKey(element: PsiElement): Boolean {
        // Structure: IDENTIFIER → CrystalVariableReference → CrystalExpression → CrystalHashEntry
        val varRef = element.parent ?: return false
        if (varRef !is CrystalVariableReference) return false
        val expr = varRef.parent ?: return false
        if (expr !is CrystalExpression) return false
        val hashEntry = expr.parent ?: return false
        if (hashEntry !is CrystalHashEntry) return false

        // Must be the first expression (the key, not the value)
        val expressions = hashEntry.expressionList
        if (expressions.isEmpty() || expressions[0] != expr) return false

        // Must use COLON separator (shorthand), not DOUBLE_ARROW (rocket =>)
        val colonNode = hashEntry.node.findChildByType(CrystalTypes.COLON)
        return colonNode != null
    }

    /**
     * Check if an IDENTIFIER token is a named argument key (key: value).
     * Named arguments act like symbols and should be colored accordingly.
     */
    private fun isNamedArgumentKey(element: PsiElement): Boolean {
        val parent = element.parent ?: return false
        // In argument or bare_argument, the IDENTIFIER is a direct child followed by COLON
        if (parent !is CrystalArgument && parent !is CrystalBareArgument) return false

        // Check that this IDENTIFIER is followed by a COLON sibling
        val nextSibling = element.nextSibling ?: return false
        return nextSibling.node.elementType == CrystalTypes.COLON
    }

    /**
     * Check if an IDENTIFIER is a built-in macro call (getter, setter, property, etc.)
     * These are highlighted as keywords since they act like language constructs.
     */
    private fun isBuiltinMacroCall(element: PsiElement): Boolean {
        val text = element.text
        if (text !in BUILTIN_MACROS) return false
        // Must be a statement-level call (first token on a logical line / inside class body)
        val parent = element.parent
        // Bare method call expression or direct child of statement list
        return parent is CrystalBareMethodCallExpression ||
               (parent?.parent is CrystalStatementList) ||
               (parent?.parent is CrystalClassBody)
    }

    /**
     * Check if an IDENTIFIER token is a usage of a method/macro parameter.
     */
    private fun isParameterUsage(element: PsiElement): Boolean {
        val name = element.text
        if (name.isBlank()) return false

        // Find enclosing method or macro
        val methodDef = PsiTreeUtil.getParentOfType(
            element,
            CrystalMethodDefinition::class.java,
            CrystalMacroDefinition::class.java
        )
        if (methodDef != null) {
            val paramList = when (methodDef) {
                is CrystalMethodDefinition -> methodDef.parameterList
                is CrystalMacroDefinition -> methodDef.parameterList
                else -> null
            }
            if (paramList != null) {
                val paramNames = paramList.parameterList.mapNotNull { param ->
                    param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
                }.toSet()
                if (name in paramNames) return true
            }
        }

        // Find enclosing block (e.g. 3.times do |i|)
        val block = PsiTreeUtil.getParentOfType(element, CrystalBlock::class.java)
        if (block != null) {
            val paramList = block.parameterList ?: return false
            val paramNames = paramList.parameterList.mapNotNull { param ->
                param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text
            }.toSet()
            return name in paramNames
        }

        return false
    }

    /**
     * Highlights $0, $1, $2, ... operand references inside asm template strings
     * with the same color as numbers.
     */
    private fun annotateAsmOperandReferences(element: PsiElement, holder: AnnotationHolder) {
        // Only inside asm expressions
        val asmExpr = PsiTreeUtil.getParentOfType(element, CrystalAsmExpression::class.java) ?: return

        // Only the first string_expression (template) — check it's the template, not a constraint
        val stringExpr = element.parent
        val asmOperand = PsiTreeUtil.getParentOfType(stringExpr, CrystalAsmOperand::class.java)
        if (asmOperand != null) return // This is a constraint string like "=r", not the template

        val text = element.text
        val startOffset = element.textRange.startOffset
        val regex = Regex("\\$\\d+")
        for (match in regex.findAll(text)) {
            val range = TextRange(startOffset + match.range.first, startOffset + match.range.last + 1)
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .textAttributes(CrystalSyntaxHighlighter.NUMBER)
                .create()
        }
    }

    /**
     * Highlights TODO, FIXME keywords (and the rest of the line) inside comments
     * with a distinct color matching Ruby's style (same as numbers/symbols).
     * Uses enforcedTextAttributes to override IntelliJ's built-in TODO highlighting.
     */
    private fun annotateTodoComment(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text
        val startOffset = element.textRange.startOffset
        val match = TODO_PATTERN.find(text) ?: return
        val range = TextRange(startOffset + match.range.first, startOffset + text.length)
        val scheme = EditorColorsManager.getInstance().globalScheme
        val attrs = scheme.getAttributes(DefaultLanguageHighlighterColors.NUMBER)
        if (attrs != null && attrs.foregroundColor != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(range)
                .enforcedTextAttributes(attrs)
                .create()
        }
    }

    /**
     * Highlights regex sub-patterns inside regex literals using IntelliJ's built-in
     * RegExp colors so they match RubyMine/IDEA exactly.
     * Also validates regex escapes and marks invalid PCRE2 escapes as errors.
     */
    private fun annotateRegexLiteral(element: PsiElement, holder: AnnotationHolder) {
        val text = element.text
        val startOffset = element.textRange.startOffset

        // Validate invalid PCRE2 escapes before highlighting
        annotateInvalidRegexEscapes(text, startOffset, holder)

        // Character classes: [...]
        for (match in REGEX_CHAR_CLASS.findAll(text)) {
            applyRange(holder, startOffset, match.range, CrystalSyntaxHighlighter.REGEXP_CHAR_CLASS)
        }
        // Escapes: \d \w \s \n \t \xXX \uXXXX \p{...} \k<name> \K \N \R \X \Q etc.
        for (match in REGEX_ESCAPE.findAll(text)) {
            applyRange(holder, startOffset, match.range, CrystalSyntaxHighlighter.REGEXP_ESC_CHARACTER)
        }
        // Quantifiers: ++ *+ ?+ + * ? {n} {n,} {n,m} (and lazy versions)
        for (match in REGEX_QUANTIFIER.findAll(text)) {
            applyRange(holder, startOffset, match.range, CrystalSyntaxHighlighter.REGEXP_QUANTIFIER)
        }
        // Alternation: |
        for (match in REGEX_ALTERNATION.findAll(text)) {
            applyRange(holder, startOffset, match.range, CrystalSyntaxHighlighter.REGEXP_UNION)
        }
        // Anchors: ^ $ \A \Z \z \G
        for (match in REGEX_ANCHOR.findAll(text)) {
            applyRange(holder, startOffset, match.range, CrystalSyntaxHighlighter.REGEXP_META)
        }
        // Group punctuation: (?:...) (?=...) (?!...) (?<=...) (?<!...) (?<name>...)
        for (match in REGEX_GROUP_PUNCTUATION.findAll(text)) {
            applyRange(holder, startOffset, match.range, CrystalSyntaxHighlighter.REGEXP_PARENTHS)
        }
        for (match in REGEX_NAMED_GROUP_PREFIX.findAll(text)) {
            applyRange(holder, startOffset, match.range, CrystalSyntaxHighlighter.REGEXP_PARENTHS)
        }
        // Simple group parentheses: ( and ) not part of a special group prefix
        for (match in REGEX_SIMPLE_GROUP.findAll(text)) {
            applyRange(holder, startOffset, match.range, CrystalSyntaxHighlighter.REGEXP_PARENTHS)
        }
    }

    /**
     * Marks invalid PCRE2 escape sequences inside regex literals as errors.
     * PCRE2 does not support: \u, \F, \L, \l, \N{name}, \U
     * For \u and \N{U+...}, suggests the equivalent \x{CODEPOINT} alternative.
     */
    private fun annotateInvalidRegexEscapes(text: String, startOffset: Int, holder: AnnotationHolder) {
        for (match in REGEX_INVALID_ESCAPE.findAll(text)) {
            val escapeText = match.value
            val range = TextRange(startOffset + match.range.first, startOffset + match.range.last + 1)
            val message = when {
                escapeText.startsWith("\\u") -> {
                    val hexValue = if (escapeText.startsWith("\\u{")) {
                        escapeText.removeSurrounding("\\u{", "}")
                    } else {
                        escapeText.removePrefix("\\u")
                    }
                    val codepoint = hexValue.toIntOrNull(16)
                    val suggestion = if (codepoint != null) "\\x{${codepoint.toString(16).uppercase()}}" else "\\x{CODEPOINT}"
                    "Invalid regex escape: PCRE2 does not support \\u. Use $suggestion instead."
                }
                escapeText.startsWith("\\N{") -> {
                    if (escapeText.startsWith("\\N{U+")) {
                        val hexValue = escapeText.removeSurrounding("\\N{U+", "}")
                        val codepoint = hexValue.toIntOrNull(16)
                        val suggestion = if (codepoint != null) "\\x{${codepoint.toString(16).uppercase()}}" else "\\x{CODEPOINT}"
                        "Invalid regex escape: PCRE2 does not support \\N{name}. Use $suggestion instead."
                    } else {
                        "Invalid regex escape: PCRE2 does not support \\N{name}. Use \\x{CODEPOINT} instead."
                    }
                }
                escapeText == "\\F" -> "Invalid regex escape: PCRE2 does not support \\F"
                escapeText == "\\L" -> "Invalid regex escape: PCRE2 does not support \\L"
                escapeText == "\\l" -> "Invalid regex escape: PCRE2 does not support \\l"
                escapeText == "\\U" -> "Invalid regex escape: PCRE2 does not support \\U"
                else -> "Invalid regex escape: PCRE2 does not support $escapeText"
            }
            holder.newAnnotation(HighlightSeverity.ERROR, message)
                .range(range)
                .create()
        }
    }

    /**
     * Validate that a heredoc start has a matching end delimiter.
     * If the end delimiter is missing, mark the start with a clear error.
     */
    private fun validateHeredocStart(element: PsiElement, holder: AnnotationHolder) {
        val startText = element.text
        val delimiter = extractHeredocDelimiter(startText) ?: return

        val file = element.containingFile ?: return

        // Find the matching end delimiter
        val endElement = findHeredocEndElement(file, delimiter)

        if (endElement == null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Missing heredoc end delimiter '$delimiter'")
                .range(element)
                .create()
        }
    }

    /**
     * Validate that a heredoc end delimiter is not indented more than
     * the least-indented content line.
     */
    private fun validateHeredocEnd(element: PsiElement, holder: AnnotationHolder) {
        val endText = element.text
        val delimiter = endText.trim()

        val parent = element.parent
        val minContentIndent = findMinContentIndent(parent, delimiter)

        if (minContentIndent != null) {
            val endIndent = endText.takeWhile { it == ' ' || it == '\t' }.length
            if (endIndent > minContentIndent) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "Heredoc end delimiter '$delimiter' is indented too deeply ($endIndent spaces). " +
                    "It must not exceed the minimum content line indent ($minContentIndent spaces)."
                )
                    .range(element)
                    .create()
            }
        }
    }

    /**
     * Find the HEREDOC_END element matching the given delimiter name.
     */
    /**
     * Handle PsiErrorElement that contains invalid single-quote strings.
     * When the parser encounters 'hello world', it creates an error element.
     * We replace the generic parser error with a user-friendly message.
     */
    private fun validateErrorElement(element: PsiElement, holder: AnnotationHolder) {
        val errorText = element.text
        // Check if the error text is a single-quoted string with more than one character
        if (errorText.startsWith("'") && errorText.endsWith("'") && errorText.length > 3) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "Unterminated char literal — single quotes can only contain one character. " +
                "Use double quotes for strings."
            )
                .range(element)
                .create()
        }
    }

    private fun findHeredocEndElement(file: PsiFile, delimiter: String): PsiElement? {
        return PsiTreeUtil.findChildrenOfType(file, PsiElement::class.java).find {
            it.node.elementType == CrystalTypes.HEREDOC_END && it.text.trim() == delimiter
        }
    }

    /**
     * Find the minimum indentation among content lines in the heredoc literal.
     * Uses the parent heredoc AST node and scans its children for HEREDOC_CONTENT tokens.
     * Returns the number of leading spaces/tabs, or null if no content found.
     *
     * All HEREDOC_CONTENT tokens are concatenated first before splitting into lines,
     * because interpolation splits a single logical line across multiple tokens.
     */
    private fun findMinContentIndent(parent: PsiElement, delimiter: String): Int? {
        val nodeChildren = parent.node.getChildren(null) ?: return null
        val contentBuilder = StringBuilder()

        for (node in nodeChildren) {
            if (node.elementType == CrystalTypes.HEREDOC_CONTENT) {
                contentBuilder.append(node.text)
            } else if (node.elementType == CrystalTypes.HEREDOC_END && node.text.trim() == delimiter) {
                break
            }
        }

        if (contentBuilder.isEmpty()) return null

        var minIndent: Int? = null
        for (line in contentBuilder.toString().lines()) {
            if (line.isNotBlank()) {
                val indent = line.takeWhile { it == ' ' || it == '\t' }.length
                if (minIndent == null || indent < minIndent) {
                    minIndent = indent
                }
            }
        }
        return minIndent
    }

    /**
     * Extract the heredoc delimiter name from the start token text.
     * Supports <<-IDENTIFIER and <<-'IDENTIFIER'.
     */
    private fun extractHeredocDelimiter(text: String): String? {
        val quotedMatch = Regex("""<<-'([A-Za-z_][A-Za-z0-9_]*)'""").find(text)
        if (quotedMatch != null) return quotedMatch.groupValues[1]
        val unquotedMatch = Regex("""<<-([A-Za-z_][A-Za-z0-9_]*)""").find(text)
        if (unquotedMatch != null) return unquotedMatch.groupValues[1]
        return null
    }

    private fun applyRange(holder: AnnotationHolder, baseOffset: Int, range: IntRange, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(com.intellij.openapi.util.TextRange(baseOffset + range.first, baseOffset + range.last + 1))
            .textAttributes(key)
            .create()
    }

    private fun apply(holder: AnnotationHolder, element: PsiElement, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(key)
            .create()
    }

    companion object {
        private val TODO_PATTERN = Regex("\\b(TODO|FIXME)\\b")

        // Regex sub-pattern matching
        private val REGEX_ESCAPE = Regex("""\\[dDwWsSbtnr0aAfv]|\\[xXu]\{[0-9a-fA-F]+\}|\\[xX][0-9a-fA-F]{1,2}|\\u[0-9a-fA-F]{4}|\\p\{[^}]+\}|\\P\{[^}]+\}|\\k<[^>]+>|\\k'[^']+'|\\[KNRX]|\\[QHhvV]""")
        // Invalid PCRE2 escapes that Crystal's regex engine does not support
        private val REGEX_INVALID_ESCAPE = Regex("""\\u[0-9a-fA-F]{4}|\\u\{[0-9a-fA-F]+\}|\\F(?!\w)|\\L(?!\w)|\\l(?!\w)|\\N\{[^}]*\}|\\U(?!\w)""")
        private val REGEX_QUANTIFIER = Regex("""[+\*?]\+[?+]?|[+\*?][?+]?|\{[0-9]+\}|\{[0-9]+,?\}|\{[0-9]+,[0-9]+\}|\{[0-9]+,\}|\{,?[0-9]+\}""")
        private val REGEX_CHAR_CLASS = Regex("""\[(?:\\.|[^\[\]])*\]""")
        private val REGEX_ALTERNATION = Regex("""\|""")
        private val REGEX_ANCHOR = Regex("""[\^$]|\\[AZzG]""")
        private val REGEX_GROUP_PUNCTUATION = Regex("""\(\?[:=!><]|\(\?<=\[!\]|\(\?[imsx]+\-?[imsx]*[:)]|\(\?[#)]|\(\?>""")
        private val REGEX_NAMED_GROUP_PREFIX = Regex("""\(\?<[a-zA-Z_]\w*>""")
        private val REGEX_SIMPLE_GROUP = Regex("""\((?!\?)|\)""")

        private val BUILTIN_MACROS = setOf(
            "getter", "setter", "property",
            "class_getter", "class_setter", "class_property",
            "record", "delegate", "forward_missing_to",
            "def_equals", "def_hash", "def_equals_and_hash",
            "def_clone", "def_clone_as"
        )
    }
}
