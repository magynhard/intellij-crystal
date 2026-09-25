package de.magynhard.crystal.ameba

import com.google.gson.JsonParser

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
            val root = JsonParser.parseString(output).asJsonObject
            val sources = root.getAsJsonArray("sources") ?: return emptyList()
            val issues = mutableListOf<AmebaIssue>()
            for (source in sources) {
                val obj = source.asJsonObject
                val path = obj.get("path")?.asString ?: continue
                if (!samePath(path, expectedPath)) continue
                val entries = obj.getAsJsonArray("issues") ?: continue
                for (entry in entries) {
                    parseJsonIssue(entry.asJsonObject)?.let(issues::add)
                }
            }
            issues
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseJsonIssue(obj: com.google.gson.JsonObject): AmebaIssue? {
        return try {
            val location = obj.getAsJsonObject("location") ?: return null
            val line = location.get("line")?.asInt ?: return null
            val column = location.get("column")?.asInt ?: return null
            if (line < 1 || column < 1) return null
            val end = obj.getAsJsonObject("end_location")
            val message = obj.get("message")?.asString?.trim().orEmpty()
            if (message.isEmpty()) return null
            AmebaIssue(
                ruleName = obj.get("rule_name")?.asString?.trim().takeIf { !it.isNullOrEmpty() } ?: "Unknown",
                severity = AmebaSeverity.parse(obj.get("severity")?.asString),
                message = message,
                line = line,
                column = column,
                endLine = end?.get("line")?.asInt?.takeIf { it >= line },
                endColumn = end?.get("column")?.asInt
            )
        } catch (_: Exception) {
            null
        }
    }

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
