package de.magynhard.crystal.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalHighlightErrorFilterTest : BasePlatformTestCase() {

    /**
     * When an invalid single-quote string produces BAD_CHARACTER, the parser
     * also produces a generic "expression or NEWLINE expected" error.
     * The filter should suppress the parser error so only our friendly
     * single-quote message remains.
     */
    fun testSingleQuoteStringParserErrorIsSuppressed() {
        myFixture.enableInspections(CrystalSingleQuoteStringInspection())
        myFixture.configureByText("test.cr", "e = 'hello world'")
        val highlights = myFixture.doHighlighting()

        val errors = highlights.filter { it.severity == HighlightSeverity.ERROR }

        // Should have exactly one error: our friendly single-quote message
        assertEquals("Should show exactly one error, not duplicate parser + inspection", 1, errors.size)
        assertTrue(
            "Should show friendly message, not parser error. Got: ${errors.firstOrNull()?.description}",
            errors.firstOrNull()?.description?.contains("single quotes can only contain one character") == true
        )
    }

    /**
     * When a heredoc start has no matching end, the parser produces a generic
     * syntax error. The filter should suppress it so only our annotator message
     * about the missing end delimiter remains.
     */
    fun testMissingHeredocEndParserErrorIsSuppressed() {
        myFixture.configureByText("test.cr", "x = <<-EOF\n  hello")
        val highlights = myFixture.doHighlighting()

        val errors = highlights.filter { it.severity == HighlightSeverity.ERROR }

        // Should have exactly one error: our friendly heredoc message
        assertEquals("Should show exactly one error, not duplicate parser + annotator", 1, errors.size)
        assertTrue(
            "Should show friendly message, not parser error. Got: ${errors.firstOrNull()?.description}",
            errors.firstOrNull()?.description?.contains("Missing heredoc end delimiter") == true
        )
    }

    /**
     * Normal parser errors (e.g. unexpected token) that we do NOT handle
     * ourselves should still be shown.
     */
    fun testUnhandledParserErrorIsStillShown() {
        myFixture.configureByText("test.cr", "def foo(bar,)")
        val highlights = myFixture.doHighlighting()

        val errors = highlights.filter { it.severity == HighlightSeverity.ERROR }

        // Should still show the parser error for trailing comma
        assertTrue("Should still show unhandled parser errors", errors.isNotEmpty())
    }
}
