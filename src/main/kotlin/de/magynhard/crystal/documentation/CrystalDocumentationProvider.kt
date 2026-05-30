package de.magynhard.crystal.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.*
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Provides Quick Documentation (Ctrl+Q / F1) and hover documentation for Crystal elements.
 * Shows the element signature (syntax-highlighted) and doc comments rendered as Markdown/HTML.
 */
class CrystalDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = resolveTarget(element) ?: return null
        return buildDocumentation(target)
    }

    override fun getCustomDocumentationElement(
        editor: com.intellij.openapi.editor.Editor,
        file: com.intellij.psi.PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        if (contextElement == null) return null
        // Try to resolve reference from the context element
        val ref = contextElement.reference ?: contextElement.parent?.reference
        return ref?.resolve()
    }

    // ==================== Target Resolution ====================

    private fun resolveTarget(element: PsiElement?): PsiElement? {
        if (element == null) return null
        // Already a definition
        if (element is CrystalMethodDefinition || element is CrystalClassDefinition
            || element is CrystalModuleDefinition) {
            return element
        }
        // Try resolving via reference
        val ref = element.reference
        if (ref != null) {
            val resolved = ref.resolve()
            if (resolved != null) return resolveTarget(resolved)
        }
        // Walk up a few levels to find a definition (for leaf tokens like IDENTIFIER in a method name)
        var current: PsiElement? = element.parent
        var depth = 0
        while (current != null && depth < 4) {
            if (current is CrystalMethodDefinition || current is CrystalClassDefinition
                || current is CrystalModuleDefinition) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    // ==================== Documentation Building ====================

    private fun buildDocumentation(target: PsiElement): String {
        val sb = StringBuilder()
        sb.append("<div class='definition'><pre>")
        sb.append(renderSignature(target))
        sb.append("</pre></div>")

        val docComment = collectDocComment(target)
        if (docComment != null) {
            sb.append("<div class='content'>")
            sb.append(renderMarkdown(docComment, target))
            sb.append("</div>")
        }

        return sb.toString()
    }

    // ==================== Signature Rendering ====================

    private fun renderSignature(target: PsiElement): String {
        val signatureText = buildSignatureText(target)
        // Use HtmlSyntaxInfoUtil for syntax-highlighted signature
        val highlighted = highlightCrystalCode(signatureText, target)
        return highlighted ?: escapeHtml(signatureText)
    }

    private fun buildSignatureText(target: PsiElement): String {
        return when (target) {
            is CrystalMethodDefinition -> buildMethodSignature(target)
            is CrystalClassDefinition -> buildClassSignature(target)
            is CrystalModuleDefinition -> buildModuleSignature(target)
            else -> target.text.lines().first()
        }
    }

    private fun buildMethodSignature(method: CrystalMethodDefinition): String {
        val sb = StringBuilder()
        val enclosingClass = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
            ?: PsiTreeUtil.getParentOfType(method, CrystalModuleDefinition::class.java)

        val className = when (enclosingClass) {
            is CrystalClassDefinition -> enclosingClass.name
            is CrystalModuleDefinition -> enclosingClass.name
            else -> null
        }

        // Check if it's a self (class) method
        val methodNameText = method.methodName?.text ?: method.name ?: "unknown"
        val isSelfMethod = methodNameText.startsWith("self.")

        if (className != null) {
            sb.append(className)
            if (isSelfMethod) {
                sb.append(".")
                sb.append(methodNameText.removePrefix("self."))
            } else {
                sb.append("#")
                sb.append(methodNameText)
            }
        } else {
            sb.append(methodNameText)
        }

        // Parameters
        val paramList = method.parameterList
        if (paramList != null) {
            sb.append("(")
            sb.append(paramList.parameterList.joinToString(", ") { it.text.trim() })
            sb.append(")")
        }

        // Return type
        val returnType = method.typeReference
        if (returnType != null) {
            sb.append(" : ")
            sb.append(returnType.text)
        }

        return sb.toString()
    }

    private fun buildClassSignature(classDef: CrystalClassDefinition): String {
        val sb = StringBuilder("class ")
        sb.append(classDef.name ?: "Unknown")
        // Check for superclass via text scan (simple approach)
        val text = classDef.text
        val firstLine = text.lines().first()
        if (firstLine.contains("<")) {
            val superPart = firstLine.substringAfter("<").trim()
            if (superPart.isNotEmpty()) {
                sb.append(" < ")
                sb.append(superPart)
            }
        }
        return sb.toString()
    }

    private fun buildModuleSignature(moduleDef: CrystalModuleDefinition): String {
        return "module ${moduleDef.name ?: "Unknown"}"
    }

    // ==================== Doc Comment Collection ====================

    /**
     * Collects doc comment lines above a definition.
     * Returns the merged Markdown text, or null if no doc comment exists.
     */
    private fun collectDocComment(element: PsiElement): String? {
        val comments = mutableListOf<String>()

        var current: PsiElement? = PsiTreeUtil.prevLeaf(element)

        // Skip whitespace/newlines directly before element
        var newlineCount = 0
        while (current != null && isWhitespaceOrNewline(current)) {
            if (current.node?.elementType == CrystalTypes.NEWLINE) newlineCount++
            current = PsiTreeUtil.prevLeaf(current)
        }
        // If there were 2+ newlines before the definition, there's a blank line = no doc comment
        if (newlineCount > 1) return null

        // Collect consecutive comment lines going backwards
        while (current != null) {
            if (current is PsiComment || current.node?.elementType == CrystalTypes.LINE_COMMENT) {
                val text = current.text
                if (text.startsWith("#")) {
                    val content = when {
                        text.startsWith("# ") -> text.removePrefix("# ")
                        text == "#" -> ""
                        text.startsWith("#") -> text.removePrefix("#")
                        else -> text
                    }
                    comments.add(0, content)
                } else {
                    break
                }
                current = PsiTreeUtil.prevLeaf(current)
            } else if (isWhitespaceOrNewline(current)) {
                // Count newlines between comment lines — 2+ means blank line (end of doc block)
                var nlCount = 0
                while (current != null && isWhitespaceOrNewline(current)) {
                    if (current.node?.elementType == CrystalTypes.NEWLINE) nlCount++
                    current = PsiTreeUtil.prevLeaf(current)
                }
                if (nlCount > 1) break
                // Don't advance — current is now the next non-whitespace element
            } else {
                break
            }
        }

        if (comments.isEmpty()) return null
        return comments.joinToString("\n")
    }

    private fun isWhitespaceOrNewline(element: PsiElement): Boolean {
        if (element is PsiWhiteSpace) return true
        val type = element.node?.elementType
        return type == CrystalTypes.NEWLINE || type == com.intellij.psi.TokenType.WHITE_SPACE
    }

    // ==================== Markdown Rendering ====================

    private fun renderMarkdown(markdown: String, context: PsiElement): String {
        val flavour = GFMFlavourDescriptor()
        val parsedTree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        var html = HtmlGenerator(markdown, parsedTree, flavour).generateHtml()

        // Strip the wrapping <body> tags that HtmlGenerator adds
        html = html.removePrefix("<body>").removeSuffix("</body>")

        // Enhance Crystal code blocks with syntax highlighting
        html = highlightCodeBlocks(html, context)

        return html
    }

    /**
     * Replaces <code> blocks containing Crystal code with syntax-highlighted versions.
     */
    private fun highlightCodeBlocks(html: String, context: PsiElement): String {
        // Replace <pre><code>...</code></pre> blocks with highlighted Crystal
        val codeBlockPattern = Regex("<pre><code[^>]*>(.*?)</code></pre>", RegexOption.DOT_MATCHES_ALL)
        return codeBlockPattern.replace(html) { match ->
            val code = unescapeHtml(match.groupValues[1].trim())
            val highlighted = highlightCrystalCode(code, context)
            if (highlighted != null) {
                "<pre><code>$highlighted</code></pre>"
            } else {
                match.value
            }
        }
    }

    // ==================== Syntax Highlighting ====================

    private fun highlightCrystalCode(code: String, context: PsiElement): String? {
        val project = context.project
        return try {
            val builder = StringBuilder()
            HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                builder, project, CrystalLanguage, code, 1.0f
            )
            builder.toString()
        } catch (_: Exception) {
            null
        }
    }

    // ==================== HTML Utilities ====================

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }
}
