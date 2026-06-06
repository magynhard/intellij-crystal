package de.magynhard.crystal

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

    fun testInvalidRegexEscapeN() {
        myFixture.configureByText("test.cr", "r = /\\N{U+0041}/")
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.severity == com.intellij.lang.annotation.HighlightSeverity.ERROR }
        assertTrue("Should report error for \\N{name} escape", errors.isNotEmpty())
        val error = errors.find { it.description?.contains("Invalid regex escape") == true }
        assertNotNull("Should have 'Invalid regex escape' message", error)
        assertTrue("Should suggest \\x{41} for \\N{U+0041}", error!!.description!!.contains("\\x{41}"))
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
        val indentErrors = errors.filter { it.description?.contains("indented too deeply") == true }
        assertTrue("End delimiter at min content indent should be valid", indentErrors.isEmpty())
    }
}
