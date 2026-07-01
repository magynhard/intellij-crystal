package de.magynhard.crystal.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import de.magynhard.crystal.completion.CrystalTypeInference
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
 *    full qualified name, resolves via [CrystalClassIndex], then filters methods
 *    by the enclosing class's qualified name to avoid ambiguity (Foo::Sub vs Bar::Sub).
 *
 * 3. IDENTIFIER receiver (e.g. `a.essen` where `a = Apfel.new`):
 *    - Call [CrystalTypeInference.inferType] on the receiver variable name.
 *    - If a concrete type is returned, resolve via [CrystalMethodByClassIndex]
 *      keyed by the inferred type, filtering by method name.
 *    - If the type is **unknown** (untyped parameter, untyped return chain, …),
 *      return `null` — no jump, no false-positive popup.
 *
 * 4. `.new` constructor on a class — the IDENTIFIER `new` is NOT in
 *    [CrystalMethodByClassIndex] for the default constructor. This returns `null`
 *    here; `CrystalGotoDeclarationHandler` and `CrystalDocumentationProvider`
 *    still handle `.new` via the dedicated `findNewTargets` path.
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

        // Don't resolve `.new` here — the dedicated constructor path handles it.
        if (methodName == "new") return null

        val project = element.project
        val scope = GlobalSearchScope.allScope(project)

        val className = info.className ?: return null
        val qualifiedName = info.qualifiedName

        // Resolve via CrystalMethodByClassIndex — exact, no name-only guessing.
        val methods = StubIndex.getElements(
            CrystalMethodByClassIndex.KEY, className, project, scope,
            CrystalMethodDefinition::class.java
        )

        // Filter by method name
        val matchingByName = methods.filter { it.name == methodName }

        // Filter by qualified class name if available (for namespace disambiguation)
        return if (qualifiedName != null) {
            matchingByName.filter { method ->
                val enclosing = CrystalPsiUtils.getEnclosingType(method)
                enclosing != null && CrystalPsiUtils.buildQualifiedName(enclosing) == qualifiedName
            }.firstOrNull()
        } else {
            matchingByName.firstOrNull()
        }
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
        val className: String?,        // simple name for CrystalMethodByClassIndex lookup
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
            return ReceiverInfo(prev.text, isStatic = true, rawName = prev.text)
        }
        // Composite wrapping a CONSTANT (e.g. variable_reference wrapping `Apfel`).
        val constantChild = prev.node?.findChildByType(CrystalTypes.CONSTANT)
        if (constantChild != null) {
            return ReceiverInfo(constantChild.text, isStatic = true, rawName = constantChild.text)
        }
        // IDENTIFIER receiver (e.g. `a` in `a.essen`) → infer type.
        if (type == CrystalTypes.IDENTIFIER ||
            prev.node?.findChildByType(CrystalTypes.IDENTIFIER) != null) {
            val varName = prev.text
            // Infer the variable's type — no guessing if unknown.
            val inferredType = CrystalTypeInference.inferType(varName, prev, element.project)
            return ReceiverInfo(inferredType, isStatic = false, rawName = varName)
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
        return ReceiverInfo(simpleName, isStatic = true, rawName = simpleName, qualifiedName = qualifiedName)
    }
}