package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.analysis.CrystalReceiverMode
import de.magynhard.crystal.analysis.CrystalTypeIdentity
import de.magynhard.crystal.analysis.CrystalTypeResolutionSession
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.navigation.CrystalAccessorCoupling
import de.magynhard.crystal.psi.CrystalReceiverExpression

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

    data object Unresolved : DotCallResolution
    data object Suppressed : DotCallResolution
}

object CrystalDotCallTargetResolver {

    fun resolve(access: CrystalDotCallAccess): DotCallResolution =
        resolve(access, CrystalTypeSetResolver.session(access))

    internal fun resolve(
        access: CrystalDotCallAccess,
        session: CrystalTypeResolutionSession
    ): DotCallResolution {
        val call = CrystalCallExtractor.extractDotCall(access) ?: return DotCallResolution.Unresolved
        if (containsMacroInterpolation(call.receiver) || containsMacroInterpolation(call.methodNameElement)) {
            return DotCallResolution.Suppressed
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
                ?: return DotCallResolution.Suppressed
            ExactReceiverType(identity.simpleName, identity.qualifiedName)
        } else {
            CrystalExactReceiverTypeResolver.resolve(normalizedReceiver, call.access, session)
                ?: return DotCallResolution.Suppressed
        }

        val mode = if (constantReceiver) ReceiverMode.STATIC else ReceiverMode.INSTANCE
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
