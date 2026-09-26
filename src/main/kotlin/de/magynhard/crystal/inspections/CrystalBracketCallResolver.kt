package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import de.magynhard.crystal.analysis.CrystalReceiverMode
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.psi.CrystalBracketCallAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalReceiverExpression
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Exact static `[]` targets for a bracket-call access (`Foo[]`, `Foo[1]`),
 * or empty when the receiver is not an exact constant type root, is
 * unresolvable or ambiguous, or declares no `def self.[]`. Macro-backed
 * targets (`Int64[]` via the Number `[]` macro) resolve through the existing
 * Number path instead — macros are not method definitions, so they never
 * appear here. Instance receivers stay suppressed: index reads keep their
 * own resolution.
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
        val root = CrystalReceiverExpression.extractExactConstantTypeRoot(receiver)
            ?: receiver.text.takeIf {
                it.removePrefix("::").firstOrNull()?.isUpperCase() == true
            }
            ?: return emptyList()
        val session = CrystalTypeSetResolver.session(access)
        val identity = session.resolveType(root.removePrefix("::"), access) ?: return emptyList()
        val collection = session.collectNamedMethods(identity, CrystalReceiverMode.STATIC, "[]")
        if (!collection.complete) return emptyList()
        return collection.methods
    }

    private fun previousSignificantSibling(element: PsiElement): PsiElement? {
        var current = element.prevSibling
        while (current != null && (current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE)) {
            current = current.prevSibling
        }
        return current
    }
}
