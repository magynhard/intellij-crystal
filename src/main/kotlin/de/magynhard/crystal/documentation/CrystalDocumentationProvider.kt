package de.magynhard.crystal.documentation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.completion.CrystalTypeInference
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Provides Quick Documentation (Ctrl+Q / F1) and hover documentation for Crystal elements.
 * Shows the element signature (syntax-highlighted) and doc comments rendered as Markdown/HTML.
 *
 * Documentation links: type names, class names, and superclass names inside the rendered
 * documentation popup are hyperlinked via `psi_element://class:<name>` URLs. Clicking them
 * resolves via [CrystalClassIndex] and replaces the popup content with the target element's
 * documentation (handled by [getDocumentationElementForLink]).
 */
class CrystalDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = resolveTarget(element) ?: return null

        // Variable hover: show inferred type
        if (isVariableIdentifier(target)) {
            return buildVariableDocumentation(target)
        }

        return buildDocumentation(target)
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        if (contextElement == null) return null

        // 1. Unwrap argument wrappers to find the actual expression inside
        val unwrapped = unwrapArgument(contextElement)

        // 2. Try PsiReference on the context element (and its parent, for leaf tokens
        //    whose reference lives on the wrapping composite, e.g. IDENTIFIER inside
        //    CrystalVariableReference).
        val ref = unwrapped.reference ?: unwrapped.parent?.reference
        val resolved = ref?.resolve()
        if (resolved != null) {
            // If resolved element is a definition/parameter, return it directly
            if (resolved is CrystalMethodDefinition || resolved is CrystalClassDefinition
                || resolved is CrystalModuleDefinition || resolved is CrystalStructDefinition
                || resolved is CrystalEnumDefinition || resolved is CrystalParameter) {
                return resolved
            }
            // If resolved element is a variable identifier, return it for type info
            if (isVariableIdentifier(resolved)) return resolved
            // Otherwise, resolved to something like an assignment — continue to next steps
        }

        // 3. Fallback for DOT-call identifiers (Apfel.tanzen, a.essen, Senf.new) —
        //    these have no PsiReference today. Delegate to the GotoDeclarationHandler,
        //    which knows how to resolve the DOT pattern via sibling/leaf scanning.
        val handler: GotoDeclarationHandler = CrystalGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(unwrapped, targetOffset, editor)
        if (targets?.firstOrNull() != null) return targets.first()

        // 4. Definition/parameter walk-up: hovering over the definition name itself
        //    (e.g. `butter` in `def butter`, `Foo` in `class Foo`) or a parameter name.
        var current: PsiElement? = unwrapped
        var depth = 0
        while (current != null && depth < 4) {
            if (current is CrystalMethodDefinition || current is CrystalClassDefinition
                || current is CrystalModuleDefinition || current is CrystalStructDefinition
                || current is CrystalEnumDefinition || current is CrystalParameter) {
                return current
            }
            current = current.parent
            depth++
        }

        // 5. Variable identifier: hovering over a variable name (not a definition).
        if (isVariableIdentifier(unwrapped)) {
            return unwrapped
        }

        // 6. Last resort: direct StubIndex lookup for classes/methods without resolving through handler
        val project = unwrapped.project
        val scope = GlobalSearchScope.allScope(project)
        val name = unwrapped.text

        val classes = StubIndex.getElements(
            CrystalClassIndex.KEY, name, project, scope,
            CrystalNamedElement::class.java
        )
        if (classes.isNotEmpty()) return classes.firstOrNull()

        val methods = StubIndex.getElements(
            CrystalMethodIndex.KEY, name, project, scope,
            CrystalMethodDefinition::class.java
        )
        if (methods.isNotEmpty()) return methods.firstOrNull()

        return null
    }

    /**
     * Unwraps argument wrappers (CrystalArgument, CrystalBareArgument) to find
     * the actual expression inside. E.g. for `foo(arr)`, extracts `arr` from
     * the CrystalArgument wrapper.
     */
    private fun unwrapArgument(element: PsiElement): PsiElement {
        return when (element) {
            is CrystalArgument -> element.expression ?: element
            is CrystalBareArgument -> {
                // CrystalBareArgument contains the expression as a child
                val expr = element.children.firstOrNull { it is CrystalExpression }
                expr ?: element
            }
            else -> element
        }
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager, link: String, originalElement: PsiElement?): PsiElement? {
        if (!link.startsWith("class:")) return null
        val className = link.removePrefix("class:")
        val project = originalElement?.project ?: return null
        val elements = StubIndex.getElements(
            CrystalClassIndex.KEY, className, project,
            GlobalSearchScope.allScope(project), CrystalNamedElement::class.java
        )
        return elements.firstOrNull()
    }

    // ==================== Target Resolution ====================

    private fun resolveTarget(element: PsiElement?): PsiElement? {
        if (element == null) return null
        // Already a definition or parameter — return directly
        if (element is CrystalMethodDefinition || element is CrystalClassDefinition
            || element is CrystalModuleDefinition || element is CrystalStructDefinition
            || element is CrystalEnumDefinition || element is CrystalParameter) {
            return element
        }
        // Variable identifier — return directly for type info rendering
        if (isVariableIdentifier(element)) {
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
                || current is CrystalModuleDefinition || current is CrystalStructDefinition
                || current is CrystalEnumDefinition) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    private fun isVariableIdentifier(element: PsiElement): Boolean {
        // An IDENTIFIER token or CrystalVariableReference that is NOT inside a definition/parameter
        val isIdent = element.node?.elementType == CrystalTypes.IDENTIFIER
        val isVarRef = element is CrystalVariableReference
        if (!isIdent && !isVarRef) {
            // Also check if it's a CrystalExpression wrapping a variable reference
            if (element is CrystalExpression) {
                val varRef = element.variableReferenceList.firstOrNull()
                if (varRef != null) return isVariableIdentifier(varRef)
            }
            return false
        }
        var current: PsiElement? = element.parent
        var depth = 0
        while (current != null && depth < 5) {
            if (current is CrystalMethodDefinition || current is CrystalClassDefinition
                || current is CrystalModuleDefinition || current is CrystalStructDefinition
                || current is CrystalEnumDefinition || current is CrystalParameter) {
                return false
            }
            current = current.parent
            depth++
        }
        return true
    }

    // ==================== Documentation Building ====================

    private fun buildDocumentation(target: PsiElement): String {
        val sb = StringBuilder()
        sb.append("<div class='definition'><pre>")
        sb.append(renderSignature(target))
        sb.append("</pre></div>")

        // Auto-generated doc for untyped parameters
        if (target is CrystalParameter && target.typeReference == null) {
            sb.append("<div class='content'>")
            sb.append("<p>The type of this parameter is not specified and will be determined at runtime.</p>")
            sb.append("</div>")
        } else {
            val docComment = collectDocComment(target)
            if (docComment != null) {
                sb.append("<div class='content'>")
                sb.append(renderMarkdown(docComment, target))
                sb.append("</div>")
            }
        }

        return sb.toString()
    }

    // ==================== Signature Rendering ====================

    private fun renderSignature(target: PsiElement): String {
        val project = target.project
        return when (target) {
            is CrystalMethodDefinition -> buildMethodSignatureHtml(target, project)
            is CrystalClassDefinition -> buildClassSignatureHtml(target, project)
            is CrystalModuleDefinition -> buildModuleSignatureHtml(target)
            is CrystalStructDefinition -> buildStructSignatureHtml(target, project)
            is CrystalEnumDefinition -> buildEnumSignatureHtml(target)
            is CrystalParameter -> buildParameterSignatureHtml(target, project)
            else -> highlightCrystalCode(target.text.lines().first(), target)
                ?: escapeHtml(target.text.lines().first())
        }
    }

    private fun buildMethodSignatureHtml(method: CrystalMethodDefinition, project: Project): String {
        val sb = StringBuilder()
        val enclosingClass = PsiTreeUtil.getParentOfType(method, CrystalClassDefinition::class.java)
            ?: PsiTreeUtil.getParentOfType(method, CrystalModuleDefinition::class.java)

        val className = when (enclosingClass) {
            is CrystalClassDefinition -> enclosingClass.name
            is CrystalModuleDefinition -> enclosingClass.name
            else -> null
        }

        // Line 1: enclosing class name (linked) — top-level methods show "Object" (Crystal's universal base)
        val displayClassName = className ?: "Object"
        sb.append(linkToClass(displayClassName, project) ?: escapeHtml(displayClassName))
        sb.append("\n")

        // Line 2: method signature (no "def " prefix)
        val methodNameText = method.name ?: "unknown"

        // Build the method line as plain text, then highlight
        val paramList = method.parameterList
        val paramsText = if (paramList != null) {
            paramList.parameterList.joinToString(", ") { it.text.trim() }
        } else ""

        val retTypeText = method.typeReference?.let { " : ${it.text}" } ?: ""

        val methodLine = "$methodNameText($paramsText)$retTypeText"
        val highlighted = highlightCrystalCode(methodLine, method) ?: escapeHtml(methodLine)

        // Wrap type names with links
        sb.append(wrapTypeLinks(highlighted, project))

        return sb.toString()
    }

    private fun buildClassSignatureHtml(classDef: CrystalClassDefinition, project: Project): String {
        val sb = StringBuilder()
        sb.append(highlightCrystalCode("class ", classDef) ?: escapeHtml("class "))
        // Class name is plain — no self-link (the popup IS the class's documentation)
        sb.append(escapeHtml(classDef.name ?: "Unknown"))

        // Superclass
        val superclassClause = classDef.superclassClause
        if (superclassClause != null) {
            val superTypeRef = superclassClause.typeReference
            val superName = superTypeRef.text.trim()
            sb.append(" < ")
            sb.append(linkToClass(superName, project) ?: escapeHtml(superName))
        }

        return sb.toString()
    }

    private fun buildModuleSignatureHtml(moduleDef: CrystalModuleDefinition): String {
        return "${highlightCrystalCode("module ", moduleDef) ?: escapeHtml("module ")}${escapeHtml(moduleDef.name ?: "Unknown")}"
    }

    private fun buildStructSignatureHtml(structDef: CrystalStructDefinition, project: Project): String {
        val sb = StringBuilder()
        sb.append(highlightCrystalCode("struct ", structDef) ?: escapeHtml("struct "))
        sb.append(escapeHtml(structDef.name ?: "Unknown"))

        val superclassClause = structDef.superclassClause
        if (superclassClause != null) {
            val superTypeRef = superclassClause.typeReference
            val superName = superTypeRef.text.trim()
            sb.append(" < ")
            sb.append(linkToClass(superName, project) ?: escapeHtml(superName))
        }

        return sb.toString()
    }

    private fun buildEnumSignatureHtml(enumDef: CrystalEnumDefinition): String {
        val sb = StringBuilder()
        sb.append(highlightCrystalCode("enum ", enumDef) ?: escapeHtml("enum "))
        sb.append(escapeHtml(enumDef.name ?: "Unknown"))
        return sb.toString()
    }

    private fun buildParameterSignatureHtml(param: CrystalParameter, project: Project): String {
        val sb = StringBuilder()

        // Line 1: type name (linked if resolvable, "Any" if untyped) + muted "(Parameter)"
        val typeRef = param.typeReference
        if (typeRef != null) {
            // Use wrapTypeLinks to handle union types like "String | Int32"
            val typeText = typeRef.text.trim()
            val highlighted = highlightCrystalCode(typeText, param) ?: escapeHtml(typeText)
            sb.append(wrapTypeLinks(highlighted, project))
        } else {
            sb.append(escapeHtml("Any"))
        }
        sb.append(" <span style='color:gray'>(Parameter)</span>")
        sb.append("\n")

        // Line 2: parameter name
        val paramName = param.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: "unknown"
        sb.append(escapeHtml(paramName))

        return sb.toString()
    }

    private fun buildVariableDocumentation(target: PsiElement): String {
        val project = target.project
        // Extract variable name: handle CrystalVariableReference, CrystalExpression wrappers
        val name = when {
            target is CrystalVariableReference -> {
                target.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: target.text
            }
            target is CrystalExpression -> {
                val varRef = target.variableReferenceList.firstOrNull()
                if (varRef != null) {
                    varRef.node.findChildByType(CrystalTypes.IDENTIFIER)?.text ?: varRef.text
                } else {
                    target.text
                }
            }
            else -> target.text
        } ?: return ""

        val sb = StringBuilder()
        sb.append("<div class='definition'><pre>")
        sb.append(buildVariableSignatureHtml(target, name, project))
        sb.append("</pre></div>")

        return sb.toString()
    }

    private fun buildVariableSignatureHtml(target: PsiElement, name: String, project: Project): String {
        val sb = StringBuilder()

        // Line 1: inferred type(s) (linked) + muted "(Variable)". Unions shown as "A | B".
        val inferredTypes = CrystalTypeInference.inferTypeList(name, target, project)
        if (inferredTypes.isNotEmpty()) {
            val inferredType = inferredTypes.joinToString(" | ")
            val highlighted = highlightCrystalCode(inferredType, target) ?: escapeHtml(inferredType)
            sb.append(wrapTypeLinks(highlighted, project))
        } else {
            sb.append(escapeHtml("Any"))
        }
        sb.append(" <span style='color:gray'>(Variable)</span>")
        sb.append("\n")

        // Line 2: variable name
        sb.append(escapeHtml(name))

        return sb.toString()
    }

    // ==================== Documentation Links ====================

    /**
     * Returns an `<a>` tag linking to the class documentation, or null if the class
     * is not found in [CrystalClassIndex] (silent omit — callers fall back to plain text).
     */
    private fun linkToClass(name: String, project: Project): String? {
        val elements = StubIndex.getElements(
            CrystalClassIndex.KEY, name, project,
            GlobalSearchScope.allScope(project), CrystalNamedElement::class.java
        )
        if (elements.isEmpty()) return null
        return "<a href=\"psi_element://class:$name\">$name</a>"
    }

    /**
     * Wraps type names in the syntax-highlighted HTML with clickable links.
     * For each uppercase identifier found in the HTML that exists in [CrystalClassIndex],
     * it is wrapped with an `<a>` tag pointing to the class documentation.
     * Uses a word-boundary match (no letter/digit/underscore before or after) to prevent
     * partial matches like `Foo` inside `FooBar`.
     *
     * Special case: Integer types (Int8, Int32, UInt64, etc.) and Float types
     * (Float32, Float64) are linked to their parent type (`Int` or `Float`) since
     * they don't have individual documentation pages.
     */
    private fun wrapTypeLinks(highlightedHtml: String, project: Project): String {
        // Find all potential type names (uppercase identifiers) in the HTML
        val typeNames = Regex("[A-Z][A-Za-z0-9_]*")
            .findAll(highlightedHtml)
            .map { it.value }
            .distinct()
            .filter { name ->
                isResolvableType(name, project)
            }
            .toList()

        var result = highlightedHtml
        for (name in typeNames) {
            val linkTarget = resolveTypeLinkTarget(name, project)
            if (linkTarget != null) {
                // Match type name only at word boundaries — no letter/digit/underscore before or after
                // to prevent matching `Foo` inside `FooBar` or `Foo_Bar`
                result = result.replace(
                    Regex("""(?<![a-zA-Z0-9_])${Regex.escape(name)}(?![a-zA-Z0-9_])"""),
                    "<a href=\"psi_element://class:$linkTarget\">$name</a>"
                )
            }
        }
        return result
    }

    /**
     * Checks if a type name can be resolved (either directly in the class index,
     * or via the numeric type fallback mapping).
     */
    private fun isResolvableType(name: String, project: Project): Boolean {
        if (StubIndex.getElements(
                CrystalClassIndex.KEY, name, project,
                GlobalSearchScope.allScope(project), CrystalNamedElement::class.java
            ).isNotEmpty()) return true
        return resolveNumericTypeLink(name) != null
    }

    /**
     * Returns the link target for a type name. For numeric types, returns the
     * parent type name (e.g. "Int32" → "Int", "Float64" → "Float").
     * Returns null if the type should not be linked.
     */
    private fun resolveTypeLinkTarget(name: String, project: Project): String? {
        // Check direct class index first
        if (StubIndex.getElements(
                CrystalClassIndex.KEY, name, project,
                GlobalSearchScope.allScope(project), CrystalNamedElement::class.java
            ).isNotEmpty()) return name

        // Check numeric type fallback
        return resolveNumericTypeLink(name)
    }

    /**
     * Maps numeric types to their parent type for documentation linking.
     * Int8/Int16/Int32/Int64/Int128 → "Int"
     * UInt8/UInt16/UInt32/UInt64/UInt128 → "Int"
     * Float32/Float64 → "Float"
     */
    private fun resolveNumericTypeLink(name: String): String? {
        return when {
            name.startsWith("Int") && name.length in 4..6 -> "Int"
            name.startsWith("UInt") && name.length in 5..7 -> "Int"
            name.startsWith("Float") && name.length in 6..7 -> "Float"
            else -> null
        }
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
