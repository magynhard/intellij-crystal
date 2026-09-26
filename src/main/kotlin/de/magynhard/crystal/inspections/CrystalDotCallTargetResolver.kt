package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalReceiverMode
import de.magynhard.crystal.analysis.CrystalTypeIdentity
import de.magynhard.crystal.analysis.CrystalTypeResolutionSession
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalFunDefinition
import de.magynhard.crystal.psi.CrystalMacroContext
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.navigation.CrystalAccessorCoupling
import de.magynhard.crystal.psi.CrystalReceiverExpression
import de.magynhard.crystal.stubs.CrystalFunDefinitionStub
import de.magynhard.crystal.stubs.CrystalIndexService

sealed interface DotCallResolution {
    data class Methods(
        val call: DotCallDescriptor,
        val receiverType: ExactReceiverType,
        val methods: List<CrystalMethodDefinition>
    ) : DotCallResolution

    data class ImplicitConstructor(
        val call: DotCallDescriptor,
        val receiverType: ExactReceiverType
    ) : DotCallResolution

    data class RecordFallback(
        val call: DotCallDescriptor,
        val receiverName: String,
        val qualifiedName: String,
        val recordDefinition: CrystalMethodCallExpression
    ) : DotCallResolution

    /**
     * An accessor-macro binding: the receiver type declares `property foo` /
     * `getter? foo` / `class_property foo` (whole family,好人 suffix count?) …
     * the accessor ARGUMENT is the declaration — the generated reader/setter
     * methods have no PSI of their own.
     */
    data class Accessor(
        val call: DotCallDescriptor,
        val receiverType: ExactReceiverType,
        val accessorArgs: List<com.intellij.psi.PsiElement>
    ) : DotCallResolution

    /**
     * An FFI call resolved through the lib-fun index (`LibC.exit`): the
     * receiver is a library identity, not a type, and the targets are `fun`
     * declarations (possibly several identical ones across platform files).
     * Macros, unknown, and ambiguous targets never reach this variant.
     */
    data class LibFunctions(
        val call: DotCallDescriptor,
        val funs: List<CrystalFunDefinition>
    ) : DotCallResolution

    data object Unresolved : DotCallResolution
    data object Suppressed : DotCallResolution
}

object CrystalDotCallTargetResolver {

    /**
     * Maximum nesting of completed preceding calls resolved as receiver
     * evidence (`a.b.c.d.e` needs three levels). Bounds hierarchy lookups on
     * pathological chains; deeper chains stay suppressed.
     */
    private const val MAX_CHAIN_DEPTH = 4

    fun resolve(access: CrystalDotCallAccess): DotCallResolution =
        resolve(access, CrystalTypeSetResolver.session(access))

    internal fun resolve(
        access: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession,
        depth: Int = 0
    ): DotCallResolution {
        // Macro context (`{{ … }}` interpolations, macro bodies): receivers
        // are macro-runtime objects (TypeNode, StringLiteral, …) dispatching
        // to the `Crystal::Macros` compiler API, never to runtime defs —
        // resolving them would navigate to false targets.
        if (CrystalMacroContext.isInMacroContext(access)) return DotCallResolution.Suppressed
        val call = CrystalCallExtractor.extractDotCall(access) ?: return DotCallResolution.Unresolved
        if (containsMacroInterpolation(call.receiver) || containsMacroInterpolation(call.methodNameElement)) {
            return DotCallResolution.Suppressed
        }

        // Chained call (`env.status(:not_found).json(...)`): the receiver is
        // itself a completed DOT call. Its annotated return type becomes the
        // receiver evidence — but only when the preceding call is applicable
        // and unambiguous (see resolveChainReceiver). Anything else stays
        // suppressed exactly like before.
        if (call.receiver is CrystalDotCallAccess) {
            if (depth >= MAX_CHAIN_DEPTH) return DotCallResolution.Suppressed
            val chained = resolveChainReceiver(call.receiver, call.access, session, depth)
                ?: return DotCallResolution.Suppressed
            return finishResolve(call, chained, ReceiverMode.INSTANCE, session)
        }

        val normalizedReceiver = CrystalReceiverExpression.normalize(call.receiver)
        val exactTypeRoot = CrystalReceiverExpression.extractExactConstantTypeRoot(call.receiver)
        if (normalizedReceiver is CrystalMethodCallExpression && exactTypeRoot == null) {
            return DotCallResolution.Suppressed
        }
        val normalizedReceiverText = exactTypeRoot ?: call.receiverText
        val constantReceiver = exactTypeRoot != null ||
            normalizedReceiver === call.receiver && isConstantReceiver(call.receiverText)
        if (constantReceiver && call.methodName == "new") {
            return resolveConstructor(call, session.resolveConstructor(normalizedReceiverText, call.access))
        }
        val receiverType = if (constantReceiver) {
            val identity = session.resolveType(normalizedReceiverText, call.access)
            if (identity == null) {
                // Library receiver (`LibC.exit`): types never resolve — the
                // lib-fun index owns these calls.
                return resolveLibCall(call, normalizedReceiverText)
            }
            ExactReceiverType(identity.simpleName, identity.qualifiedName)
        } else {
            CrystalExactReceiverTypeResolver.resolve(normalizedReceiver, call.access, session)
                ?: return DotCallResolution.Suppressed
        }

        val mode = if (constantReceiver) ReceiverMode.STATIC else ReceiverMode.INSTANCE
        return finishResolve(call, receiverType, mode, session)
    }

    /**
     * Shared tail behind every receiver shape: collect the exact candidate
     * methods, suppress incomplete hierarchies, fall back to accessor-macro
     * bindings, and report the methods or the unresolved name.
     */
    private fun finishResolve(
        call: DotCallDescriptor,
        receiverType: ExactReceiverType,
        mode: ReceiverMode,
        session: CrystalTypeResolutionSession
    ): DotCallResolution {
        val collection = collectMethods(receiverType, mode, call.methodName, session)
        if (!collection.complete) return DotCallResolution.Suppressed
        if (collection.methods.isEmpty()) {
            // Accessor macros (`property foo` … class_* family) declare their
            // reader/setter methods purely in macro-land: the ARGUMENT is the
            // declaration, so an unresolved method name falls through to the
            // accessor binding of the receiver type.
            val accessorArgs = collectAccessorArguments(call, receiverType, mode, session)
            if (accessorArgs.isNotEmpty()) return DotCallResolution.Accessor(call, receiverType, accessorArgs)
            return DotCallResolution.Unresolved
        }
        return DotCallResolution.Methods(call, receiverType, collection.methods)
    }

    /**
     * Receiver evidence from a completed preceding call
     * (`env.status(:not_found)` as the receiver of `.json(...)`): the inner
     * call resolves through this same resolver, so only exact (never
     * name-only) targets participate. The inner arguments must satisfy at
     * least one overload by arity, every applicable overload must carry the
     * same annotated return type (absent annotations are never inferred, and
     * union or nilable returns stay suppressed like other union receivers),
     * and the return must resolve to one exact type identity. Constructor,
     * accessor, lib-fun, and macro-spliced predecessors stay suppressed.
     */
    private fun resolveChainReceiver(
        innerAccess: CrystalDotCallAccess,
        context: PsiElement,
        session: CrystalTypeResolutionSession,
        depth: Int
    ): ExactReceiverType? {
        val inner = resolve(innerAccess, session, depth + 1)
        val methods = (inner as? DotCallResolution.Methods) ?: return null
        val args = methods.call.argumentHolder
        if (containsMacroInterpolation(args)) return null
        val counts = countCallArguments(args) ?: return null
        val applicable = methods.methods.filter {
            evaluateOverload(it.parameterList, counts.total, counts.positional, counts.named).isValid
        }
        if (applicable.isEmpty()) return null
        val returns = applicable.map {
            it.typeReference?.text?.filterNot(Char::isWhitespace) ?: return null
        }.distinct()
        if (returns.size != 1) return null
        val returnText = returns.single()
        // Union and nilable chain results have no single exact identity —
        // they stay suppressed like any other union receiver.
        if (returnText.contains("|") || returnText.endsWith("?")) return null
        val identity = session.resolveType(returnText, context) ?: return null
        return ExactReceiverType(identity.simpleName, identity.qualifiedName)
    }

    /**
     * Resolves an FFI call (`LibC.exit`) through the lib-fun index. Only exact
     * qualified library identities resolve: every candidate library must share
     * the receiver's qualified name and be require-visible, and the surviving
     * `fun` declarations must agree on one signature — platform-duplicated
     * declarations (e.g. per-OS `LibC` files) collapse, genuinely different
     * ones suppress. Unknown, ambiguous, and macro-spliced targets stay
     * suppressed exactly like unknown types.
     */
    private fun resolveLibCall(
        call: DotCallDescriptor,
        rootText: String
    ): DotCallResolution {
        return try {
            resolveLibCallInner(call, rootText)
        } catch (_: Throwable) {
            DotCallResolution.Suppressed
        }
    }

    private fun resolveLibCallInner(
        call: DotCallDescriptor,
        rootText: String
    ): DotCallResolution {
        val project = call.access.project
        val scope = GlobalSearchScope.allScope(project)
        val cleanRoot = rootText.removePrefix("::").substringBefore("(").trim()
        val simpleRoot = cleanRoot.substringAfterLast("::")
        if (simpleRoot.isEmpty() || !simpleRoot.first().isUpperCase()) {
            return DotCallResolution.Suppressed
        }
        val libs = CrystalIndexService.findLibs(simpleRoot, project, scope).filter { lib ->
            CrystalPsiUtils.libQualifiedName(lib) == cleanRoot &&
                CrystalRequireVisibility.isVisible(lib, call.access)
        }
        if (libs.isEmpty()) return DotCallResolution.Suppressed
        val owners = libs.mapNotNull { CrystalPsiUtils.libQualifiedName(it) }.toSet()
        val candidates = CrystalIndexService.findLibFunctions(call.methodName, project, scope)
            .filter { funDef ->
                funOwnerOf(funDef) in owners &&
                    CrystalRequireVisibility.isVisible(funDef, call.access)
            }
        if (candidates.isEmpty()) return DotCallResolution.Suppressed
        val groups = candidates.groupBy { signatureOf(it)?.key() ?: "unmodelable" }
        if (groups.size != 1) return DotCallResolution.Suppressed
        return DotCallResolution.LibFunctions(call, groups.values.single())
    }

    /**
     * Owner of a `fun` declaration: the stub's recorded owner first (no AST
     * load), falling back to the PSI parent walk. Both use the same
     * lib-chain rule, so indexed and freshly parsed declarations agree.
     */
    private fun funOwnerOf(funDef: CrystalFunDefinition): String? {
        val stubOwner = (funDef as? StubBasedPsiElement<*>)?.stub
            ?.let { it as? CrystalFunDefinitionStub }?.ownerQualifiedName
        if (stubOwner != null) return stubOwner
        return CrystalPsiUtils.libOwnerQualifiedName(funDef)
    }

    private fun resolveConstructor(
        call: DotCallDescriptor,
        result: CrystalConstructorResolution
    ): DotCallResolution {
        val receiverType = ExactReceiverType(result.identity.simpleName, result.identity.qualifiedName)
        return when (result) {
            is CrystalConstructorResolution.Methods -> DotCallResolution.Methods(call, receiverType, result.methods)
            is CrystalConstructorResolution.Implicit -> DotCallResolution.ImplicitConstructor(call, receiverType)
            is CrystalConstructorResolution.Record -> DotCallResolution.RecordFallback(
                call,
                result.identity.simpleName,
                result.identity.qualifiedName,
                result.recordDefinition
            )
            is CrystalConstructorResolution.Abstract -> DotCallResolution.Suppressed
            is CrystalConstructorResolution.Incomplete -> DotCallResolution.Suppressed
            is CrystalConstructorResolution.Unavailable -> DotCallResolution.Unresolved
        }
    }

    private fun collectMethods(
        receiverType: ExactReceiverType,
        mode: ReceiverMode,
        methodName: String,
        session: CrystalTypeResolutionSession,
        actualSelf: Boolean? = null,
        includeModuleEdges: Boolean = true
    ): MethodCollection {
        val result = session.collectNamedMethods(
            CrystalTypeIdentity(receiverType.simpleName, receiverType.qualifiedName),
            if (mode == ReceiverMode.STATIC) CrystalReceiverMode.STATIC else CrystalReceiverMode.INSTANCE,
            methodName,
            actualSelf,
            includeModuleEdges
        )
        return MethodCollection(result.methods, result.complete)
    }


    /**
     * Accessor-macro name arguments of the receiver type matching the called
     * method shape: `foo` (plain reader), `foo?`/`foo!` (suffix variants), or
     * `foo=` (setter, from `obj.foo = v`). Instance mode binds the plain
     * family, static mode (`Session.timeout`, `session.foo=` — an instance
     * receiver never binds `self.timeout`) the `class_*` family.
     */
    private fun collectAccessorArguments(
        call: DotCallDescriptor,
        receiverType: ExactReceiverType,
        mode: ReceiverMode,
        session: CrystalTypeResolutionSession,
    ): List<PsiElement> {
        val allowed = CrystalAccessorCoupling.allowedAccessorMacros(call.methodName) ?: return emptyList()
        val propertyName = call.methodName
            .removeSuffix("=").removeSuffix("?").removeSuffix("!")
        if (propertyName.isEmpty()) return emptyList()
        val declarations = session.findExactTypeDeclarations(
            CrystalTypeIdentity(receiverType.simpleName, receiverType.qualifiedName),
        )
        return declarations.flatMap { declaration ->
            CrystalAccessorCoupling.accessorArgsMatching(
                declaration,
                propertyName,
                allowed,
                classMacrosOnly = mode == ReceiverMode.STATIC,
            )
        }
    }

    private fun containsMacroInterpolation(element: PsiElement): Boolean =
        element is de.magynhard.crystal.psi.CrystalMacroInterpolation ||
            PsiTreeUtil.findChildOfType(
                element,
                de.magynhard.crystal.psi.CrystalMacroInterpolation::class.java,
                false
            ) != null

    private fun isConstantReceiver(receiverText: String): Boolean =
        receiverText.removePrefix("::").firstOrNull()?.isUpperCase() == true



    private enum class ReceiverMode { STATIC, INSTANCE }

    private data class MethodCollection(
        val methods: List<CrystalMethodDefinition>,
        val complete: Boolean
    )

}
