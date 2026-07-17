package de.magynhard.crystal.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.completion.CrystalTypeInference
import de.magynhard.crystal.inspections.CrystalExpressionTypeResolver
import de.magynhard.crystal.stubs.CrystalMethodByClassIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

/**
 * Reference from a DOT-call method-name identifier to its definition.
 *
 * Resolution rules (no false-positive name-only guessing):
 *
 * 1. CONSTANT receiver (e.g. `Apfel.tanzen`, `Senf.new`) — resolve via
 *    [CrystalMethodByClassIndex] keyed by the receiver (class/module/struct/enum
 *    name). Filters results by method name. This is exact — no guessing.
 *
 * 2. Namespace receiver (e.g. `Foo::Sub.space`) — walks left through
 *    [CrystalNamespaceAccess] and [CrystalVariableReference] to reconstruct the
 *    full qualified name, resolves via CrystalClassIndex, then filters methods
 *    by the enclosing class's qualified name to avoid ambiguity (Foo::Sub vs Bar::Sub).
 *
 * 3. IDENTIFIER receiver (e.g. `a.essen` where `a = Apfel.new`):
 *    - Call [CrystalTypeInference.inferType] on the receiver variable name.
 *    - If a concrete type is returned, resolve via [CrystalMethodByClassIndex]
 *      keyed by the inferred type, filtering by method name.
 *    - If the type is **unknown** (untyped parameter, untyped return chain, …),
 *      return `null` — no jump, no false-positive popup.
 *
 * 4. `.new` constructor on a class — resolved via [CrystalMethodByClassIndex] for
 *    `def self.new` if it exists. If not found, falls through to `record` macro,
 *    then to `def initialize` via [CrystalCompletionHelper.getInitializeMethod].
 *    This makes Find Usages on both `.new` and `initialize` work correctly.
 *
 * 5. Project-scoped name-only fallback — when the receiver type is unknown (bare
 *    untyped local/parameter), resolve by method name within the PROJECT ONLY
 *    (never the stdlib) so local navigation works (`rule.auto_fixable?`) without
 *    the cross-project false-positive popups the strict design otherwise forbids.
 *
 * The receiver is found by walking `prevSibling` (skipping whitespace/NLS) from
 * this element — the DOT is the first child of [CrystalDotCallAccess], so the
 * receiver is the preceding sibling in the flattened `postfix_expression` sequence.
 */
class CrystalDotCallReference(
    element: PsiElement,
    private val methodName: String,
    rangeStart: Int,
    rangeLength: Int
) : PsiReferenceBase<PsiElement>(element, TextRange(rangeStart, rangeStart + rangeLength), true) {

    private val receiverInfo: ReceiverInfo? by lazy { findReceiver() }

    override fun resolve(): PsiElement? {
        val info = receiverInfo ?: return null
        val project = element.project
        val scope = GlobalSearchScope.allScope(project)

        val qualifiedName = info.qualifiedName
        val classNames = info.classNames

        // Iterate over every inferred member type (unions: `Int32 | Nil` resolve methods on
        // both members). Namespace disambiguation via qualifiedName only applies when there is
        // a single member type.
        for (className in classNames) {
            // 1. Exact class-based lookup (CONSTANT / namespace / inferred-type receivers).
            val methods = StubIndex.getElements(
                CrystalMethodByClassIndex.KEY, className, project, scope,
                CrystalMethodDefinition::class.java
            )
            val matchingByName = methods.filter { it.name == methodName }

            // Filter by qualified class name if available (for namespace disambiguation)
            val result = if (qualifiedName != null && classNames.size == 1) {
                matchingByName.filter { method ->
                    val enclosing = CrystalPsiUtils.getEnclosingType(method)
                    enclosing != null && CrystalPsiUtils.buildQualifiedName(enclosing) == qualifiedName
                }.firstOrNull()
            } else {
                matchingByName.firstOrNull()
            }
            if (result != null) return result

            // 2. Stdlib fallback. StubIndex cannot resolve stdlib classes/methods (their roots
            //    are stored under an internal scope no GlobalSearchScope intersects), so when the
            //    index misses for a known receiver class, fall back to the cached stdlib scan.
            //    Prefer the fully-qualified class name (e.g. `Crystal::System::Dir`) so nested
            //    classes resolve to the right file instead of a same-named simple class.
            val lookupClass = if (classNames.size == 1 && qualifiedName != null) qualifiedName else className
            val stdlibMethod = CrystalReference.resolveStdlibMethod(project, lookupClass, methodName)
            if (stdlibMethod != null) return stdlibMethod

            // 4. For .new: fall through to record → initialize resolution
            //    (matches CrystalGotoDeclarationHandler priority: def self.new > record > initialize)
            if (methodName == "new") {
                val file = element.containingFile ?: return null
                val recordDef = CrystalCompletionHelper.findRecordDefinition(className, file)
                if (recordDef != null) return recordDef
                val init = CrystalCompletionHelper.getInitializeMethod(className, project, file)
                if (init != null) return init
                val sym = CrystalReference.resolveStdlibSymbol(project, className)
                if (sym != null) return sym
            }
        }

        // 3. Project-scoped name-only fallback for INSTANCE DOT-calls whose receiver type is
        //    unknown (untyped local/parameter, e.g. `rule.auto_fixable?` where `rule` is a bare
        //    parameter). Resolves by method name within the PROJECT ONLY (never the stdlib), so
        //    we enable local navigation without the cross-project false-positive popups the
        //    strict design otherwise forbids. Returns the first project match; ambiguity across
        //    multiple same-named methods is acceptable for navigation (they are semantically
        //    equivalent definitions of the same message).
        if (classNames.isEmpty()) {
            // CrystalMethodIndex (StubIndex) contains ONLY project methods — stdlib files are
            // skipped by CrystalStubBuilder, so stdlib is never indexed. Querying it therefore
            // cannot produce stdlib false positives; the only possible ambiguity is between
            // same-named project methods, which is acceptable for local navigation.
            val projectMethods = StubIndex.getElements(
                CrystalMethodIndex.KEY, methodName, project,
                scope, CrystalMethodDefinition::class.java
            )
            projectMethods.firstOrNull()?.let { return it }
        }

        return null
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val identNode = element.node.findChildByType(CrystalTypes.IDENTIFIER)
            ?: element.node.findChildByType(CrystalTypes.CONSTANT)
            ?: return element
        val newLeaf = createLeafFromText(element.project, newElementName, identNode.elementType)
            ?: return element
        identNode.treeParent.replaceChild(identNode, newLeaf)
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    /**
     * Returns the receiver class name (for [CrystalMethodByClassIndex] lookup),
     * whether it is a CONSTANT (static) or IDENTIFIER (instance) reference,
     * and the full qualified name (for namespace disambiguation).
     *
     * - CONSTANT receiver: the name IS the class name → exact static lookup.
     * - Namespace receiver (e.g. `Foo::Sub`): walks left to build full path.
     * - IDENTIFIER receiver: infer the variable's type via [CrystalTypeInference].
     *   If no type can be inferred, className stays `null` and resolution aborts.
     */
    private data class ReceiverInfo(
        val classNames: List<String>, // inferred member type names (empty = unknown → no guessing)
        val isStatic: Boolean,
        val rawName: String,
        val qualifiedName: String? = null // full path for disambiguation, null if not a namespace path
    )

    private fun findReceiver(): ReceiverInfo? {
        // Walk siblings before this dot_call_access, skipping whitespace,
        // to find the receiver expression. The DOT is inside this composite,
        // so the receiver lives on the prevSibling of this element.
        var prev = element.prevSibling
        while (prev is PsiWhiteSpace || prev?.node?.elementType.toString() == "WHITE_SPACE"
            || prev?.node?.elementType == CrystalTypes.NEWLINE) {
            prev = prev?.prevSibling
        }
        if (prev == null) return null

        val type = prev.node?.elementType

        // Namespace receiver: previous element is a CrystalNamespaceAccess (e.g. `::Sub` in `Foo::Sub.space`)
        if (prev is CrystalNamespaceAccess) {
            return buildNamespaceReceiver(prev)
        }

        // Direct CONSTANT token (e.g. `Apfel` in `Apfel.tanzen`)
        if (type == CrystalTypes.CONSTANT) {
            return ReceiverInfo(listOf(prev.text), isStatic = true, rawName = prev.text)
        }
        // Composite wrapping a CONSTANT (e.g. variable_reference wrapping `Apfel`).
        val constantChild = prev.node?.findChildByType(CrystalTypes.CONSTANT)
        if (constantChild != null) {
            return ReceiverInfo(listOf(constantChild.text), isStatic = true, rawName = constantChild.text)
        }
        // IDENTIFIER receiver (e.g. `a` in `a.essen`) → infer type(s), including unions.
        if (type == CrystalTypes.IDENTIFIER ||
            prev.node?.findChildByType(CrystalTypes.IDENTIFIER) != null) {
            val varName = prev.text
            // Infer the variable's type(s) — empty list means unknown, no guessing.
            val inferredTypes = CrystalTypeInference.inferTypeList(varName, prev, element.project)
            return ReceiverInfo(inferredTypes, isStatic = false, rawName = varName)
        }
        // `self` receiver (e.g. `self.foo`) → resolve to the enclosing type. `self` is a keyword
        // token (SELF), not an IDENTIFIER, so it was previously missed entirely and every
        // `self.method` call returned null. Works for both instance (`self` = instance) and
        // class (`def self.foo`, `self` = the class) methods.
        if (type == CrystalTypes.SELF) {
            val enclosing = CrystalPsiUtils.getEnclosingType(prev)
            val className = enclosing?.let { CrystalPsiUtils.buildQualifiedName(it) }
            if (className != null) return ReceiverInfo(listOf(className), isStatic = false, rawName = "self")
            return null
        }
        // Literal / expression receiver (e.g. `1.foo`, `"s".upcase`, `[].size`, `{}.keys`)
        // → infer the receiver's type via the expression type resolver. Placed last so it
        // only runs for non-CONSTANT / non-IDENTIFIER / non-self receivers. Descends through
        // any wrapper node (e.g. `primary_expression`) until it reaches a node resolveType
        // understands, and strips generics (Array(Int32) → Array) so the class name matches
        // the symbol-table key.
        val typeName = inferReceiverTypeFromExpression(prev)
        if (typeName != null) {
            return ReceiverInfo(listOf(typeName), isStatic = false, rawName = prev.text)
        }
        return null
    }

    /** Descend through wrapper nodes until [CrystalExpressionTypeResolver] yields a type. */
    private fun inferReceiverTypeFromExpression(expr: PsiElement): String? {
        var current: PsiElement? = expr
        while (current != null) {
            val resolved = CrystalExpressionTypeResolver.resolveType(current)
            if (resolved != null) return resolved.typeName.substringBefore("(")
            current = current.children.firstOrNull { it !is PsiWhiteSpace }
        }
        return null
    }

    /**
     * Builds a [ReceiverInfo] from a [CrystalNamespaceAccess] element by walking
     * left through preceding [CrystalNamespaceAccess] and [CrystalVariableReference]
     * elements to reconstruct the full qualified name.
     *
     * Example: for `Foo::Sub.space`, when called on `::Sub`, returns:
     * ReceiverInfo(className="Sub", qualifiedName="Foo::Sub")
     */
    private fun buildNamespaceReceiver(namespaceAccess: CrystalNamespaceAccess): ReceiverInfo {
        val pathParts = mutableListOf<String>()

        // Get the CONSTANT from this namespace_access
        namespaceAccess.node.findChildByType(CrystalTypes.CONSTANT)?.text?.let { pathParts.add(0, it) }

        // Walk left through preceding namespace_access and variable_reference
        var current = namespaceAccess.prevSibling
        while (current != null) {
            when {
                current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE -> {
                    current = current.prevSibling
                }
                current is CrystalNamespaceAccess -> {
                    current.node.findChildByType(CrystalTypes.CONSTANT)?.text?.let { pathParts.add(0, it) }
                    current = current.prevSibling
                }
                current is CrystalVariableReference -> {
                    current.node.findChildByType(CrystalTypes.CONSTANT)?.text?.let { pathParts.add(0, it) }
                    break
                }
                else -> break
            }
        }

        val simpleName = pathParts.last()
        val qualifiedName = pathParts.joinToString("::")
        return ReceiverInfo(listOf(simpleName), isStatic = true, rawName = simpleName, qualifiedName = qualifiedName)
    }
}