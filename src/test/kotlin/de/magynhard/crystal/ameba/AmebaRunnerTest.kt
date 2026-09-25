package de.magynhard.crystal.ameba

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import junit.framework.TestCase
import java.io.File

class AmebaRunnerTest : TestCase() {

    private lateinit var tempDir: File

    override fun setUp() {
        super.setUp()
        tempDir = FileUtil.createTempDirectory("ameba-runner", null)
    }

    override fun tearDown() {
        try {
            FileUtil.delete(tempDir)
        } finally {
            super.tearDown()
        }
    }

    fun testRunsJsonAndParsesIssues() {
        if (SystemInfo.isWindows) return
        val capture = File(tempDir, "stdin.txt")
        val binary = writeFake(mode = "json", capture = capture)
        val request = baseRequest(binary).copy(documentText = "a.try { |i| i.odd? }\n")

        val result = AmebaRunner.run(request)

        assertNull(result.error)
        assertEquals(1, result.issues.size)
        assertEquals("Style/VerboseBlock", result.issues.single().ruleName)
        // The document text must arrive on STDIN (unsaved buffers lint fresh).
        assertEquals("a.try { |i| i.odd? }\n", capture.readText())
    }

    fun testIssuesFoundIsNotAnError() {
        if (SystemInfo.isWindows) return
        val binary = writeFake(mode = "json", capture = File(tempDir, "stdin.txt"))
        // Fake exits 1 with issues, like real Ameba on findings.
        val result = AmebaRunner.run(baseRequest(binary))
        assertNull("Findings must not surface as errors: ${result.error}", result.error)
        assertEquals(1, result.issues.size)
    }

    fun testFallsBackToFlycheckForOldBinaries() {
        if (SystemInfo.isWindows) return
        val binary = writeFake(mode = "flycheck-fallback", capture = File(tempDir, "stdin.txt"))
        val result = AmebaRunner.run(baseRequest(binary))

        assertNull(result.error)
        assertEquals(1, result.issues.size)
        assertEquals("Style/VerboseBlock", result.issues.single().ruleName)
        assertEquals(AmebaSeverity.WARNING, result.issues.single().severity)
    }

    fun testReportsRealErrors() {
        if (SystemInfo.isWindows) return
        val binary = writeFake(mode = "error", capture = File(tempDir, "stdin.txt"))
        val result = AmebaRunner.run(baseRequest(binary))

        assertTrue(result.issues.isEmpty())
        assertNotNull(result.error)
        assertTrue(result.error!!.contains("cannot read config"))
    }

    fun testReportsMissingBinary() {
        val request = baseRequest(File(tempDir, "no-such-ameba").absolutePath)
        val result = AmebaRunner.run(request)
        assertTrue(result.issues.isEmpty())
        assertNotNull(result.error)
    }

    private fun baseRequest(binary: File): AmebaRunner.Request {
        return baseRequest(binary.absolutePath)
    }

    private fun baseRequest(binaryPath: String): AmebaRunner.Request {
        return AmebaRunner.Request(
            binaryPath = binaryPath,
            configPath = null,
            workDirectory = tempDir.absolutePath,
            stdinFileName = "main.cr",
            targetFilePath = File(tempDir, "main.cr").absolutePath,
            documentText = "puts 1\n",
            timeoutMs = 15_000L
        )
    }

    /**
     * Fake `ameba`: captures STDIN, then behaves per `mode`.
     * - json: prints canned JSON findings for `main.cr`, exits 1.
     * - flycheck-fallback: rejects `--format json` on stderr, answers
     *   flycheck lines when asked again.
     * - error: real failure on stderr, exit 2.
     */
    private fun writeFake(mode: String, capture: File): File {
        val json = File(tempDir, "response.json")
        json.writeText(
            """{"sources": [{"path": "main.cr", "issues": [{"rule_name": "Style/VerboseBlock", """ +
                """"severity": "Convention", "message": "Use short block notation.", """ +
                """"location": {"line": 1, "column": 3}, "end_location": {"line": 1, "column": 20}}]}]}"""
        )
        val file = File(tempDir, "fake-ameba")
        file.writeText(
            """
            #!/bin/sh
            cat > "${capture.absolutePath}"
            if [ "$mode" = "error" ]; then
              echo "cannot read config" >&2
              exit 2
            fi
            if [ "$mode" = "flycheck-fallback" ]; then
              if [ "$2" = "json" ]; then
                echo "Unknown formatter \`json\`. Use one of progress|todo|flycheck." >&2
                exit 1
              fi
              echo "main.cr:2:5: W: [Style/VerboseBlock] Use short block notation"
              exit 1
            fi
            cat "${json.absolutePath}"
            exit 1
            """.trimIndent() + "\n"
        )
        assertTrue("Cannot make fake executable: $file", file.setExecutable(true))
        return file
    }
}
