package de.magynhard.crystal.ameba

/**
 * A single Ameba diagnostic, as reported by `ameba --format json`
 * (or the flycheck fallback). Line/column numbers are 1-based, exactly as
 * Ameba reports them; end positions may be absent in older output.
 */
data class AmebaIssue(
    val ruleName: String,
    val severity: AmebaSeverity,
    val message: String,
    val line: Int,
    val column: Int,
    val endLine: Int?,
    val endColumn: Int?
)

enum class AmebaSeverity {
    CONVENTION,
    WARNING,
    ERROR;

    companion object {
        fun parse(raw: String?): AmebaSeverity {
            return when (raw?.trim()?.lowercase()) {
                "error", "fatal", "e" -> ERROR
                "warning", "w" -> WARNING
                else -> CONVENTION
            }
        }
    }
}
