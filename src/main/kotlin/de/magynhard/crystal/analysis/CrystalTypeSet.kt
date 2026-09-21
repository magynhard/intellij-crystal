package de.magynhard.crystal.analysis

internal data class CrystalResolvedType(
    val name: String,
    val isUnsuffixedNumericLiteral: Boolean = false
)

internal sealed interface CrystalTypeResolution {
    data class Known(val types: List<CrystalResolvedType>) : CrystalTypeResolution {
        init {
            require(types.isNotEmpty())
        }
    }

    data object Unknown : CrystalTypeResolution
}

internal enum class CrystalVariableProvenance { ANNOTATION, ASSIGNMENT, MIXED, UNKNOWN }

internal data class CrystalVariableResolution(
    val resolution: CrystalTypeResolution,
    val provenance: CrystalVariableProvenance
)

internal fun knownType(
    name: String,
    isUnsuffixedNumericLiteral: Boolean = false
): CrystalTypeResolution = name.trim().takeIf(String::isNotEmpty)?.let {
    CrystalTypeResolution.Known(listOf(CrystalResolvedType(it, isUnsuffixedNumericLiteral)))
} ?: CrystalTypeResolution.Unknown

internal fun mergeKnown(results: List<CrystalTypeResolution>): CrystalTypeResolution {
    if (results.any { it is CrystalTypeResolution.Unknown }) return CrystalTypeResolution.Unknown
    val merged = linkedMapOf<String, CrystalResolvedType>()
    for (type in results.filterIsInstance<CrystalTypeResolution.Known>().flatMap { it.types }) {
        val previous = merged[type.name]
        merged[type.name] = if (previous == null) {
            type
        } else {
            previous.copy(
                isUnsuffixedNumericLiteral =
                    previous.isUnsuffixedNumericLiteral && type.isUnsuffixedNumericLiteral
            )
        }
    }
    return if (merged.isEmpty()) CrystalTypeResolution.Unknown else CrystalTypeResolution.Known(merged.values.toList())
}

internal fun parseTypeSet(typeText: String): CrystalTypeResolution {
    val text = unwrapTypeGrouping(typeText.trim())
    if (text.isEmpty()) return CrystalTypeResolution.Unknown
    val parts = mutableListOf<String>()
    var depth = 0
    var start = 0
    text.forEachIndexed { index, character ->
        when (character) {
            '(' -> depth++
            ')' -> if (depth > 0) depth--
            '|' -> if (depth == 0) {
                parts.add(text.substring(start, index))
                start = index + 1
            }
        }
    }
    parts.add(text.substring(start))
    val results = parts.map { part ->
        val unwrapped = normalizeNilable(unwrapTypeGrouping(part.trim()))
        if (unwrapped.containsTopLevelUnion()) parseTypeSet(unwrapped) else knownType(unwrapped)
    }
    return mergeKnown(results)
}

/**
 * Crystal's nilable shorthand `T?` is sugar for `T | Nil`. The resolver models
 * unions explicitly (and documents that condition-based narrowing is not
 * modeled), so a nilable annotation must be expanded here: otherwise
 * `Array(String)?` stays a single pseudo-type name that neither element
 * extraction nor compatibility can decompose. A proc type (`Int32 -> String?`)
 * keeps the `?` on its return type and is left untouched.
 */
private fun normalizeNilable(typeText: String): String {
    val trimmed = typeText.trim()
    if (!trimmed.endsWith("?")) return trimmed
    if (trimmed.containsTopLevelArrow()) return trimmed
    val base = trimmed.dropLast(1).trim()
    return if (base.isEmpty()) trimmed else "$base | Nil"
}

private fun String.containsTopLevelArrow(): Boolean {
    var depth = 0
    for (index in indices) {
        when (this[index]) {
            '(', '[', '{' -> depth++
            ')', ']', '}' -> if (depth > 0) depth--
            '-' -> if (depth == 0 && getOrNull(index + 1) == '>') return true
        }
    }
    return false
}

internal fun CrystalTypeResolution.render(): String? =
    (this as? CrystalTypeResolution.Known)?.types?.joinToString(" | ") { it.name }

private fun String.containsTopLevelUnion(): Boolean {
    var depth = 0
    for (character in this) {
        when (character) {
            '(' -> depth++
            ')' -> if (depth > 0) depth--
            '|' -> if (depth == 0) return true
        }
    }
    return false
}

private fun unwrapTypeGrouping(typeText: String): String {
    var current = typeText
    while (current.startsWith('(') && current.endsWith(')')) {
        var depth = 0
        var complete = true
        for (index in current.indices) {
            when (current[index]) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth == 0 && index != current.lastIndex) {
                complete = false
                break
            }
        }
        if (!complete || depth != 0) break
        current = current.substring(1, current.lastIndex).trim()
    }
    return current
}
