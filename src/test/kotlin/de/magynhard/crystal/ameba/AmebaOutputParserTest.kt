package de.magynhard.crystal.ameba

import junit.framework.TestCase

class AmebaOutputParserTest : TestCase() {

    private val sampleJson = """
        {
          "metadata": {"ameba_version": "1.7.0", "crystal_version": "1.17.0"},
          "sources": [
            {"path": "other.cr", "issues": [
              {"rule_name": "Lint/UselessAssign", "severity": "Warning",
               "message": "Useless assignment.",
               "location": {"line": 1, "column": 1},
               "end_location": {"line": 1, "column": 2}}
            ]},
            {"path": "main.cr", "issues": [
              {"rule_name": "Style/VerboseBlock", "severity": "Convention",
               "message": "Use short block notation instead: `try(&.odd?)`.",
               "location": {"line": 2, "column": 5},
               "end_location": {"line": 2, "column": 20}},
              {"rule_name": "Lint/UnusedArgument", "severity": "Warning",
               "message": "Unused argument `x`.\nSecond line.",
               "location": {"line": 5, "column": 10}}
            ]}
          ],
          "summary": {"issues_count": 3, "target_sources_count": 2}
        }
    """.trimIndent()

    fun testParsesJsonIssuesForExpectedPathOnly() {
        val issues = AmebaOutputParser.parseJson(sampleJson, "main.cr")
        assertEquals(2, issues.size)

        val first = issues[0]
        assertEquals("Style/VerboseBlock", first.ruleName)
        assertEquals(AmebaSeverity.CONVENTION, first.severity)
        assertEquals("Use short block notation instead: `try(&.odd?)`.", first.message)
        assertEquals(2, first.line)
        assertEquals(5, first.column)
        assertEquals(2, first.endLine)
        assertEquals(20, first.endColumn)

        val second = issues[1]
        assertEquals("Lint/UnusedArgument", second.ruleName)
        assertEquals(AmebaSeverity.WARNING, second.severity)
        assertEquals("Unused argument `x`.\nSecond line.", second.message)
        assertNull(second.endLine)
        assertNull(second.endColumn)
    }

    fun testJsonMatchesAbsoluteExpectedPathBySuffix() {
        val issues = AmebaOutputParser.parseJson(sampleJson, "/proj/src/main.cr")
        assertEquals(2, issues.size)
    }

    fun testJsonToleratesDotPrefixedPaths() {
        val json = sampleJson.replace("\"main.cr\"", "\"./main.cr\"")
        val issues = AmebaOutputParser.parseJson(json, "main.cr")
        assertEquals(2, issues.size)
    }

    fun testJsonRejectsUnrelatedSameNamedPaths() {
        val json = sampleJson.replace("\"main.cr\"", "\"src/main.cr\"")
        val issues = AmebaOutputParser.parseJson(json, "lib/main.cr")
        assertTrue(issues.isEmpty())
    }

    fun testJsonHandlesMalformedInput() {
        assertTrue(AmebaOutputParser.parseJson("", "main.cr").isEmpty())
        assertTrue(AmebaOutputParser.parseJson("not json at all", "main.cr").isEmpty())
        assertTrue(AmebaOutputParser.parseJson("{\"sources\": []}", "main.cr").isEmpty())
        assertTrue(AmebaOutputParser.parseJson("{\"sources\": [{\"path\": \"main.cr\"}]}", "main.cr").isEmpty())
    }

    fun testJsonSkipsInvalidEntries() {
        val json = """
            {"sources": [{"path": "main.cr", "issues": [
              {"rule_name": "R", "severity": "Warning", "message": "",
               "location": {"line": 1, "column": 1}},
              {"rule_name": "R", "severity": "Warning", "message": "m",
               "location": {"line": 0, "column": 1}},
              {"rule_name": "R", "severity": "Warning", "message": "kept",
               "location": {"line": 3, "column": 2}}
            ]}]}
        """.trimIndent()
        val issues = AmebaOutputParser.parseJson(json, "main.cr")
        assertEquals(1, issues.size)
        assertEquals("kept", issues.single().message)
    }

    fun testParsesFlycheckLines() {
        val output = """
            main.cr:2:5: W: [Style/VerboseBlock] Use short block notation
            main.cr:5:10: C: [Lint/UselessAssign] Useless assignment to variable `size`
            other.cr:1:1: E: [Lint/Syntax] Unexpected token
            nonsense line without structure
        """.trimIndent()
        val issues = AmebaOutputParser.parseFlycheck(output, "main.cr")
        assertEquals(2, issues.size)
        assertEquals("Style/VerboseBlock", issues[0].ruleName)
        assertEquals(AmebaSeverity.WARNING, issues[0].severity)
        assertEquals(2, issues[0].line)
        assertEquals(5, issues[0].column)
        assertNull(issues[0].endLine)
        assertEquals(AmebaSeverity.CONVENTION, issues[1].severity)
    }

    fun testFlycheckHandlesMalformedInput() {
        assertTrue(AmebaOutputParser.parseFlycheck("", "main.cr").isEmpty())
        assertTrue(AmebaOutputParser.parseFlycheck("garbage\nmore garbage", "main.cr").isEmpty())
    }

    fun testSeverityParsing() {
        assertEquals(AmebaSeverity.ERROR, AmebaSeverity.parse("Error"))
        assertEquals(AmebaSeverity.ERROR, AmebaSeverity.parse("FATAL"))
        assertEquals(AmebaSeverity.WARNING, AmebaSeverity.parse("warning"))
        assertEquals(AmebaSeverity.CONVENTION, AmebaSeverity.parse("Convention"))
        assertEquals(AmebaSeverity.CONVENTION, AmebaSeverity.parse(null))
        assertEquals(AmebaSeverity.CONVENTION, AmebaSeverity.parse("mystery"))
    }

    fun testNullFieldsNeverAbortParsing() {
        // Gson throws on asString/asInt of JsonNull: every nullable member
        // must degrade gracefully instead of dropping valid siblings.
        val json = """
            {"sources": [
              {"path": null, "issues": [
                {"rule_name": "R", "severity": "Warning", "message": "lost with path",
                 "location": {"line": 1, "column": 1}}
              ]},
              "not-an-object",
              {"path": "main.cr", "issues": null},
              {"path": "main.cr", "issues": [
                {"rule_name": null, "severity": null, "message": "kept as Unknown",
                 "location": {"line": 2, "column": 3}},
                {"rule_name": "R", "severity": "Warning", "message": null,
                 "location": {"line": 3, "column": 1}},
                {"rule_name": "R", "severity": "Warning", "message": "no location"},
                {"rule_name": "R", "severity": "Warning", "message": "null line",
                 "location": {"line": null, "column": 1}},
                {"rule_name": "R", "severity": "Warning", "message": "string line",
                 "location": {"line": "2", "column": 1}}
              ]}
            ]}
        """.trimIndent()
        val issues = AmebaOutputParser.parseJson(json, "main.cr")
        assertEquals(1, issues.size)
        assertEquals("Unknown", issues.single().ruleName)
        assertEquals(AmebaSeverity.CONVENTION, issues.single().severity)
        assertEquals("kept as Unknown", issues.single().message)
        assertEquals(2, issues.single().line)
        assertEquals(3, issues.single().column)
    }
}
