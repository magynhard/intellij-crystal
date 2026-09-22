package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.stubs.CrystalNamedStub

/**
 * Utility functions for Crystal PSI elements.
 */
object CrystalPsiUtils {

    data class RecordDefinition(
        val qualifiedName: String,
        val call: CrystalMethodCallExpression
    )

    fun findRecordDefinition(className: String, file: PsiFile): CrystalMethodCallExpression? {
        return findRecordDefinitions(className, file).firstOrNull()?.call
    }

    fun findRecordDefinitions(className: String, file: PsiFile): List<RecordDefinition> {
        return PsiTreeUtil.findChildrenOfType(file, CrystalMethodCallExpression::class.java).mapNotNull { call ->
            val name = recordName(call) ?: return@mapNotNull null
            if (name != className) return@mapNotNull null
            RecordDefinition(
                buildQualifiedName(call) ?: return@mapNotNull null,
                call
            )
        }
    }

    fun recordName(call: CrystalMethodCallExpression): String? {
        return recordDeclaredName(call)?.removePrefix("::")?.substringAfterLast("::")
    }

    fun recordArguments(call: CrystalMethodCallExpression): List<PsiElement> =
        call.bareArgumentList?.bareArgumentList.orEmpty() +
            call.callArgs?.argumentList?.argumentList.orEmpty()

    /** Field arguments of a record declaration, without the leading type-name argument. */
    fun recordFieldArguments(call: CrystalMethodCallExpression): List<PsiElement> =
        recordArguments(call).drop(1)

    /**
     * Parsed shape of one record field argument (`name : Type = default`).
     * [name] is null when the argument carries no leading name.
     */
    data class RecordFieldInfo(
        val name: String?,
        val typeText: String?,
        val defaultText: String?,
        val hasDefault: Boolean,
    )

    /**
     * Extracts the name/type/default of a single record field argument. The name
     * is the first significant token before the colon — an `IDENTIFIER`, a
     * keyword (`record Span, start : Int32, end : Int32`), or a shorthand
     * instance/class variable (`record Point, @x : Int32, @@y : Int32`), each
     * matching the grammar's `named_type_bare_argument`. Instance/class-variable
     * names drop their `@`/`@@` sigil so the accessor and call-site name is the
     * bare field name. Keyword tokens after the colon stay part of the type text
     * (`x : Nil`, `x : self`).
     */
    fun recordFieldInfo(fieldArgument: PsiElement): RecordFieldInfo {
        var name: String? = null
        var typeText: String? = null
        var defaultText: String? = null
        var pastColon = false
        var pastAssign = false
        for (child in fieldArgument.node.getChildren(null)) {
            val type = child.elementType
            when {
                type == CrystalTypes.COLON -> pastColon = true
                type == CrystalTypes.ASSIGN -> pastAssign = true
                type == com.intellij.psi.TokenType.WHITE_SPACE -> Unit
                !pastColon && type == CrystalTypes.IDENTIFIER -> name = child.text
                !pastColon && CrystalTokenTypes.KEYWORDS.contains(type) -> name = child.text
                !pastColon && (type == CrystalTypes.INSTANCE_VAR_ACCESS || type == CrystalTypes.CLASS_VAR_ACCESS) ->
                    name = child.text.removePrefix("@@").removePrefix("@")
                pastAssign -> defaultText = (defaultText ?: "") + child.text
                pastColon -> typeText = (typeText ?: "") + child.text
            }
        }
        return RecordFieldInfo(name, typeText, defaultText, pastAssign)
    }

    private fun recordDeclaredName(call: CrystalMethodCallExpression): String? {
        if (call.firstChild?.text != "record") return null
        val candidate = recordArguments(call).firstOrNull()?.text?.filterNot(Char::isWhitespace) ?: return null
        return candidate.takeIf { RECORD_NAME.matches(it) }
    }

    /**
     * Returns whether the element is a nominal type boundary: a class, module,
     * struct, or enum definition, or a `record` declaration with a type body.
     */
    fun isTypeDefinition(element: PsiElement): Boolean =
        element is CrystalClassDefinition ||
            element is CrystalModuleDefinition ||
            element is CrystalStructDefinition ||
            element is CrystalEnumDefinition ||
            element is CrystalMethodCallExpression && recordName(element) != null &&
            element.classBody != null

    /**
     * The method header line (`private def in_call_args(value = true, &)`):
     * from the line start through the closing bracket of the parameter list.
     * The rename dialog target display and the descriptive name use it — the
     * platform fallbacks render the WHOLE method text (including the body).
     */
    fun methodHeaderText(def: CrystalMethodDefinition): String? {
        val fileText = def.containingFile?.text ?: return null
        var endOffset = def.parameterList?.textRange?.endOffset
            ?: (def.textRange.startOffset + def.text.substringBefore('\n').length)
        // The closing `)` of the parameter list belongs to the def node, not
        // to the parameterList element — include it (plus a trailing block
        // param when present).
        while (endOffset < fileText.length && fileText[endOffset].isWhitespace()) endOffset++
        if (endOffset < fileText.length && fileText[endOffset] == ')') {
            endOffset++
            while (endOffset < fileText.length && fileText[endOffset].isWhitespace()) endOffset++
        }
        if (endOffset < fileText.length && fileText[endOffset] == '&') {
            while (endOffset < fileText.length && fileText[endOffset] != '\n') endOffset++
        }
        var start = def.textRange.startOffset
        while (start > 0 && fileText[start - 1] != '\n') start--
        return fileText.substring(start, endOffset.coerceAtMost(fileText.length))
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { null }
    }

    fun buildLexicalQualifiedNameCandidates(simpleName: String, context: PsiElement): Set<String> {
        val candidates = linkedSetOf<String>()
        val enclosingParts = getEnclosingType(context)
            ?.let(::buildQualifiedName)
            ?.split("::")
            .orEmpty()
        for (size in enclosingParts.size downTo 1) {
            candidates.add(enclosingParts.take(size).joinToString("::") + "::$simpleName")
        }
        candidates.add(simpleName)
        return candidates
    }

    fun isInsideMacroControlRegion(element: PsiElement): Boolean {
        var current = element
        while (true) {
            val parent = current.parent ?: return false
            if (parent is PsiFile || parent is CrystalClassBody) {
                var depth = 0
                for (sibling in parent.children) {
                    if (sibling === current) {
                        if (depth > 0) return true
                        break
                    }
                    if (sibling is CrystalMacroControl) {
                        depth = updateMacroDepth(depth, sibling)
                    }
                }
            }
            current = parent
        }
    }

    private fun updateMacroDepth(depth: Int, control: CrystalMacroControl): Int {
        val keyword = control.text.removePrefix("{%")
            .removeSuffix("%}")
            .trim()
            .takeWhile { !it.isWhitespace() }
        return when (keyword) {
            "end" -> (depth - 1).coerceAtLeast(0)
            "if", "unless", "for", "case", "begin" -> depth + 1
            else -> depth
        }
    }

    /**
     * Returns whether a method has an explicit static receiver in its definition
     * header: `self` (`def self.build`) or a constant path (`def Float64.new`,
     * `def Time::Location.new`). Macro-interpolated receivers stay unclassified
     * here — their concrete owner is unknown before macro expansion.
     */
    fun isSelfMethod(method: CrystalMethodDefinition): Boolean {
        method.stub?.isSelfMethod?.let { return it }
        val node = method.node
        var sawReceiver = false
        var child = node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                CrystalTypes.LPAREN, CrystalTypes.METHOD_BODY, CrystalTypes.COLON -> break
                CrystalTypes.SELF, CrystalTypes.CONSTANT -> sawReceiver = true
                CrystalTypes.DOT -> if (sawReceiver) return true
            }
            child = child.treeNext
        }
        return false
    }

    /**
     * Returns the explicit constant receiver of a method definition header, if
     * any: `def Time::Location.new` → `"Time::Location"`, `def Float64.new` →
     * `"Float64"`. Returns null for `self` receivers, macro-interpolated
     * receivers, and receiver-less definitions. Only strict
     * `CONSTANT (:: CONSTANT)*` shapes qualify, mirroring the grammar.
     */
    fun explicitMethodReceiverQualifiedName(method: CrystalMethodDefinition): String? {
        val segments = mutableListOf<String>()
        var expectConstant = true
        var child = method.node.firstChildNode
        while (child != null) {
            when (child.elementType) {
                CrystalTypes.LPAREN, CrystalTypes.METHOD_BODY, CrystalTypes.COLON -> break
                CrystalTypes.DOT -> return segments.joinToString("::").takeIf { segments.isNotEmpty() }
                CrystalTypes.CONSTANT -> {
                    if (!expectConstant) return null
                    segments.add(child.text)
                    expectConstant = false
                }
                CrystalTypes.DOUBLE_COLON -> {
                    if (expectConstant) return null
                    expectConstant = true
                }
                com.intellij.psi.TokenType.WHITE_SPACE -> Unit
                else -> {
                    // SELF, macro interpolation, or the method target itself:
                    // only a DOT-terminated constant path is an explicit owner.
                    if (segments.isNotEmpty()) return null
                }
            }
            child = child.treeNext
        }
        return null
    }

    /**
     * Returns the qualified owner governing a method definition: the explicit
     * receiver first (`def Time::Location.new` inside any scope belongs to
     * `Time::Location`), otherwise the lexically enclosing type or record.
     */
    fun methodOwnerQualifiedName(method: CrystalMethodDefinition): String? {
        explicitMethodReceiverQualifiedName(method)?.let { return it }
        return getEnclosingType(method)?.let(::buildQualifiedName)
    }

    /**
     * Returns the qualified owner governing an unqualified call site: the
     * explicit receiver of the enclosing method definition first (so `load`
     * inside `def Time::Location.new` resolves against `Time::Location`),
     * otherwise the lexically enclosing type.
     */
    fun callSiteOwnerQualifiedName(context: PsiElement): String? {
        val enclosingMethod = PsiTreeUtil.getParentOfType(context, CrystalMethodDefinition::class.java)
        if (enclosingMethod != null) {
            explicitMethodReceiverQualifiedName(enclosingMethod)?.let { return it }
        }
        return getEnclosingType(context)?.let(::buildQualifiedName)
    }

    /**
     * Builds the fully-qualified name of a class/module/struct/enum definition
     * by walking up the PSI tree and collecting enclosing type names.
     *
     * Examples:
     * - `class Foo; class Sub; end; end` → `"Foo::Sub"`
     * - `class Foo::Sub` (namespace-defined) → `"Foo::Sub"` (from stub.name)
     * - `class C` in `class B` in `class A` → `"A::B::C"`
     * - `module Foo; class Bar; end; end` → `"Foo::Bar"`
     */
    fun buildQualifiedName(element: PsiElement): String? {
        val parts = mutableListOf<String>()
        var current: PsiElement? = element
        while (current != null) {
            val name = when (current) {
                is CrystalClassDefinition -> extractQualifiedTypeName(current) ?: current.name
                is CrystalModuleDefinition -> extractQualifiedTypeName(current) ?: current.name
                is CrystalStructDefinition -> extractQualifiedTypeName(current) ?: current.name
                is CrystalEnumDefinition -> extractQualifiedTypeName(current) ?: current.name
                is CrystalMethodCallExpression -> recordDeclaredName(current)
                else -> null
            }
            if (name?.startsWith("::") == true) {
                parts.clear()
                parts.add(name.removePrefix("::"))
                break
            }
            if (name != null) parts.add(0, name)
            current = current.parent
        }
        return if (parts.isNotEmpty()) parts.joinToString("::") else null
    }

    /**
     * Builds the fully-qualified name of a class/module/struct/enum definition
     * from a stub element, using the stub tree for faster traversal.
     */
    fun buildQualifiedNameFromStub(stub: com.intellij.psi.stubs.StubElement<*>): String? {
        val parts = mutableListOf<String>()
        var current: com.intellij.psi.stubs.StubElement<*>? = stub
        while (current != null) {
            if (current is CrystalNamedStub) {
                val name = current.name
                if (name != null) {
                    parts.add(0, name)
                }
            }
            current = current.parentStub
        }
        return if (parts.isNotEmpty()) parts.joinToString("::") else null
    }

    /**
     * Returns the immediate enclosing class/module/struct/enum (or body-bearing
     * record declaration) of an element.
     */
    fun getEnclosingType(element: PsiElement): PsiElement? {
        return PsiTreeUtil.findFirstParent(element) { parent ->
            parent !== element && isTypeDefinition(parent)
        }
    }

    /**
     * Builds the full namespace path from a [CrystalNamespaceAccess] element
     * by walking left through preceding [CrystalNamespaceAccess] and
     * [CrystalVariableReference] elements.
     *
     * Example: for `Foo::Sub.space`, when called on the `::Sub` element,
     * returns `"Foo::Sub"`.
     */
    fun buildNamespacePath(namespaceAccess: CrystalNamespaceAccess): String {
        val parts = mutableListOf<String>()

        // Get the CONSTANT from this namespace_access
        namespaceAccess.node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.CONSTANT)
            ?.text?.let { parts.add(0, it) }

        // Walk left through preceding namespace_access and variable_reference
        var current = namespaceAccess.prevSibling
        while (current != null) {
            when {
                current is PsiWhiteSpace ||
                    current.node?.elementType == de.magynhard.crystal.psi.CrystalTypes.NEWLINE -> {
                    current = current.prevSibling
                }
                current is CrystalNamespaceAccess -> {
                    current.node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.CONSTANT)
                        ?.text?.let { parts.add(0, it) }
                    current = current.prevSibling
                }
                current is CrystalVariableReference -> {
                    current.node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.CONSTANT)
                        ?.text?.let { parts.add(0, it) }
                    break
                }
                else -> break
            }
        }

        return parts.joinToString("::")
    }

    /**
     * Extracts the fully-qualified type name from a type definition's PSI children.
     * For `class Foo::Bar`, returns "Foo::Bar". For `class Baz`, returns null.
     *
     * Works by scanning the PSI children for CONSTANT tokens (type_name is inlined as
     * direct children of the definition node) and joining them with "::" if there are
     * multiple CONSTANTS before the class_body.
     */
    private fun extractQualifiedTypeName(element: PsiElement): String? {
        val constants = mutableListOf<String>()
        var child = element.node.firstChildNode
        while (child != null) {
            if (child.elementType == CrystalTypes.CONSTANT) {
                constants.add(child.text)
            }
            // Stop at class_body — CONSTANTS inside the body are not part of the type_name
            if (child.elementType == CrystalTypes.CLASS_BODY) break
            child = child.treeNext
        }
        // If there are multiple CONSTANTS (e.g. Foo::Bar), return the full qualified name
        return if (constants.size >= 2) constants.joinToString("::") else null
    }

    private val RECORD_NAME = Regex("(?:::)?[A-Z][A-Za-z0-9_]*(?:::[A-Z][A-Za-z0-9_]*)*")

    /**
     * Local binding of one `multi_assign_target`, or null when the target
     * binds no local: the identifier leaf plus whether it is a splat target
     * (`*rest`).
     *
     * Only compiler-valid local forms qualify: a bare `variable` identifier
     * (including `_`) and `STAR variable`. Indexed/member targets
     * (`arr[0]`, `a.foo`) bind no locals, `macro_interpolation` targets are
     * not statically known, and parenthesized, nested, or typed shapes are
     * compiler syntax errors — none of them may become bindings.
     */
    fun multiAssignTargetLocal(target: CrystalMultiAssignTarget): Pair<PsiElement, Boolean>? {
        val significant = target.node.getChildren(null).map { it.psi }.filter { !it.text.isBlank() }
        if (significant.size == 1 && significant[0].node.elementType == CrystalTypes.IDENTIFIER) {
            return significant[0] to false
        }
        if (significant.size == 2 && significant[0].node.elementType == CrystalTypes.STAR &&
            significant[1].node.elementType == CrystalTypes.IDENTIFIER
        ) {
            return significant[1] to true
        }
        return null
    }
}
