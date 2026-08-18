package de.magynhard.crystal.analysis

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalRequireCollectorTest : BasePlatformTestCase() {

    fun testCollectsTrailingRequireAfterMacroControlledArguments() {
        val file = configure(
            "module Indexable(T)\n" +
                "  protected def self.each_cartesian_impl(*indexables : *U, &block) forall U\n" +
                "    yield Tuple.new(\n" +
                "      {% for i in 0...U.size %}\n" +
                "        indexables[{{ i }}].unsafe_fetch(indices[{{ i }}]),\n" +
                "      {% end %}\n" +
                "    )\n" +
                "  end\n" +
                "end\n" +
                "require \"./indexable/*\"",
        )

        assertEquals(listOf("./indexable/*"), CrystalRequireCollector.collect(file).paths)
    }

    fun testCollectsDirectAndMacroControlledTopLevelRequires() {
        val file = configure(
            "require \"json\"\n" +
                "{% if flag?(:win32) %}\nrequire \"win32\"\n{% end %}\n" +
                "require \"./models/**\"",
        )

        val result = CrystalRequireCollector.collect(file)

        assertEquals(listOf("json", "win32", "./models/**"), result.paths)
        assertEquals("4:json|5:win32|11:./models/**", result.fingerprint)
        assertEquals(result.fingerprint, CrystalRequireCollector.collect(file).fingerprint)
    }

    fun testExcludesRequiresOutsideStaticTopLevelContexts() {
        val file = configure(
            "def load\nrequire \"inside_def\"\nend\n" +
                "if true\nrequire \"dynamic\"\nend\n" +
                "class Loader\nrequire \"inside_type\"\nend\n" +
                "{{ require \"inside_macro_interpolation\" }}",
        )

        assertTrue(CrystalRequireCollector.collect(file).paths.isEmpty())
    }

    fun testExcludesInterpolatedAndIncompleteStringRequires() {
        val file = configure(
            "require \"json/#{format}\"\n" +
                "require \"unterminated",
        )

        assertTrue(CrystalRequireCollector.collect(file).paths.isEmpty())
    }

    fun testExcludesCompoundDoubleQuotedRequireExpressions() {
        val file = configure(
            "require \"foo\" \"bar\"\n" +
                "require \"baz\"\"qux\"",
        )

        assertTrue(CrystalRequireCollector.collect(file).paths.isEmpty())
    }

    fun testCollectsDecodedDoubleQuotedLiteralContent() {
        val file = configure(
            "require \"\\x2e\\057feature\\u002ecr\"\n" +
                "require \"unicode_\\u{41 1F600}\"\n" +
                "require \"octal_\\101\\\\\\\"\\#\"\n" +
                "require \"continued_\\\nname\"",
        )

        assertEquals(
            listOf("./feature.cr", "unicode_A😀", "octal_A\\\"#", "continued_name"),
            CrystalRequireCollector.collect(file).paths,
        )
    }

    fun testCollectsContinuationWhitespaceAtClosingQuoteBoundary() {
        val file = configure(
            "require \"lf_feature\\\n  \"\n" +
                "require \"crlf_feature\\\r\n\t \"",
        )

        assertEquals(
            listOf("lf_feature", "crlf_feature"),
            CrystalRequireCollector.collect(file).paths,
        )
    }

    fun testExcludesInvalidEscapesWithoutProducingFingerprintEdges() {
        val file = configure(
            "require \"invalid_hex_\\xF\"\n" +
                "require \"invalid_octal_\\400\"\n" +
                "require \"invalid_unicode_\\u{110000}\"\n" +
                "require \"invalid_separator_\\u{41\\t42}\"",
        )

        assertTrue(CrystalRequireCollector.collect(file).paths.isEmpty())
        assertEquals("", CrystalRequireCollector.collect(file).fingerprint)
    }

    fun testFingerprintIgnoresMethodBodyEdits() {
        val original = fingerprint("require \"json\"\ndef load\n1\nend")
        val edited = fingerprint("require \"json\"\ndef load\nputs \"changed\"\nend")

        assertEquals(original, edited)
    }

    fun testFingerprintChangesWhenTopLevelRequireIsAdded() {
        val original = fingerprint("require \"json\"")
        val added = fingerprint("require \"json\"\nrequire \"yaml\"")

        assertTrue(original != added)
    }

    fun testFingerprintChangesWhenTopLevelRequireIsRemoved() {
        val original = fingerprint("require \"json\"\nrequire \"yaml\"")
        val removed = fingerprint("require \"json\"")

        assertTrue(original != removed)
    }

    fun testFingerprintChangesWhenTopLevelRequiresAreReordered() {
        val original = fingerprint("require \"json\"\nrequire \"yaml\"")
        val reordered = fingerprint("require \"yaml\"\nrequire \"json\"")

        assertTrue(original != reordered)
    }

    fun testFingerprintChangesWhenTopLevelRequireIsEdited() {
        val original = fingerprint("require \"json\"")
        val edited = fingerprint("require \"json/lexer\"")

        assertTrue(original != edited)
    }

    private fun fingerprint(code: String): String =
        CrystalRequireCollector.collect(configure(code)).fingerprint

    private fun configure(code: String): PsiFile =
        myFixture.configureByText("test.cr", code)
}
