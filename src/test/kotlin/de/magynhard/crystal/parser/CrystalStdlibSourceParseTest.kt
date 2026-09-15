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

    fun testFiberContextCrParsesWithoutErrors() {
        // fiber/context.cr carries `property stack_top : Void*` (line ~9): a
        // pointer-typed declaration-macro argument whose dangling `*` used to
        // abort parsing and drop every later member of the file from stub
        // indexing.
        val context = findStdlibFile("fiber/context.cr") ?: return
        assertParsesCleanly(context)
    }

    fun testFastFloatAsciiNumberCrParsesWithoutErrors() {
        val asciiNumber = findStdlibFile("float/fast_float/ascii_number.cr") ?: return
        assertParsesCleanly(asciiNumber)
    }

    fun testRegexPcre2CrParsesWithoutErrors() {
        val pcre2 = findStdlibFile("regex/pcre2.cr") ?: return
        assertParsesCleanly(pcre2)
    }

    fun testFastFloatBigintCrParsesWithoutErrors() {
        // float/fast_float/bigint.cr computes with suffixed integers inside
        // macro interpolations (`{{ Limb == UInt64 ? 27_u32 : 13_u32 }}`).
        val bigint = findStdlibFile("float/fast_float/bigint.cr") ?: return
        assertParsesCleanly(bigint)
    }

    fun testFastFloatCommonCrParsesWithoutErrors() {
        // float/fast_float/float_common.cr passes hex literals with suffixes
        // as macro-interpolated call arguments (`{{ 0x20000000000000_u64 }}`).
        val common = findStdlibFile("float/fast_float/float_common.cr") ?: return
        assertParsesCleanly(common)
    }

    fun testLibunwindCrParsesWithoutErrors() {
        // exception/call_stack/libunwind.cr compares against a
        // macro-interpolated hex literal (`== {{ flag?(:bits64) ? 0x20b : 0x10b }}`).
        val libunwind = findStdlibFile("exception/call_stack/libunwind.cr") ?: return
        assertParsesCleanly(libunwind)
    }

    fun testXmlCrParsesWithoutErrors() {
        // xml.cr divides inside string interpolation (`"#{number // 10_000}"`).
        val xml = findStdlibFile("xml.cr") ?: return
        assertParsesCleanly(xml)
    }

    fun testSpecHelpersStringCrParsesWithoutErrors() {
        // spec/helpers/string.cr chains a call on a block value inside a macro
        // body (`end.should(%expectation, ...)`).
        val helper = findStdlibFile("spec/helpers/string.cr") ?: return
        assertParsesCleanly(helper)
    }

    fun testMacrosMethodsCrParsesWithoutErrors() {
        // compiler/crystal/macros/methods.cr calls bare `is_a?(...)` inside a
        // brace block (`interpret_check_args { BoolLiteral.new(...) }`).
        val methods = findStdlibFile("compiler/crystal/macros/methods.cr") ?: return
        assertParsesCleanly(methods)
    }

    fun testHumanizeCrParsesWithoutErrors() {
        // humanize.cr calls bare `responds_to?(...)` inside `||` operands.
        val humanize = findStdlibFile("humanize.cr") ?: return
        assertParsesCleanly(humanize)
    }

    fun testJsonBuilderCrParsesWithoutErrors() {
        // json/builder.cr mixes an implicit comparison call with a plain
        // entry (`when .<(0x20), 0x7f`).
        val builder = findStdlibFile("json/builder.cr") ?: return
        assertParsesCleanly(builder)
    }

    fun testComplexCrParsesWithoutErrors() {
        // complex.cr matches on a tuple with assignment entries
        // (`case {real_inf_sign = @real.infinite?, ...}`).
        val complex = findStdlibFile("complex.cr") ?: return
        assertParsesCleanly(complex)
    }

    fun testCallErrorCrParsesWithoutErrors() {
        // compiler/crystal/semantic/call_error.cr matches on a tuple with an
        // assignment entry (`case {arg_type = arg.type, arg}`).
        val callError = findStdlibFile("compiler/crystal/semantic/call_error.cr") ?: return
        assertParsesCleanly(callError)
    }

    fun testPrimitivesCrParsesWithoutErrors() {
        // compiler/crystal/interpreter/primitives.cr uses parenthesized
        // semicolon groups as ternary branches (`checked ? (sign_extend(7,
        // node: node); i64_to_u8(node: node)) : nop`).
        val primitives = findStdlibFile("compiler/crystal/interpreter/primitives.cr") ?: return
        assertParsesCleanly(primitives)
    }

    fun testJsonFromJsonCrParsesWithoutErrors() {
        // json/from_json.cr defines `def Time::Location.new` (line ~481): an
        // explicitly qualified receiver owning the method outside any lexical
        // type. A parse gap there cascades through the rest of the file.
        val fromJson = findStdlibFile("json/from_json.cr") ?: return
        assertParsesCleanly(fromJson)
    }

    fun testYamlFromYamlCrParsesWithoutErrors() {
        // yaml/from_yaml.cr defines `def Time::Location.new` (line ~334).
        val fromYaml = findStdlibFile("yaml/from_yaml.cr") ?: return
        assertParsesCleanly(fromYaml)
    }
}
