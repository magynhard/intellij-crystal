package de.magynhard.crystal.inspections

/**
 * Determines whether an argument type is compatible with a parameter type,
 * considering Crystal's type system rules including union types, nilable types,
 * and numeric autocasting.
 */
object CrystalTypeCompatibility {

    /** All known built-in types where we can confidently detect mismatches. */
    private val KNOWN_BUILTINS = setOf(
        "Int8", "Int16", "Int32", "Int64", "Int128",
        "UInt8", "UInt16", "UInt32", "UInt64", "UInt128",
        "Float32", "Float64",
        "String", "Char", "Bool", "Nil", "Symbol", "Regex"
    )

    /** All numeric types (integers + floats). */
    private val NUMERIC_TYPES = setOf(
        "Int8", "Int16", "Int32", "Int64", "Int128",
        "UInt8", "UInt16", "UInt32", "UInt64", "UInt128",
        "Float32", "Float64"
    )

    /** All integer types. */
    private val INTEGER_TYPES = setOf(
        "Int8", "Int16", "Int32", "Int64", "Int128",
        "UInt8", "UInt16", "UInt32", "UInt64", "UInt128"
    )

    /**
     * Numeric autocast hierarchy: which suffixed types can be implicitly widened.
     * Crystal allows lossless widening (no precision loss).
     * Int → Float is NOT allowed (precision loss).
     */
    private val NUMERIC_AUTOCAST: Map<String, Set<String>> = mapOf(
        "Int8" to setOf("Int16", "Int32", "Int64", "Int128"),
        "Int16" to setOf("Int32", "Int64", "Int128"),
        "Int32" to setOf("Int64", "Int128"),
        "Int64" to setOf("Int128"),
        "UInt8" to setOf("UInt16", "UInt32", "UInt64", "UInt128", "Int16", "Int32", "Int64", "Int128"),
        "UInt16" to setOf("UInt32", "UInt64", "UInt128", "Int32", "Int64", "Int128"),
        "UInt32" to setOf("UInt64", "UInt128", "Int64", "Int128"),
        "UInt64" to setOf("UInt128", "Int128"),
        "Float32" to setOf("Float64")
    )

    /**
     * Checks if an argument type is compatible with a parameter type.
     *
     * @param argType The resolved type of the argument
     * @param paramType The declared type of the parameter (from type annotation)
     * @param isUnsuffixedNumericLiteral If true, the argument is a numeric literal without suffix
     *        (Crystal autocasts these to any numeric type that can hold the value)
     * @return true if compatible, false if definite mismatch
     */
    fun isCompatible(argType: String, paramType: String, isUnsuffixedNumericLiteral: Boolean = false): Boolean {
        val normalizedParam = normalizeType(paramType)

        // Exact match
        if (argType == normalizedParam) return true

        // Handle nilable types: "Type?" is sugar for "Type | Nil"
        if (normalizedParam.endsWith("?")) {
            val baseType = normalizedParam.dropLast(1).trim()
            if (argType == "Nil") return true
            if (argType == baseType) return true
            // Recurse for autocast check against the base type
            return isCompatible(argType, baseType, isUnsuffixedNumericLiteral)
        }

        // Handle union types: "Type1 | Type2 | ..."
        if (normalizedParam.contains("|")) {
            val members = normalizedParam.split("|").map { it.trim() }
            return members.any { isCompatible(argType, it, isUnsuffixedNumericLiteral) }
        }

        // Unsuffixed numeric literals autocast to ANY numeric type
        if (isUnsuffixedNumericLiteral && normalizedParam in NUMERIC_TYPES) {
            return true
        }

        // Suffixed numeric autocast (lossless widening)
        if (argType in NUMERIC_TYPES && normalizedParam in NUMERIC_TYPES) {
            val targets = NUMERIC_AUTOCAST[argType]
            if (targets != null && normalizedParam in targets) return true
        }

        // Handle generic types: "Array(Int32)", "Hash(String, Int32)", etc.
        if (normalizedParam.contains("(") && argType.contains("(")) {
            val paramBase = normalizedParam.substringBefore("(").trim()
            val argBase = argType.substringBefore("(").trim()
            if (paramBase != argBase) return false
            // Same base generic — compare inner types
            val paramInner = extractGenericTypeArgs(normalizedParam)
            val argInner = extractGenericTypeArgs(argType)
            if (paramInner.size != argInner.size) return false
            return paramInner.zip(argInner).all { (p, a) -> isCompatible(a, p, isUnsuffixedNumericLiteral) }
        }
        // Arg is generic but param is not (e.g. arg="Array(Int32)", param="String") → incompatible
        if (argType.contains("(") && !normalizedParam.contains("(")) {
            return false
        }
        // Param is generic but arg is not (e.g. param="Array(Int32)", arg="String") → incompatible
        if (normalizedParam.contains("(") && !argType.contains("(")) {
            return false
        }

        // If the parameter type is NOT a known builtin, we can't be sure it's incompatible
        // (it could be a superclass, an alias, a generic, etc.)
        if (normalizedParam !in KNOWN_BUILTINS) return true

        // If the argument type is NOT a known builtin, we also can't be sure
        if (argType !in KNOWN_BUILTINS) return true

        // Both are known builtins and we didn't find a match → incompatible
        return false
    }

    /**
     * Normalizes a type string by trimming whitespace and handling common patterns.
     */
    /**
     * Crystal shorthand type aliases that expand to union types.
     */
    private val TYPE_ALIASES = mapOf(
        "Int" to "Int8 | Int16 | Int32 | Int64 | Int128",
        "UInt" to "UInt8 | UInt16 | UInt32 | UInt64 | UInt128",
        "Float" to "Float32 | Float64",
        "Number" to "Int8 | Int16 | Int32 | Int64 | Int128 | UInt8 | UInt16 | UInt32 | UInt64 | UInt128 | Float32 | Float64"
    )

    private fun normalizeType(type: String): String {
        val trimmed = type.trim()
        return TYPE_ALIASES[trimmed] ?: trimmed
    }

    /**
     * Extracts generic type arguments from a type like "Array(Int32)" → ["Int32"]
     * or "Hash(String, Int32)" → ["String", "Int32"].
     * Handles nested parens by tracking depth.
     */
    private fun extractGenericTypeArgs(type: String): List<String> {
        val openIdx = type.indexOf('(')
        val closeIdx = type.lastIndexOf(')')
        if (openIdx < 0 || closeIdx < 0 || closeIdx <= openIdx) return emptyList()
        val inner = type.substring(openIdx + 1, closeIdx)
        // Split by comma, respecting nested parens
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in inner.indices) {
            when (inner[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> {
                    if (depth == 0) {
                        result.add(inner.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
        }
        result.add(inner.substring(start).trim())
        return result
    }

    /**
     * Returns the set of expected types for error messages.
     * For union types, returns all members.
     */
    fun describeExpectedType(paramType: String): String {
        val normalized = normalizeType(paramType)
        if (normalized.endsWith("?")) {
            return "${normalized.dropLast(1).trim()} | Nil"
        }
        return normalized
    }
}
