package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalReceiverMode
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.analysis.CrystalTypeIdentity
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.completion.CrystalTypeCompletionProvider
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Shared unresolved-name predicate for the `CrystalUnresolvedName` inspection
 * and hover documentation.
 *
 * A name is reported only when it resolves to nothing *and* its absence can
 * be proven: indexed-but-unrequired definitions, macro-uncertain enclosing
 * types, incomplete hierarchies, and macro contexts all stay silent, mirroring
 * the suppression-first conventions of the call-argument inspections and
 * dependency-aware completion. See `docs/specs/unresolved-names.md`.
 */
object CrystalUnresolvedName {

    fun messageFor(name: String): String = "Cannot find '$name'"

    /**
     * Top-level methods available through `prelude.cr` without any explicit
     * require, verified against the Crystal distribution (column-0 `def`s in
     * prelude-required files: `kernel`, `concurrent`, `process`,
     * `crystal/main`, `io`, `random`, `signal`). Always known, even without a
     * configured SDK.
     */
    internal val PRELUDE_TOP_LEVEL_METHODS = setOf(
        "abort", "at_exit", "caller", "exit", "gets", "loop",
        "p", "pp", "print", "printf", "puts", "raise", "rand",
        "read_line", "sleep", "spawn", "sprintf", "system",
        // Prelude-internal helpers: callable without require, never warned.
        "consume_unicode_character", "consume_unicode_glob_character",
        "dup_as_array", "pool_slice", "traverse_eh_table", "unescape",
    )

    /**
     * Compiler builtins callable without any `def`/`macro` in the index
     * (`sizeof`, `typeof`, … are compiler intrinsics, not stdlib macros).
     * `require` is a compiler pseudo-keyword, not a resolvable call.
     */
    private val COMPILER_BUILTIN_CALLS = setOf(
        "sizeof", "typeof", "pointerof", "instance_sizeof", "offsetof",
        "alignof", "uninitialized", "require",
    )

    /** Compiler-provided magic constants with no declaration in the index. */
    private val MAGIC_CONSTANTS = setOf(
        "__DIR__", "__FILE__", "__LINE__", "__END_LINE__", "__METHOD__",
    )

    /**
     * Well-known prelude macros (`macros.cr` and `object/properties.cr`, both
     * reachable from `prelude.cr`). A same-named macro makes the name a macro
     * invocation; the index guard below covers project and stdlib macros when
     * they are indexed.
     */
    private val PRELUDE_MACROS = setOf(
        "record", "property", "property!", "property?", "getter", "getter!",
        "getter?", "setter", "delegate", "spawn", "debugger", "pp!", "p!",
    )

    /**
     * True when the bare lowercase name read or called at [context] resolves
     * to nothing provable: no visible local, no prelude baseline entry, no
     * require-visible method/type, no macro, no accessor binding, and no
     * macro-uncertain enclosing type that could still provide an
     * implicit-self member.
     */
    fun isUnresolvedBareName(name: String, context: PsiElement): Boolean {
        if (name in MAGIC_CONSTANTS) return false
        if (name in PRELUDE_TOP_LEVEL_METHODS || name in COMPILER_BUILTIN_CALLS) return false
        if (name in CrystalTypeCompletionProvider.OPTIONAL_STDLIB_TYPES &&
            !hasIndexedType(name, context)
        ) {
            return false
        }

        if (resolvesThroughReference(context)) return false
        if (name in PRELUDE_MACROS || hasMacro(name, context)) return false

        // An implicit-self call inside a macro-uncertain type may still bind
        // to a macro-generated member: silence unless the enclosing type's
        // named-method collection for this name is complete.
        if (!isEnclosingTypeCompleteFor(context, name)) return false
        return true
    }

    /**
     * True when the bare `CONSTANT` read at [context] resolves to nothing
     * provable: no prelude core type, no require-visible type, no same-file
     * assignment, no macro. Like bare names, types indexed only outside the
     * require closure are reported; require-gated stdlib names unknown to the
     * index (no SDK) stay silent. Cross-file non-type constants are out of
     * scope until the constant-declaration index lands.
     */
    fun isUnresolvedConstant(name: String, context: PsiElement): Boolean {
        if (isBaselineConstant(name, context)) return false
        if (resolvesThroughReference(context)) return false
        if (isDeclaredConstant(name, context)) return false
        return true
    }

    private fun isBaselineConstant(name: String, context: PsiElement): Boolean {
        if (name in MAGIC_CONSTANTS) return true
        if (name in CrystalTypeCompletionProvider.CORE_STDLIB_TYPES) return true
        if (name in CrystalTypeCompletionProvider.OPTIONAL_STDLIB_TYPES &&
            !hasIndexedType(name, context)
        ) {
            return true
        }
        return false
    }

    /**
     * True when a constant [name] is declared in a way the full reference
     * resolution may miss: a require-visible indexed type, a same-file
     * assignment or record, or a macro. Used for namespace roots, where the
     * reference resolves the whole path instead of the root segment.
     * Unjudgeable contexts (injections, empty snapshots, broken index) count
     * as declared: silence, never warn.
     */
    private fun isDeclaredConstant(name: String, context: PsiElement): Boolean {
        if (hasSameFileConstantAssignment(context, name)) return true
        if (hasSameFileRecord(name, context)) return true
        if (name in PRELUDE_MACROS || hasMacro(name, context)) return true
        return try {
            val service = CrystalRequireGraphService.getInstance(context.project)
            if (service.isProgramLessInjection(context)) return true
            val sources = service.effectiveSources(context).takeIf { it.files.isNotEmpty() } ?: return true
            CrystalRequireVisibility.isTypeNameVisible(name, context.project, allScope(context), sources)
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Returns the element to flag for a DOT call whose method cannot be
     * resolved, or `null` when the call is known or must stay silent. The
     * receiver is judged first through the require closure: with a visible
     * receiver type only an unresolvable method name is flagged; with an
     * unknown receiver root its first constant leaf is flagged instead
     * (require-closure consistency: unrequired receivers are reported).
     * Suppressed or incomplete resolutions stay silent, and
     * operators/keywords are never method names.
     */
    fun dotCallFlagElement(access: CrystalDotCallAccess): PsiElement? {
        val call = CrystalCallExtractor.extractDotCall(access) ?: return null
        val nameElement = call.methodNameElement
        val nameType = nameElement.node?.elementType
        if (nameType != CrystalTypes.IDENTIFIER && nameType != CrystalTypes.CONSTANT) return null
        val root = CrystalReceiverExpression.extractExactConstantTypeRoot(call.receiver)
            ?: call.receiver.text.takeIf {
                it.removePrefix("::").firstOrNull()?.isUpperCase() == true
            }
        if (root != null) {
            // Generic arguments are not part of declaration identities.
            val cleanRoot = root.substringBefore("(")
            val simpleName = cleanRoot.substringAfterLast("::")
            if (!isReceiverTypeVisible(cleanRoot, simpleName, access)) {
                if (hasSameFileConstantAssignment(access, simpleName)) return null
                if (hasSameFileRecord(simpleName, access)) return null
                return firstConstantLeaf(call.receiver) ?: nameElement
            }
        }
        return if (CrystalDotCallTargetResolver.resolve(access) is DotCallResolution.Unresolved) {
            nameElement
        } else {
            null
        }
    }
    /**
     * Returns the leaf to flag for a qualified `A::B` path, or `null` when the
     * path is known or must stay silent. Only the last chain segment is
     * considered; when its root resolves the member scope is unknown (no
     * member index for enum values or constants), so only an unresolvable
     * root is flagged — on the root leaf itself.
     */
    fun namespaceFlagElement(access: CrystalNamespaceAccess): PsiElement? {
        if (hasFollowingDoubleColon(access)) return null
        return rootFlagElement(collectNamespaceSegments(access), access)
    }

    /**
     * Returns the leaf to flag for a type path (`x : Helper`,
     * `Array(Helper)`, `A | B`), or `null` when known or silent. Single
     * segments use the full bare-constant chain; qualified paths use the
     * root rule, mirroring namespace paths.
     */
    fun typePathFlagElement(path: CrystalTypePath): PsiElement? {
        val pieces = path.node.getChildren(null)
            .map { it.psi }
            .filter { it.node?.elementType == CrystalTypes.CONSTANT }
        if (pieces.isEmpty()) return null
        if (pieces.size == 1) {
            val name = pieces.single().text
            if (name.isBlank()) return null
            return if (isUnresolvedConstant(name, path)) pieces.single() else null
        }
        return rootFlagElement(pieces.map { it.text to it }, path)
    }

    private fun rootFlagElement(
        segments: List<Pair<String, PsiElement>>,
        context: PsiElement,
    ): PsiElement? {
        if (segments.isEmpty()) return null
        val (rootName, rootLeaf) = segments.first()
        if (rootName.isBlank()) return null
        // The reference resolves the whole path, never the root alone, so a
        // resolvable root with an unresolvable member (e.g. `Color::Red`)
        // needs the declared-constant rule instead of the full chain.
        val rootKnown = if (rootName.first().isUpperCase()) {
            isBaselineConstant(rootName, context) || isDeclaredConstant(rootName, context)
        } else {
            !isUnresolvedBareName(rootName, context)
        }
        if (rootKnown) return null
        return rootLeaf
    }

    private fun resolvesThroughReference(context: PsiElement): Boolean {
        return try {
            val ref = context.reference ?: context.parent?.reference ?: return false
            ref.resolve() != null
        } catch (_: Throwable) {
            // A broken index entry must silence, never warn.
            true
        }
    }

    private fun hasIndexedType(name: String, context: PsiElement): Boolean {
        return try {
            CrystalRequireVisibility.hasIndexedType(name, context.project, allScope(context))
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasMacro(name: String, context: PsiElement): Boolean {
        return try {
            CrystalIndexService.findMacros(name, context.project, allScope(context)).isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasSameFileConstantAssignment(context: PsiElement, name: String): Boolean {
        val file = context.containingFile ?: return false
        return try {
            PsiTreeUtil.findChildrenOfType(file, CrystalConstantAssignment::class.java).any { assignment ->
                assignment.node.findChildByType(CrystalTypes.CONSTANT)?.text == name
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * True when an implicit-self bare call could still bind to the enclosing
     * type: either there is no enclosing type (top-level has no implicit
     * members beyond top-level defs, checked above), the enclosing type
     * cannot be established (conservative silence), or its named-method
     * collection for [name] is incomplete (macro-uncertain members,
     * unresolvable edges). Only a complete-but-empty collection proves absence.
     */
    private fun isEnclosingTypeCompleteFor(context: PsiElement, name: String): Boolean {
        return try {
            val enclosing = CrystalPsiUtils.getEnclosingType(context) ?: return true
            val qualified = CrystalPsiUtils.buildQualifiedName(enclosing) ?: return false
            val identity = CrystalTypeIdentity(
                qualified.substringAfterLast("::"),
                qualified,
            )
            val session = CrystalTypeSetResolver.session(context)
            session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, name).complete
        } catch (_: Throwable) {
            false
        }
    }

    private fun isReceiverTypeVisible(root: String, simpleName: String, context: PsiElement): Boolean {
        return try {
            val candidates = CrystalIndexService.findTypes(simpleName, context.project, allScope(context))
            candidates.any { candidate ->
                CrystalPsiUtils.buildQualifiedName(candidate) == root &&
                    CrystalRequireVisibility.isVisible(candidate, context)
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun firstConstantLeaf(element: PsiElement): PsiElement? {
        // Node-level traversal: token leaves are not exposed as PSI children.
        val node = element.node ?: return null
        if (node.elementType == CrystalTypes.CONSTANT) return element
        node.getChildren(null).forEach { child ->
            firstConstantLeaf(child.psi)?.let { return it }
        }
        return null
    }

    private fun hasFollowingDoubleColon(access: CrystalNamespaceAccess): Boolean {
        var sibling = access.nextSibling
        while (sibling is PsiWhiteSpace || sibling?.node?.elementType == CrystalTypes.NEWLINE) {
            sibling = sibling.nextSibling
        }
        return sibling?.node?.elementType == CrystalTypes.DOUBLE_COLON
    }

    private fun collectNamespaceSegments(access: CrystalNamespaceAccess): List<Pair<String, PsiElement>> {
        val segments = mutableListOf<Pair<String, PsiElement>>()
        val ownConstant = access.node.findChildByType(CrystalTypes.CONSTANT)?.psi
            ?: return emptyList()
        var current: PsiElement? = access.prevSibling
        while (current != null) {
            when {
                current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE -> {
                    current = current.prevSibling
                }
                current is CrystalNamespaceAccess -> {
                    val constant = current.node.findChildByType(CrystalTypes.CONSTANT)?.psi
                        ?: return emptyList()
                    segments.add(0, constant.text to constant)
                    current = current.prevSibling
                }
                current is CrystalVariableReference -> {
                    val constant = current.node.findChildByType(CrystalTypes.CONSTANT)?.psi
                    if (constant != null) segments.add(0, constant.text to constant)
                    // A lowercase root is a value, not a namespace segment, and
                    // ends the constant path.
                    break
                }
                else -> break
            }
        }
        segments.add(ownConstant.text to ownConstant)
        return segments
    }

    private fun hasSameFileRecord(name: String, context: PsiElement): Boolean {
        val file = context.containingFile ?: return false
        return try {
            CrystalPsiUtils.findRecordDefinitions(name, file).isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    private fun allScope(context: PsiElement) = GlobalSearchScope.allScope(context.project)

    /**
     * Returns the warnable name leaf of a `variable_reference` (bare read or
     * bare `CONSTANT`), or `null` when the position is not a name read:
     * definition names, member-access receivers and roots, symbol keys,
     * `case ... in` patterns (owned by `CrystalInvalidInPattern`), globals,
     * and keyword spellings.
     *
     * The boolean is true for `CONSTANT` leaves.
     */
    fun variableReferenceLeaf(reference: CrystalVariableReference): Pair<PsiElement, Boolean>? {
        val parent = reference.parent
        if (parent is CrystalNamedElement) return null
        if (parent?.parent is CrystalNamedElement) return null
        // Write targets: an assignment target wrapped in a reference
        // composite (e.g. grouped `(grouped = 1)`) binds rather than reads.
        // The reference is a target when it sits before the assignment's
        // `=`; reads on the right-hand side keep resolving normally.
        if (isAssignmentTarget(reference)) return null
        if (PsiTreeUtil.getParentOfType(reference, CrystalInClause::class.java) != null) return null
        // Arguments of macro invocations are macro data, not runtime names:
        // `record Config, …`, `property name`, `getter age` declare API
        // surface through expansion — never measured against defs.
        if (isInsideMacroInvocation(reference)) return null

        val leaf = reference.node.findChildByType(CrystalTypes.IDENTIFIER)?.psi
            ?: reference.node.findChildByType(CrystalTypes.CONSTANT)?.psi
            ?: return null
        val leafType = leaf.node?.elementType
        if (leafType == CrystalTypes.GLOBAL_VAR) return null
        if (leafType != CrystalTypes.IDENTIFIER && leafType != CrystalTypes.CONSTANT) return null

        if (isAdjacentToMemberAccess(leaf)) return null
        if (isHashKeyColon(leaf)) return null

        val name = leaf.text
        if (name.isBlank()) return null
        return leaf to (leafType == CrystalTypes.CONSTANT)
    }

    /**
     * Returns the warnable callee leaf of a non-DOT call expression, or
     * `null` when the call has a DOT shape (owned by the DOT path) or the
     * callee is not a plain name.
     */
    fun callCalleeLeaf(callExpr: PsiElement): Pair<PsiElement, Boolean>? {
        if (callExpr.node?.findChildByType(CrystalTypes.DOT) != null) return null
        if (isInsideMacroInvocation(callExpr, skipSelfCall = true)) return null
        val callee = CrystalCallExtractor.findMethodNameElement(callExpr) ?: return null
        if (isKeywordSpelling(callee)) return null
        val calleeType = callee.node?.elementType
        if (calleeType != CrystalTypes.IDENTIFIER && calleeType != CrystalTypes.CONSTANT) return null
        val name = callee.text
        if (name.isBlank()) return null
        return callee to (calleeType == CrystalTypes.CONSTANT)
    }

    /** True when [leaf] (or the element) is unresolved and must be reported. */
    fun isUnresolvedLeaf(leaf: PsiElement, isConstant: Boolean, context: PsiElement): Boolean {
        val name = leaf.text
        return if (isConstant) isUnresolvedConstant(name, context)
        else isUnresolvedBareName(name, context)
    }

    /**
     * True when [element] sits inside the argument list of a macro invocation:
     * macro arguments are expansion data, never runtime name reads. The
     * immediate call itself is skipped for callees ([skipSelfCall]), since a
     * callee trivially sits inside its own call.
     */
    private fun isInsideMacroInvocation(element: PsiElement, skipSelfCall: Boolean = false): Boolean {
        var start: PsiElement? = if (skipSelfCall) element.parent else element
        // A callee leaf's parent is its own call — step over it as well.
        if (skipSelfCall && start is CrystalMethodCallExpression) start = start.parent
        if (skipSelfCall && start is CrystalBareMethodCallExpression) start = start.parent
        val owningCall = generateSequence(start) { it.parent }
            .takeWhile { it !is PsiFile }
            .firstOrNull {
                it is CrystalMethodCallExpression || it is CrystalBareMethodCallExpression
            } ?: return false
        val callName = CrystalCallExtractor.extractMethodName(owningCall) ?: return false
        if (callName in PRELUDE_MACROS) return true
        return try {
            CrystalIndexService.findMacros(
                callName, element.project, GlobalSearchScope.allScope(element.project),
            ).isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * True when [reference] is a write target rather than a read: the target
     * of an enclosing assignment (sitting before its `=`), or the grouped
     * `(grouped = …)` binding identifier (mirroring
     * `CrystalLocalUsageAnalyzer`, which tracks the same binding).
     */
    private fun isAssignmentTarget(reference: CrystalVariableReference): Boolean {
        var current: PsiElement? = reference
        while (current != null && current !is PsiFile) {
            val parent = current.parent ?: return false
            if (parent is CrystalAssignment) {
                val assignToken = parent.node.findChildByType(CrystalTypes.ASSIGN) ?: return false
                return reference.textRange.startOffset < assignToken.startOffset
            }
            if (parent is CrystalGroupedExpression && parent.expressionList.size >= 2) {
                if (parent.expressionList.first().children.singleOrNull() === reference) return true
            }
            current = parent
        }
        return false
    }

    /** Keyword spellings are language syntax, never unresolved names. */
    fun isKeywordSpelling(element: PsiElement): Boolean {        val type = element.node?.elementType ?: return false
        return CrystalTokenTypes.KEYWORDS.contains(type) || CrystalTokenTypes.OPERATORS.contains(type) ||
            CrystalTokenTypes.KEYWORD_VARIABLES.contains(type)
    }

    /** Receivers (`foo` in `foo.bar`) and namespace roots (`A` in `A::B`). */
    private fun isAdjacentToMemberAccess(leaf: PsiElement): Boolean {
        var previous = leaf.prevSibling
        while (previous is PsiWhiteSpace || previous?.node?.elementType == CrystalTypes.NEWLINE) {
            previous = previous.prevSibling
        }
        // A preceding DOT/DOUBLE_COLON makes the leaf a receiver or root, and
        // a following one hands ownership to the DOT/namespace path. The leaf
        // itself may sit inside a wrapper (e.g. an argumentless call), so the
        // DOT check also considers the wrapper's next sibling.
        if (previous?.node?.elementType == CrystalTypes.DOT ||
            previous?.node?.elementType == CrystalTypes.DOUBLE_COLON
        ) {
            return true
        }
        var next: PsiElement? = leaf
        while (next != null) {
            var candidate = next.nextSibling
            while (candidate is PsiWhiteSpace || candidate?.node?.elementType == CrystalTypes.NEWLINE) {
                candidate = candidate.nextSibling
            }
            val type = candidate?.node?.elementType
            if (type == CrystalTypes.DOT || type == CrystalTypes.DOUBLE_COLON) return true
            if (candidate is CrystalDotCallAccess || candidate is CrystalNamespaceAccess) return true
            next = if (candidate == null) next.parent else null
        }
        return false
    }

    /** Hash-entry keys (`{error: …}`) are symbol-like keys, not name reads. */
    private fun isHashKeyColon(leaf: PsiElement): Boolean {
        val entry = PsiTreeUtil.getParentOfType(leaf, CrystalHashEntry::class.java) ?: return false
        if (entry.node.findChildByType(CrystalTypes.COLON) == null) return false
        val keyExpression = entry.node.getChildren(null)
            .firstOrNull { it.elementType == CrystalTypes.EXPRESSION } ?: return false
        return PsiTreeUtil.isAncestor(keyExpression.psi, leaf, false)
    }
}
