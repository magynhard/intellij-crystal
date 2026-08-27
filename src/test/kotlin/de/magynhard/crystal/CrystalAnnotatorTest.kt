package de.magynhard.crystal

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.highlighting.CrystalSyntaxHighlighter

/**
 * Tests for CrystalAnnotator — semantic highlighting of type declarations,
 * method declarations, and parameter highlighting (definition + usage).
 */
class CrystalAnnotatorTest : BasePlatformTestCase() {

    // ==================== Type declaration highlighting ====================

    fun testClassNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "class Apfel\nend")
        val highlights = myFixture.doHighlighting()
        val h = highlights.find {
            it.text == "Apfel" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CONSTANT
        }
        assertNotNull("Class name 'Apfel' should be highlighted as CONSTANT", h)
    }

    fun testModuleNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "module Utils\nend")
        val highlights = myFixture.doHighlighting()
        val h = highlights.find {
            it.text == "Utils" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CONSTANT
        }
        assertNotNull("Module name 'Utils' should be highlighted as CONSTANT", h)
    }

    fun testStructNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "struct Point\nend")
        val highlights = myFixture.doHighlighting()
        val h = highlights.find {
            it.text == "Point" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CONSTANT
        }
        assertNotNull("Struct name 'Point' should be highlighted as CONSTANT", h)
    }

    fun testEnumNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "enum Color\nRed\nend")
        val highlights = myFixture.doHighlighting()
        val h = highlights.find {
            it.text == "Color" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CONSTANT
        }
        assertNotNull("Enum name 'Color' should be highlighted as CONSTANT", h)
    }

    fun testNamespacedClassNameHighlighted() {
        myFixture.configureByText("test.cr", "class Foo::Bar\nend")
        val highlights = myFixture.doHighlighting()
        val fooH = highlights.find {
            it.text == "Foo" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CONSTANT
        }
        val barH = highlights.find {
            it.text == "Bar" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CONSTANT
        }
        assertNotNull("Namespace 'Foo' should be highlighted as CONSTANT", fooH)
        assertNotNull("Class 'Bar' should be highlighted as CONSTANT", barH)
    }

    // ==================== Constant reference highlighting ====================

    fun testConstantReferenceHighlightedAsConstant() {
        myFixture.configureByText("test.cr", "class Apfel\nend\nx = Apfel.new")
        val highlights = myFixture.doHighlighting()
        val constH = highlights.filter {
            it.text == "Apfel" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CONSTANT
        }
        assertTrue("'Apfel' references should be highlighted as CONSTANT", constH.size >= 2)
    }

    // ==================== Parameter highlighting ====================

    fun testParameterHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "def greet(name)\nend")
        val highlights = myFixture.doHighlighting()
        val h = highlights.find {
            it.text == "name" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertNotNull("Parameter 'name' should be highlighted as PARAMETER", h)
    }

    fun testMultipleParametersHighlighted() {
        myFixture.configureByText("test.cr", "def add(a, b)\nend")
        val highlights = myFixture.doHighlighting()
        val aH = highlights.find {
            it.text == "a" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        val bH = highlights.find {
            it.text == "b" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertNotNull("Parameter 'a' should be highlighted as PARAMETER", aH)
        assertNotNull("Parameter 'b' should be highlighted as PARAMETER", bH)
    }

    fun testParameterUsageHighlightedInMethodBody() {
        myFixture.configureByText("test.cr", """
            def greet(name)
              puts name
            end
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        val paramUsages = highlights.filter {
            it.text == "name" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertTrue(
            "Parameter 'name' should be highlighted in both definition and usage, found ${paramUsages.size}",
            paramUsages.size >= 2
        )
    }

    fun testNonParameterIdentifierNotHighlightedAsParameter() {
        myFixture.configureByText("test.cr", """
            def greet(name)
              x = 1
              puts x
            end
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        val xAsParam = highlights.filter {
            it.text == "x" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertTrue("Local variable 'x' should NOT be highlighted as PARAMETER", xAsParam.isEmpty())
    }

    fun testParameterNotHighlightedOutsideMethod() {
        myFixture.configureByText("test.cr", """
            def greet(name)
            end
            puts name
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        val paramHighlights = highlights.filter {
            it.text == "name" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertEquals(
            "Only the parameter definition should be highlighted, not usage outside method",
            1, paramHighlights.size
        )
    }

    fun testBlockParameterUsageHighlighted() {
        myFixture.configureByText("test.cr", """
            3.times do |i|
              puts i
            end
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        val iParamUsages = highlights.filter {
            it.text == "i" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertTrue(
            "Block parameter 'i' should be highlighted in both definition and usage, found ${iParamUsages.size}",
            iParamUsages.size >= 2
        )
    }

    fun testBlockParameterMultipleParams() {
        myFixture.configureByText("test.cr", """
            [1, 2].each_with_index do |elem, idx|
              puts elem
              puts idx
            end
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        val elemHighlights = highlights.filter {
            it.text == "elem" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        val idxHighlights = highlights.filter {
            it.text == "idx" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertTrue("Block parameter 'elem' should be highlighted (def + usage)", elemHighlights.size >= 2)
        assertTrue("Block parameter 'idx' should be highlighted (def + usage)", idxHighlights.size >= 2)
    }

    // ==================== Method declaration highlighting ====================

    fun testMethodNameHighlighted() {
        myFixture.configureByText("test.cr", "def greet\nend")
        val highlights = myFixture.doHighlighting()
        val h = highlights.find {
            it.text == "greet" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CONSTANT
        }
        assertNotNull("Method name 'greet' should be highlighted as CONSTANT", h)
    }

    // ==================== Default identifier highlighting ====================

    fun testLocalVariableHighlightedAsIdentifier() {
        myFixture.configureByText("test.cr", "x = 1")
        val highlights = myFixture.doHighlighting()
        val h = highlights.find {
            it.text == "x" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.IDENTIFIER
        }
        assertNotNull("Local variable 'x' should be highlighted as IDENTIFIER", h)
    }

    // ==================== Regex escape validation ====================

    fun testInvalidRegexEscapeU() {
        myFixture.configureByText("test.cr", "r = /\\u0041/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        assertTrue("Should report error for \\u escape", errors.isNotEmpty())
        val error = errors.find { it.description?.contains("Invalid regex escape") == true }
        assertNotNull("Should have 'Invalid regex escape' message", error)
        assertTrue("Should suggest \\x{41} for \\u0041", error!!.description!!.contains("\\x{41}"))
    }

    fun testInvalidRegexEscapeUWithBraces() {
        myFixture.configureByText("test.cr", "r = /\\u{41}/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        assertTrue("Should report error for \\u{...} escape", errors.isNotEmpty())
        val error = errors.find { it.description?.contains("Invalid regex escape") == true }
        assertNotNull("Should have 'Invalid regex escape' message", error)
        assertTrue("Should suggest \\x{41} for \\u{41}", error!!.description!!.contains("\\x{41}"))
    }

    fun testInvalidRegexEscapeF() {
        myFixture.configureByText("test.cr", "r = /\\F/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        assertTrue("Should report error for \\F escape", errors.isNotEmpty())
    }

    fun testInvalidRegexEscapeL() {
        myFixture.configureByText("test.cr", "r = /\\L/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        assertTrue("Should report error for \\L escape", errors.isNotEmpty())
    }

    fun testInvalidRegexEscapeLowerL() {
        myFixture.configureByText("test.cr", "r = /\\l/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        assertTrue("Should report error for \\l escape", errors.isNotEmpty())
    }

    fun testValidRegexEscapeNNotReported() {
        myFixture.configureByText("test.cr", "r = /\\N/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val regexErrors = errors.filter { it.description?.contains("Invalid regex escape") == true }
        assertTrue("\\N is valid PCRE2 and should not produce errors", regexErrors.isEmpty())
    }

    fun testValidRegexEscapeNUCodepointNotReported() {
        myFixture.configureByText("test.cr", "r = /\\N{U+0041}/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val regexErrors = errors.filter { it.description?.contains("Invalid regex escape") == true }
        assertTrue("\\N{U+...} is valid PCRE2 and should not produce errors", regexErrors.isEmpty())
    }

    fun testInvalidRegexEscapeUpperU() {
        myFixture.configureByText("test.cr", "r = /\\U/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        assertTrue("Should report error for \\U escape", errors.isNotEmpty())
    }

    fun testValidRegexEscapeNotReported() {
        myFixture.configureByText("test.cr", "r = /\\t \\n \\d \\w \\x41/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val regexErrors = errors.filter { it.description?.contains("Invalid regex escape") == true }
        assertTrue("Valid escapes should not produce errors", regexErrors.isEmpty())
    }

    // ==================== Heredoc validation ====================

    fun testMissingHeredocEndDelimiter() {
        myFixture.configureByText("test.cr", "a = <<-TEST\n  content")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val heredocError = errors.find { it.description?.contains("Missing heredoc end delimiter") == true }
        assertNotNull("Should report missing heredoc end delimiter", heredocError)
    }

    fun testHeredocWithEndDelimiterNoError() {
        myFixture.configureByText("test.cr", "a = <<-TEST\n  content\nTEST")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val heredocErrors = errors.filter { it.description?.contains("Missing heredoc end delimiter") == true }
        assertTrue("Complete heredoc should not report missing delimiter", heredocErrors.isEmpty())
    }

    fun testHeredocEndDelimiterIndentExceedsContent() {
        // Content has minimum indent of 2, but end delimiter is indented to 3
        myFixture.configureByText("test.cr", "a = <<-TEST\n  line1\n   line2\n   TEST")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }

        // First check: is end delimiter found at all?
        val missingEnd = errors.find { it.description?.contains("Missing heredoc end") == true }
        assertNull("End delimiter should be found", missingEnd)

        val indentError = errors.find { it.description?.contains("indented too deeply") == true }
        assertNotNull("Should report end delimiter indent too deep. Got: ${errors.map { it.description }}", indentError)
    }

    fun testHeredocEndDelimiterIndentMatchesMinimumContent() {
        // Content has minimum indent of 2, end delimiter is also at 2 — valid
        myFixture.configureByText("test.cr", "a = <<-TEST\n  line1\n   line2\n  TEST")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val indentError = errors.find { it.description?.contains("indented too deeply") == true }
        assertNull("End delimiter at same indent as minimum content line should be valid. Got: ${errors.map { it.description }}", indentError)
    }

    fun testHeredocWithInterpolationIndent() {
        // Interpolation splits content into multiple HEREDOC_CONTENT tokens.
        // The indent calculation must concatenate them before measuring indent.
        myFixture.configureByText("test.cr", "a = <<-TEST\n  hello #{name} world\n  TEST")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val indentError = errors.find { it.description?.contains("indented too deeply") == true }
        assertNull("Heredoc with interpolation should not report indent error. Got: ${errors.map { it.description }}", indentError)
    }

    // ==================== Single-quote string validation ====================

    fun testInvalidSingleQuoteString() {
        myFixture.enableInspections(de.magynhard.crystal.highlighting.CrystalSingleQuoteStringInspection())
        myFixture.configureByText("test.cr", "e = 'hello world'")
        val highlights = myFixture.doHighlighting()
        
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val singleQuoteError = errors.find { it.description?.contains("single quotes can only contain one character") == true }
        assertNotNull("Should report invalid single-quote string. Got: ${errors.map { it.description }}", singleQuoteError)
    }

    fun testValidSingleQuoteChar() {
        myFixture.configureByText("test.cr", "c = 'a'")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        val singleQuoteErrors = errors.filter { it.description?.contains("single quotes can only contain one character") == true }
        assertTrue("Valid char literal should not produce error", singleQuoteErrors.isEmpty())
    }

    // ==================== Heredoc PSI-enforced coloring (v12.2) ====================

    private fun schemeForeground(key: TextAttributesKey) =
        EditorColorsManager.getInstance().globalScheme.getAttributes(key)?.foregroundColor

    private fun forcedStringOverlaps(text: String, range: TextRange) =
        myFixture.doHighlighting().filter { info ->
            info.forcedTextAttributes?.foregroundColor == schemeForeground(CrystalSyntaxHighlighter.STRING)
                && TextRange(info.getStartOffset(), info.getEndOffset()).intersects(range.startOffset, range.endOffset)
        }

    fun testHeredocBodyContentEnforcedWithStringColor() {
        myFixture.configureByText("test.cr", "f = <<-EXAMPLE\n  Some stuff inside\nEXAMPLE")
        val contentRange = TextRange(myFixture.file.text.indexOf("Some stuff"), myFixture.file.text.indexOf("inside") + 6)
        val enforced = forcedStringOverlaps(myFixture.file.text, contentRange)
        assertTrue("Body content must carry enforced string color", enforced.isNotEmpty())
    }

    fun testHeredocDelimitersEnforced() {
        myFixture.configureByText("test.cr", "f = <<-EXAMPLE\n  Some stuff inside\nEXAMPLE")
        val highlights = myFixture.doHighlighting()
        val delimiterFg = schemeForeground(CrystalSyntaxHighlighter.HEREDOC_DELIMITER)
        val marker = highlights.find {
            it.text.contains("<<-EXAMPLE") && it.forcedTextAttributes?.foregroundColor == delimiterFg
        }
        assertNotNull("Header marker must carry enforced delimiter color", marker)
        val terminator = highlights.find {
            it.getStartOffset() > myFixture.file.text.indexOf("Some stuff")
                && it.text.contains("EXAMPLE") && it.forcedTextAttributes?.foregroundColor == delimiterFg
        }
        assertNotNull("Terminator must carry enforced delimiter color", terminator)
    }

    fun testMultiHeredocAllBodiesEnforced() {
        myFixture.configureByText("test.cr", "assert_diff(<<-FIRST, <<-SECOND)\n  one\nFIRST\n  two\nSECOND")
        val firstBody = TextRange(myFixture.file.text.indexOf("one"), myFixture.file.text.indexOf("one") + 3)
        val secondBody = TextRange(myFixture.file.text.indexOf("two"), myFixture.file.text.indexOf("two") + 3)
        assertTrue(forcedStringOverlaps(myFixture.file.text, firstBody).isNotEmpty())
        assertTrue(forcedStringOverlaps(myFixture.file.text, secondBody).isNotEmpty())
    }

    fun testInterpolationInsideHeredocKeepsItsOwnColors() {
        myFixture.configureByText("test.cr", "f = <<-EXAMPLE\n  value #{name} here\nEXAMPLE")
        val text = myFixture.file.text
        val interpStart = text.indexOf("#{")
        val interpEnd = text.indexOf("}") + 1
        val stringForcedInside = myFixture.doHighlighting().filter { info ->
            val forced = info.forcedTextAttributes?.foregroundColor
            forced == schemeForeground(CrystalSyntaxHighlighter.STRING)
                && info.getStartOffset() < interpEnd && info.getEndOffset() > interpStart
        }
        assertTrue(
            "Enforced string color must not flood interpolation regions",
            stringForcedInside.isEmpty())
    }
}
