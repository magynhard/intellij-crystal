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
        return if (collection.methods.isEmpty()) {
            DotCallResolution.Unresolved
        } else {
            DotCallResolution.Methods(call, receiverType, collection.methods)
        }
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
