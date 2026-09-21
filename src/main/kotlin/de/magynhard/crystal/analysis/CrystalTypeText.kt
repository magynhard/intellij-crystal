package de.magynhard.crystal.analysis

/**
 * Parsing helpers for the deterministic type names our own inference produces
 * ("Tuple(a, b)", "NamedTuple(k: v, ...)"). Used for splat expansion and
 * structural comparisons.
 */
internal object CrystalTypeText {

    /** Depth-aware split on top-level commas, respecting (), [], {} nesting. */
    fun splitTopLevelCommas(text: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (ch in text) {
            when (ch) {
                '(', '[', '{' -> { depth++; current.append(ch) }
                ')', ']', '}' -> { depth--; current.append(ch) }
                ',' -> if (depth == 0) {
                    parts.add(current.toString())
                    current.setLength(0)
                } else current.append(ch)
                else -> current.append(ch)
            }
        }
        parts.add(current.toString())
        return parts.map(String::trim).filter(String::isNotEmpty)
    }

    /**
     * Splits a rendered type name into its base and generic arguments:
     * "Array(Int32)" -> "Array" to ["Int32"], "Hash(String, Array(Int32))" ->
     * "Hash" to ["String", "Array(Int32)"], and a bare "String" -> "String" to [].
     * The leading `::` of an absolute path is dropped so base comparisons match.
     * Null when the parentheses are unbalanced or the base is empty.
     */
    fun genericBaseAndArguments(typeName: String): Pair<String, List<String>>? {
        val compact = typeName.trim().removePrefix("::").trim()
        if (compact.isEmpty()) return null
        val open = compact.indexOf('(')
        if (open < 0) return compact to emptyList()
        if (!compact.endsWith(")")) return null
        val base = compact.substring(0, open).trim()
        if (base.isEmpty()) return null
        return base to splitTopLevelCommas(compact.substring(open + 1, compact.length - 1))
    }

    /**
     * "Tuple(Int32, String)" -> ["Int32", "String"]; null when [typeName] is not
     * a tuple type name produced by this inference.
     */
    fun tupleElements(typeName: String): List<String>? {
        val compact = typeName.trim()
        if (!compact.startsWith("Tuple(") || !compact.endsWith(")")) return null
        return splitTopLevelCommas(compact.removePrefix("Tuple(").removeSuffix(")"))
    }

    /**
     * "NamedTuple(a: Int32, b: String)" -> {a: Int32, b: String} (first top-level
     * ':' splits key and value). Null when not a named tuple type name.
     */
    fun namedTupleEntries(typeName: String): Map<String, String>? {
        val compact = typeName.trim()
        if (!compact.startsWith("NamedTuple(") || !compact.endsWith(")")) return null
        val inner = compact.removePrefix("NamedTuple(").removeSuffix(")")
        if (inner.isBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (part in splitTopLevelCommas(inner)) {
            var depth = 0
            var colonIdx = -1
            for ((index, ch) in part.withIndex()) {
                when (ch) {
                    '(', '[', '{' -> depth++
                    ')', ']', '}' -> depth--
                    ':' -> if (depth == 0 && colonIdx < 0) { colonIdx = index; break }
                }
            }
            if (colonIdx <= 0) return null
            val key = part.substring(0, colonIdx).trim()
            val value = part.substring(colonIdx + 1).trim()
            if (key.isEmpty() || value.isEmpty()) return null
            result[key] = value
        }
        return result
    }
}
