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
        val apfelHighlight = highlights.find {
            it.text == "Apfel" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CLASS_DECLARATION
        }
        assertNotNull("Class name 'Apfel' should be highlighted as CLASS_DECLARATION", apfelHighlight)
    }

    fun testModuleNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "module Utils\nend")
        val highlights = myFixture.doHighlighting()
        val utilsHighlight = highlights.find {
            it.text == "Utils" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CLASS_DECLARATION
        }
        assertNotNull("Module name 'Utils' should be highlighted as CLASS_DECLARATION", utilsHighlight)
    }

    fun testStructNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "struct Point\nend")
        val highlights = myFixture.doHighlighting()
        val pointHighlight = highlights.find {
            it.text == "Point" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CLASS_DECLARATION
        }
        assertNotNull("Struct name 'Point' should be highlighted as CLASS_DECLARATION", pointHighlight)
    }

    fun testEnumNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "enum Color\nRed\nend")
        val highlights = myFixture.doHighlighting()
        val colorHighlight = highlights.find {
            it.text == "Color" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CLASS_DECLARATION
        }
        assertNotNull("Enum name 'Color' should be highlighted as CLASS_DECLARATION", colorHighlight)
    }

    fun testNamespacedClassNameHighlighted() {
        myFixture.configureByText("test.cr", "class Foo::Bar\nend")
        val highlights = myFixture.doHighlighting()
        val fooHighlight = highlights.find {
            it.text == "Foo" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CLASS_DECLARATION
        }
        val barHighlight = highlights.find {
            it.text == "Bar" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.CLASS_DECLARATION
        }
        assertNotNull("Namespace 'Foo' should be highlighted as CLASS_DECLARATION", fooHighlight)
        assertNotNull("Class 'Bar' should be highlighted as CLASS_DECLARATION", barHighlight)
    }

    // ==================== Parameter highlighting ====================

    fun testParameterHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "def greet(name)\nend")
        val highlights = myFixture.doHighlighting()
        val paramHighlight = highlights.find {
            it.text == "name" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertNotNull("Parameter 'name' should be highlighted as PARAMETER", paramHighlight)
    }

    fun testMultipleParametersHighlighted() {
        myFixture.configureByText("test.cr", "def add(a, b)\nend")
        val highlights = myFixture.doHighlighting()
        val aHighlight = highlights.find {
            it.text == "a" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        val bHighlight = highlights.find {
            it.text == "b" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER
        }
        assertNotNull("Parameter 'a' should be highlighted as PARAMETER", aHighlight)
        assertNotNull("Parameter 'b' should be highlighted as PARAMETER", bHighlight)
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
        // One in parameter definition, one in method body
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
        // Only the parameter definition, not the usage outside
        assertEquals(
            "Only the parameter definition should be highlighted, not usage outside method",
            1, paramHighlights.size
        )
    }

    // ==================== Method declaration highlighting ====================

    fun testMethodNameHighlighted() {
        myFixture.configureByText("test.cr", "def greet\nend")
        val highlights = myFixture.doHighlighting()
        val methodHighlight = highlights.find {
            it.text == "greet" && it.forcedTextAttributesKey == CrystalSyntaxHighlighter.FUNCTION_DECLARATION
        }
        assertNotNull("Method name 'greet' should be highlighted as FUNCTION_DECLARATION", methodHighlight)
    }
}
