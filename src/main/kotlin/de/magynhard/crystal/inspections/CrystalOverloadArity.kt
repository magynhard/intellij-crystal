package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalParameterList
import de.magynhard.crystal.psi.CrystalCallArgs
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.CrystalPsiCallArguments
import de.magynhard.crystal.psi.CrystalTypes
import de.magynhard.crystal.psi.parameterNameInfo

/**
 * Shared arity-applicability core behind argument-count diagnostics and
 * chained-call receiver resolution. Moved out of
 * `CrystalArgumentCountInspection` unchanged so every consumer evaluates the
 * same overload shapes (defaults, splats, double splats, blocks, named-only
 * parameters after a bare `*`).
 */
internal data class OverloadMatch(
    val isValid: Boolean,
    val missingParams: List<String> = emptyList(),
    val excessStartIndex: Int = -1,
    val maxArgs: Int = 0,
    val unknownNamedArgs: Set<String> = emptySet()
) {
    fun isBetterThan(other: OverloadMatch): Boolean {
        // Prefer the match with fewer missing params.
        if (missingParams.size != other.missingParams.size) {
            return missingParams.size < other.missingParams.size
        }
        // Equally close overloads that omit different required names must
        // rank deterministically instead of following collection order.
        val thisMissing = missingParams.sorted().joinToString("\u0000")
        val otherMissing = other.missingParams.sorted().joinToString("\u0000")
        if (thisMissing != otherMissing) return thisMissing < otherMissing
        val thisUnknown = unknownNamedArgs.sorted().joinToString("\u0000")
        val otherUnknown = other.unknownNamedArgs.sorted().joinToString("\u0000")
        return thisUnknown < otherUnknown
    }
}

internal fun evaluateOverload(
    parameterList: CrystalParameterList?,
    argCount: Int,
    positionalCount: Int,
    namedArgNames: Set<String>
): OverloadMatch {
    val params = parameterList?.parameterList.orEmpty()
    val namedOnlyNames = namedOnlyParameterNames(parameterList)
    val regularParams = mutableListOf<ParamInfo>()
    var hasSplat = false
    var hasDoubleSplat = false

    for (param in params) {
        when {
            param.node.findChildByType(CrystalTypes.AMPERSAND) != null -> continue
            param.node.findChildByType(CrystalTypes.STAR) != null -> { hasSplat = true; continue }
            param.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null -> { hasDoubleSplat = true; continue }
            // Macro-generated splat fragments (`{{ items.splat }}`) expand to an
            // unknown number of parameters: suppress count diagnostics like a splat.
            param.node.findChildByType(CrystalTypes.MACRO_INTERPOLATION) != null -> { hasSplat = true; continue }
        }
        val name = param.parameterNameInfo().callSiteName ?: continue
        val hasDefault = param.expression != null
        regularParams.add(ParamInfo(name, hasDefault, name in namedOnlyNames))
    }

    val paramNames = regularParams.map { it.name }.toSet()
    val requiredParams = regularParams.filter { !it.hasDefault }

    // Check unknown named args (only if no double-splat)
    if (!hasDoubleSplat) {
        val unknown = namedArgNames - paramNames
        if (unknown.isNotEmpty()) {
            return OverloadMatch(isValid = false, unknownNamedArgs = unknown)
        }
    }

    // Check: which required params are satisfied?
    val satisfiedByName = namedArgNames.intersect(requiredParams.map { it.name }.toSet())
    val requiredNotSatisfiedByName = requiredParams.filter { it.name !in satisfiedByName }

    // Named-only parameters (after a bare `*` or a `*splat`) can only be
    // satisfied by name; positional arguments never fill them. Report
    // missing parameters in declaration order to keep messages stable.
    val missing = mutableListOf<String>()
    var positionalSlot = 0
    for (param in requiredNotSatisfiedByName) {
        if (param.namedOnly) {
            missing.add(param.name)
        } else {
            if (positionalSlot >= positionalCount) missing.add(param.name)
            positionalSlot++
        }
    }
    if (missing.isNotEmpty()) {
        return OverloadMatch(isValid = false, missingParams = missing)
    }

    // Check too many args (only if no splat)
    if (!hasSplat) {
        val positionalParams = regularParams.filterNot { it.namedOnly }
        val namedSatisfied = namedArgNames.intersect(positionalParams.map { it.name }.toSet())
        val maxPositional = positionalParams.size - namedSatisfied.size
        if (positionalCount > maxPositional) {
            return OverloadMatch(
                isValid = false,
                excessStartIndex = argCount - (positionalCount - maxPositional),
                maxArgs = regularParams.size
            )
        }
    }

    return OverloadMatch(isValid = true)
}

/**
 * Names of parameters that follow a bare `*` separator or a `*splat`
 * parameter. Crystal requires such parameters to be passed by name, so a
 * positional argument must never satisfy them.
 */
private fun namedOnlyParameterNames(parameterList: CrystalParameterList?): Set<String> {
    val result = mutableSetOf<String>()
    if (parameterList == null) return result
    var namedOnly = false
    for (child in parameterList.node.getChildren(null)) {
        when (child.elementType) {
            CrystalTypes.STAR, CrystalTypes.DOUBLE_STAR -> namedOnly = true
            else -> {
                val param = child.psi as? CrystalParameter ?: continue
                if (namedOnly) {
                    param.parameterNameInfo().callSiteName?.let { result.add(it) }
                }
                if (param.node.findChildByType(CrystalTypes.STAR) != null ||
                    param.node.findChildByType(CrystalTypes.DOUBLE_STAR) != null) {
                    namedOnly = true
                }
            }
        }
    }
    return result
}

internal data class ParamInfo(val name: String, val hasDefault: Boolean, val namedOnly: Boolean = false)

/** Effective call shape, or null when unresolvable splats make the arity unknowable. */
internal data class CallArgCounts(
    val positional: Int,
    val named: Set<String>,
    val total: Int
)

/**
 * Counts the effective shape of one argument holder (a `CrystalCallArgs` or
 * bare-argument list) for arity applicability. Block passes (`&blk`) never
 * count positionally; splats and double splats make the shape unknowable
 * (null) because their expansion is invisible without data-flow resolution.
 * The trailing bare tail of the split `call_args COMMA bare_argument_list`
 * shape counts too, mirroring the inspection's own counting.
 */
internal fun countCallArguments(argumentHolder: PsiElement?): CallArgCounts? {
    if (argumentHolder == null) return CallArgCounts(0, emptySet(), 0)
    val elements = CrystalPsiCallArguments.argumentElements(argumentHolder).toMutableList()
    if (argumentHolder is CrystalCallArgs) {
        elements.addAll(CrystalPsiCallArguments.trailingBareArguments(argumentHolder))
    }
    var positional = 0
    val named = mutableSetOf<String>()
    for (element in elements) {
        val firstType = element.node?.getChildren(null)?.firstOrNull()?.elementType
        // Splat/double-splat expansion is unknowable here: no data-flow
        // literal resolution on this path, so the shape stays unchecked.
        if (firstType == CrystalTypes.STAR || firstType == CrystalTypes.DOUBLE_STAR) return null
        // A block pass is never a positional argument.
        if (firstType == CrystalTypes.AMPERSAND) continue
        val label = CrystalPsiCallArguments.getNamedLabel(element)
        if (label != null) named.add(label) else positional++
    }
    return CallArgCounts(positional, named, positional + named.size)
}
