package de.magynhard.crystal.analysis

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalRequireCollectorTest : BasePlatformTestCase() {

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

    fun testCollectsSingleDoubleQuotedLiteralWithEscapedContent() {
        val file = configure("require \"foo\\\"bar\\\\baz\"")

        assertEquals(
            listOf("foo\\\"bar\\\\baz"),
            CrystalRequireCollector.collect(file).paths,
        )
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
