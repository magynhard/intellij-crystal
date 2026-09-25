package de.magynhard.crystal.ameba

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * Parses Ameba machine-readable output into [AmebaIssue]s.
 *
 * Primary format is `ameba --format json`:
 * ```json
 * {"sources": [{"path": "a.cr", "issues": [{"rule_name": "Style/VerboseBlock",
 *   "severity": "Convention", "message": "...",
 *   "location": {"line": 1, "column": 3},
 *   "end_location": {"line": 1, "column": 20}}]}]}
 * ```
 * Issues are kept only for [expectedPath] (the `--stdin-filename` value),
 * so stale or duplicate entries for other paths never leak into the editor.
 * Malformed input yields an empty list, never an exception.
 *
 * Fallback format is `ameba --format flycheck`
 * (`path:line:col: SEVERITY: [Rule] message`), used only when the binary
 * predates JSON support.
 */
object AmebaOutputParser {

    fun parseJson(output: String, expectedPath: String): List<AmebaIssue> {
        if (output.isBlank()) return emptyList()
        return try {
            parseJsonInner(output, expectedPath)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseJsonInner(output: String, expectedPath: String): List<AmebaIssue> {
        val root = JsonParser.parseString(output) as? JsonObject ?: return emptyList()
        val sources = root.get("sources") as? JsonArray ?: return emptyList()
        val issues = mutableListOf<AmebaIssue>()
        for (source in sources) {
            val obj = source as? JsonObject ?: continue
            val path = obj.str("path")
            if (path == null || !samePath(path, expectedPath)) continue
            val entries = obj.get("issues") as? JsonArray ?: continue
            for (entry in entries) {
                parseJsonIssue(entry as? JsonObject ?: continue)?.let(issues::add)
            }
        }
        return issues
    }

    private fun parseJsonIssue(obj: JsonObject): AmebaIssue? {
        val location = obj.get("location") as? JsonObject ?: return null
        val line = location.int("line") ?: return null
        val column = location.int("column") ?: return null
        if (line < 1 || column < 1) return null
        val end = obj.get("end_location") as? JsonObject
        val message = obj.str("message")?.trim().orEmpty()
        if (message.isEmpty()) return null
        return AmebaIssue(
            ruleName = obj.str("rule_name")?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown",
            severity = AmebaSeverity.parse(obj.str("severity")),
            message = message,
            line = line,
            column = column,
            endLine = end?.int("line")?.takeIf { it >= line },
            endColumn = end?.int("column")
        )
    }

    /** Null- and type-safe string member: Gson's `asString` throws on nulls. */
    private fun JsonObject.str(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.asString

    /** Null- and type-safe int member. */
    private fun JsonObject.int(name: String): Int? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isNumber }?.asInt

    private val flycheckPattern =
        Regex("""^(.+?):(\d+):(\d+):\s*([A-Za-z]+):\s*(?:\[(.+?)]\s*)?(.*)$""")

    fun parseFlycheck(output: String, expectedPath: String): List<AmebaIssue> {
        if (output.isBlank()) return emptyList()
        val issues = mutableListOf<AmebaIssue>()
        for (rawLine in output.lines()) {
            val line = rawLine.trimEnd()
            if (line.isBlank()) continue
            val match = flycheckPattern.find(line) ?: continue
            val (path, lineNum, colNum, severity, rule, message) = match.destructured
            if (!samePath(path.trim(), expectedPath)) continue
            val lineInt = lineNum.toIntOrNull() ?: continue
            val colInt = colNum.toIntOrNull() ?: continue
            if (lineInt < 1 || colInt < 1 || message.isBlank()) continue
            issues.add(
                AmebaIssue(
                    ruleName = rule.trim().takeIf { it.isNotEmpty() } ?: "Unknown",
                    severity = AmebaSeverity.parse(severity),
                    message = message.trim().replace(Regex("\\s+"), " "),
                    line = lineInt,
                    column = colInt,
                    endLine = null,
                    endColumn = null
                )
            )
        }
        return issues
    }

    /**
     * Ameba reports the `--stdin-filename` value verbatim (usually project-
     * relative) while callers may hold absolute paths. Match on file-name
     * equality first, then suffix equality, so neither form is missed and
     * unrelated same-named files in other directories still collide only
     * when nothing better matches.
     */
    internal fun samePath(reported: String, expected: String): Boolean {
        if (reported == expected) return true
        val reportedName = reported.substringAfterLast('/').substringAfterLast('\\')
        val expectedName = expected.substringAfterLast('/').substringAfterLast('\\')
        if (reportedName != expectedName || reportedName.isEmpty()) return false
        val normalizedReported = reported.replace('\\', '/')
        val normalizedExpected = expected.replace('\\', '/')
        return normalizedReported == normalizedExpected ||
            normalizedReported.endsWith("/$normalizedExpected") ||
            normalizedExpected.endsWith("/$normalizedReported")
    }
}
