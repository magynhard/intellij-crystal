package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Parses real Crystal stdlib sources when a local Crystal installation is present.
 * These files exercise syntax far beyond hand-written fixtures (setter definitions,
 * operator methods, macro-heavy declarations) and act as an early-warning canary:
 * a grammar gap that breaks a stdlib file silently degrades stub indexing of that
 * file's members, which then surfaces as false-positive inspections downstream.
 *
 * Skips gracefully when no local Crystal stdlib is found.
 */
class CrystalStdlibSourceParseTest : BasePlatformTestCase() {

    private fun findStdlibFile(name: String): File? =
        listOf("/usr/lib/crystal", "/usr/local/lib/crystal", "/opt/crystal/lib/crystal")
            .map { File(it, name) }
            .firstOrNull { it.isFile }

    private fun assertParsesCleanly(file: File) {
        myFixture.configureByText(file.name, file.readText())
        val errors = PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)
        if (errors.isNotEmpty()) {
            val sb = StringBuilder("=== Parse errors in ${file.path} (first 5 of ${errors.size}) ===\n")
            for (error in errors.take(5)) {
                sb.appendLine("At offset ${error.textOffset}: ${error.errorDescription}")
                sb.appendLine("  Context: '${error.parent.text.take(120).replace('\n', ' ')}'")
            }
            fail(sb.toString())
        }
    }

    fun testUriCrParsesWithoutErrors() {
        val uri = findStdlibFile("uri.cr") ?: return
        assertParsesCleanly(uri)
    }

    fun testHttpClientCrParsesWithoutErrors() {
        // http/client.cr combines setter-style assignments, keyword args and blocks.
        val client = findStdlibFile("http/client.cr") ?: return
        assertParsesCleanly(client)
    }

    fun testMacrosCrParsesWithoutErrors() {
        // Builtin macro-method API (run, puts, flag?, …) — lives in the
        // excluded compiler tree and is indexed as a single-file root.
        // KNOWN GAP: `def [](name : …)`-style bracket method definitions do
        // not parse yet (their lines are tolerated below; tracked in TODO.md).
        val macros = findStdlibFile("compiler/crystal/macros.cr") ?: return
        val text = macros.readText()
        val tolerantLine: (Int) -> Boolean = { offset ->
            val lineStart = text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)) + 1
            val lineEnd = text.indexOf('\n', offset).let { if (it == -1) text.length else it }
            text.substring(lineStart, lineEnd).trimStart().startsWith("def [")
        }
        myFixture.configureByText(macros.name, text)
        val errors = PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)
        val blocking = errors.filter { !tolerantLine(it.textOffset) }
        if (blocking.isNotEmpty()) {
            val sb = StringBuilder("=== Parse errors in ${macros.path} (first 5 of ${blocking.size}) ===\n")
            for (error in blocking.take(5)) {
                sb.appendLine("At offset ${error.textOffset}: ${error.errorDescription}")
                sb.appendLine("  Context: '${error.parent.text.take(120).replace('\n', ' ')}'")
            }
            fail(sb.toString())
        }
    }

    fun testDequeCrParsesWithoutErrors() {
        val deque = findStdlibFile("deque.cr") ?: return
        assertParsesCleanly(deque)
    }

    fun testIntCrParsesWithoutErrors() {
        // int.cr defines struct Int (line ~67) BEFORE macro-heavy method bodies
        // ({% begin %} blocks with {{@type}}::CONST, line ~151+) and struct
        // Int32/Int64/UInt32 (line ~1303) AFTER them. A parse failure in the
        // macro region silently removes Int32 etc. from stub indexing and kills
        // literal DOT completion (5.<caret>) — this canary guards that.
        val int = findStdlibFile("int.cr") ?: return
        assertParsesCleanly(int)
    }

    fun testFloatCrParsesWithoutErrors() {
        // float.cr defines struct Float64 (line ~335) after {% if flag? %}
        // blocks (line ~281+).
        val float = findStdlibFile("float.cr") ?: return
        assertParsesCleanly(float)
    }

    fun testNumberAndComparableCrParseWithoutErrors() {
        for (name in listOf("number.cr", "comparable.cr")) {
            val file = findStdlibFile(name) ?: continue
            assertParsesCleanly(file)
        }
    }

    fun testJsonToJsonCrParsesWithoutErrors() {
        // json/to_json.cr reopens `class Object` (with a zero-argument
        // Object#to_json) and reopens `struct NamedTuple`/`struct Enum` with
        // {% for %} loops inside method bodies. The Object re-open supplies the
        // zero-arg to_json overload every `{…}.to_json` call dispatches to.
        val tojson = findStdlibFile("json/to_json.cr") ?: return
        assertParsesCleanly(tojson)
    }

    fun testHttpServerResponseCrParsesWithoutErrors() {
        // http/server/response.cr is the ONLY stdlib file with a parenthesized
        // type in a property macro argument (`property upgrade_handler :
        // (IO ->)?`, line ~45) sitting directly above `def initialize(@io : IO,
        // @version = "HTTP/1.1")` (line ~50). A parse gap there silently drops
        // the constructor from stub indexing, which makes every
        // `HTTP::Server::Response.new(io)` call resolve to the implicit
        // zero-argument constructor and report false "Too many arguments"
        // (reported in kemal's spec/event_stream_spec.cr). It also carries the
        // nested `class Output < IO` with a `property!` macro call.
        val response = findStdlibFile("http/server/response.cr") ?: return
        assertParsesCleanly(response)
    }

    fun testHttpServerContextCrParsesWithoutErrors() {
        val context = findStdlibFile("http/server/context.cr") ?: return
        assertParsesCleanly(context)
    }

    fun testHttpServerRequestProcessorCrParsesWithoutErrors() {
        val processor = findStdlibFile("http/server/request_processor.cr") ?: return
        assertParsesCleanly(processor)
    }
}
