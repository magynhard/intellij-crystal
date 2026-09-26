package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import de.magynhard.crystal.analysis.CrystalReceiverMode
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalTypeResolutionSession
import de.magynhard.crystal.psi.CrystalBracketCallAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalReceiverExpression
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Exact `[]` targets for a bracket-call access (`Foo[]`, `Foo[1]`, `obj[0]`),
 * or empty when the receiver is unresolvable or ambiguous, or declares no
 * matching `[]`. Constant roots resolve in static mode (`def self.[]`);
 * value receivers resolve through type inference in instance mode. Macro-backed
 * targets (`Int64[]` via the Number `[]` macro) resolve through the existing
 * Number path instead — macros are not method definitions, so they never
 * appear here.
 */
internal object CrystalBracketCallResolver {

    fun resolveMethods(access: CrystalBracketCallAccess): List<CrystalMethodDefinition> {
        return try {
            resolveMethodsInner(access)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun resolveMethodsInner(access: CrystalBracketCallAccess): List<CrystalMethodDefinition> {
        val receiver = previousSignificantSibling(access) ?: return emptyList()
        val session = CrystalTypeSetResolver.session(access)
        val root = CrystalReceiverExpression.extractExactConstantTypeRoot(receiver)
            ?: receiver.text.takeIf {
                it.removePrefix("::").firstOrNull()?.isUpperCase() == true
            }
        if (root != null) {
            val identity = session.resolveType(root.removePrefix("::"), access) ?: return emptyList()
            val collection = session.collectNamedMethods(identity, CrystalReceiverMode.STATIC, "[]")
            if (!collection.complete) return emptyList()
            return collection.methods
        }
        return instanceMethods(receiver, session, access)
    }

    /**
     * Instance `[]` targets for a value receiver (`obj[0]`, `arr[i]`): every
     * inferred type arm must yield a complete `[]` collection, otherwise the
     * call stays suppressed instead of guessing across a union.
     */
    private fun instanceMethods(
        receiver: PsiElement,
        session: CrystalTypeResolutionSession,
        access: CrystalBracketCallAccess,
    ): List<CrystalMethodDefinition> {
        val known = session.resolve(receiver) as? CrystalTypeResolution.Known ?: return emptyList()
        if (known.types.isEmpty()) return emptyList()
        val result = mutableListOf<CrystalMethodDefinition>()
        for (type in known.types) {
            val identity = session.resolveType(type.name, access) ?: return emptyList()
            val collection = session.collectNamedMethods(identity, CrystalReceiverMode.INSTANCE, "[]")
            if (!collection.complete) return emptyList()
            result.addAll(collection.methods)
        }
        return result.distinct()
    }

    private fun previousSignificantSibling(element: PsiElement): PsiElement? {
        var current = element.prevSibling
        while (current != null && (current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE)) {
            current = current.prevSibling
        }
        return current
    }
}
