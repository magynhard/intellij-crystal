package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.inspections.CrystalParseErrorInspection

/**
 * The parse-error inspection turns `PsiErrorElement` diagnostics into discrete
 * inspection problems so offline Inspect Code runs can report parser failures
 * (`enabledByDefault=false` because the editor error highlighter already
 * renders them).
 */
class CrystalParseErrorInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun parseProblems() =
        myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("Crystal parse error: ") }

    fun testReportsParseErrorAsInspectionProblem() {
        myFixture.enableInspections(CrystalParseErrorInspection())
        myFixture.configureByText("test.cr", "def foo(a :\n")
        assertTrue(
            "Expected a Crystal parse error problem",
            parseProblems().isNotEmpty()
        )
    }

    fun testNoSyntheticMessageOnValidFile() {
        myFixture.enableInspections(CrystalParseErrorInspection())
        myFixture.configureByText("test.cr", "def foo(a : Int32)\n  a\nend\n")
        assertTrue(
            "Valid file must not produce parse-error problems: ${parseProblems()}",
            parseProblems().isEmpty()
        )
    }
}
