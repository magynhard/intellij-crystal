package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.CrystalTypeSetResolver

/** Compatibility facade for inspection consumers that still expect one rendered type string. */
object CrystalExpressionTypeResolver {
    data class ResolvedType(
        val typeName: String,
        val isUnsuffixedNumericLiteral: Boolean = false
    )

    fun resolveType(expr: PsiElement): ResolvedType? =
        when (val result = CrystalTypeSetResolver.resolve(expr)) {
            is CrystalTypeResolution.Known -> ResolvedType(
                result.types.joinToString(" | ") { it.name },
                result.types.size == 1 && result.types.single().isUnsuffixedNumericLiteral
            )
            CrystalTypeResolution.Unknown -> null
        }
}
