package de.magynhard.crystal.sdk

/**
 * Requirement operators for shard dependency versions, exactly the set the
 * shards documentation defines (`<`, `<=`, `>`, `>=`, `!=`, `~>`, `=`,
 * bare versions, `*`, comma-separated combinations). Anything else is not
 * evaluated — unknown shapes must never produce a verdict.
 */
internal object CrystalVersionRequirement {

    /**
     * True when [version] satisfies [requirement], false when it violates it,
     * null when the requirement cannot be evaluated (unknown operator,
     * unparseable shape, blank).
     */
    fun satisfies(requirement: String?, version: String?): Boolean? {
        if (requirement == null) return true
        val trimmed = requirement.trim()
        if (trimmed.isEmpty() || trimmed == "*") return true
        if (version == null) return null
        val parts = trimmed.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return true
        for (part in parts) {
            when (evaluatePart(part, version)) {
                true -> continue
                false -> return false
                null -> return null
            }
        }
        return true
    }

    private fun evaluatePart(part: String, version: String): Boolean? {
        val operators = listOf("<=", ">=", "!=", "~>", "<", ">", "=")
        for (operator in operators) {
            if (part.startsWith(operator)) {
                val bound = part.removePrefix(operator).trim()
                // Shard versions must contain digits; anything else is not
                // an evaluable requirement (never guess on garbage input).
                if (!startsWithDigit(bound)) return null
                return compareWithOperator(operator, version, bound)
            }
        }
        // Bare version means an exact match.
        if (!startsWithDigit(part)) return null
        val bound = parseVersion(part) ?: return null
        return compareVersions(parseVersion(version) ?: return null, bound) == 0
    }

    private fun startsWithDigit(text: String): Boolean =
        text.firstOrNull()?.isDigit() == true

    private fun compareWithOperator(operator: String, version: String, bound: String): Boolean? {
        val actual = parseVersion(version) ?: return null
        return when (operator) {
            "<" -> compareVersions(actual, parseVersion(bound) ?: return null) < 0
            "<=" -> compareVersions(actual, parseVersion(bound) ?: return null) <= 0
            ">" -> compareVersions(actual, parseVersion(bound) ?: return null) > 0
            ">=" -> compareVersions(actual, parseVersion(bound) ?: return null) >= 0
            "!=" -> compareVersions(actual, parseVersion(bound) ?: return null) != 0
            "=" -> compareVersions(actual, parseVersion(bound) ?: return null) == 0
            "~>" -> {
                val lower = parseVersion(bound) ?: return null
                val upper = pessimisticUpperBound(bound) ?: return null
                compareVersions(actual, lower) >= 0 && compareVersions(actual, upper) < 0
            }
            else -> null
        }
    }

    /**
     * Pessimistic upper bound exactly as shards documents it: the last
     * specified component may rise, earlier ones are fixed
     * (`~> 0.3.5` is `>= 0.3.5, < 0.4.0`; `~> 2.1` is `>= 2.1, < 3.0`;
     * `~> 1` is `>= 1, < 2`).
     */
    private fun pessimisticUpperBound(bound: String): List<VersionPart>? {
        val parts = splitVersion(bound)
        if (parts.isEmpty()) return null
        if (parts.size == 1) {
            val major = parts.single().number ?: return null
            return listOf(VersionPart(major + 1, ""))
        }
        val kept = parts.dropLast(1).toMutableList()
        val last = kept.removeLast()
        val lastNumber = last.number ?: return null
        kept.add(VersionPart(lastNumber + 1, ""))
        return kept
    }

    private data class VersionPart(val number: Int?, val rest: String)

    private fun parseVersion(text: String): List<VersionPart>? {
        val parts = splitVersion(text.trim())
        if (parts.isEmpty()) return null
        return parts
    }

    private fun splitVersion(text: String): List<VersionPart> {
        if (text.isEmpty()) return emptyList()
        return text.split('.').map { segment ->
            val digits = segment.takeWhile { it.isDigit() }
            VersionPart(
                number = digits.takeIf { it.isNotEmpty() }?.toIntOrNull(),
                rest = segment.drop(digits.length)
            )
        }
    }

    /**
     * Numeric segments compare numerically, missing segments count as zero,
     * and a non-empty remainder sorts below an empty one (`2.1.0-dev` is
     * strictly before `2.1.0`); remaining remainders compare lexically.
     */
    private fun compareVersions(left: List<VersionPart>, right: List<VersionPart>): Int {
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val leftPart = left.getOrNull(index) ?: VersionPart(0, "")
            val rightPart = right.getOrNull(index) ?: VersionPart(0, "")
            val leftNumber = leftPart.number ?: 0
            val rightNumber = rightPart.number ?: 0
            if (leftNumber != rightNumber) return leftNumber.compareTo(rightNumber)
            if (leftPart.rest != rightPart.rest) {
                if (leftPart.rest.isEmpty()) return 1
                if (rightPart.rest.isEmpty()) return -1
                val order = leftPart.rest.compareTo(rightPart.rest)
                if (order != 0) return order
            }
        }
        return 0
    }
}
