package de.magynhard.crystal

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.highlighting.CrystalSyntaxHighlighter

/**
 * Tests for CrystalAnnotator — semantic highlighting of type declarations,
 * method declarations, and parameter highlighting (definition + usage).
 *
 * Uses enforcedTextAttributes, so we check forcedTextAttributes (not forcedTextAttributesKey).
 */
class CrystalAnnotatorTest : BasePlatformTestCase() {

    private fun hasEnforcedHighlight(text: String, key: com.intellij.openapi.editor.colors.TextAttributesKey): Boolean {
        val highlights = myFixture.doHighlighting()
        val scheme = EditorColorsManager.getInstance().globalScheme
        val expectedAttrs = scheme.getAttributes(key)
        return highlights.any { h ->
            h.text == text && (
                h.forcedTextAttributesKey == key ||
                h.forcedTextAttributes == expectedAttrs
            )
        }
    }

    // ==================== Type declaration highlighting ====================

    fun testClassNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "class Apfel\nend")
        assertTrue("Class name 'Apfel' should be highlighted as CLASS_DECLARATION",
            hasEnforcedHighlight("Apfel", CrystalSyntaxHighlighter.CLASS_DECLARATION))
    }

    fun testModuleNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "module Utils\nend")
        assertTrue("Module name 'Utils' should be highlighted as CLASS_DECLARATION",
            hasEnforcedHighlight("Utils", CrystalSyntaxHighlighter.CLASS_DECLARATION))
    }

    fun testStructNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "struct Point\nend")
        assertTrue("Struct name 'Point' should be highlighted as CLASS_DECLARATION",
            hasEnforcedHighlight("Point", CrystalSyntaxHighlighter.CLASS_DECLARATION))
    }

    fun testEnumNameHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "enum Color\nRed\nend")
        assertTrue("Enum name 'Color' should be highlighted as CLASS_DECLARATION",
            hasEnforcedHighlight("Color", CrystalSyntaxHighlighter.CLASS_DECLARATION))
    }

    fun testNamespacedClassNameHighlighted() {
        myFixture.configureByText("test.cr", "class Foo::Bar\nend")
        assertTrue("Namespace 'Foo' should be highlighted as CLASS_DECLARATION",
            hasEnforcedHighlight("Foo", CrystalSyntaxHighlighter.CLASS_DECLARATION))
        assertTrue("Class 'Bar' should be highlighted as CLASS_DECLARATION",
            hasEnforcedHighlight("Bar", CrystalSyntaxHighlighter.CLASS_DECLARATION))
    }

    // ==================== Parameter highlighting ====================

    fun testParameterHighlightedInDefinition() {
        myFixture.configureByText("test.cr", "def greet(name)\nend")
        assertTrue("Parameter 'name' should be highlighted as PARAMETER",
            hasEnforcedHighlight("name", CrystalSyntaxHighlighter.PARAMETER))
    }

    fun testMultipleParametersHighlighted() {
        myFixture.configureByText("test.cr", "def add(a, b)\nend")
        assertTrue("Parameter 'a' should be highlighted as PARAMETER",
            hasEnforcedHighlight("a", CrystalSyntaxHighlighter.PARAMETER))
        assertTrue("Parameter 'b' should be highlighted as PARAMETER",
            hasEnforcedHighlight("b", CrystalSyntaxHighlighter.PARAMETER))
    }

    fun testParameterUsageHighlightedInMethodBody() {
        myFixture.configureByText("test.cr", """
            def greet(name)
              puts name
            end
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        val scheme = EditorColorsManager.getInstance().globalScheme
        val expectedAttrs = scheme.getAttributes(CrystalSyntaxHighlighter.PARAMETER)
        val paramUsages = highlights.filter { h ->
            h.text == "name" && (
                h.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER ||
                h.forcedTextAttributes == expectedAttrs
            )
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
        val scheme = EditorColorsManager.getInstance().globalScheme
        val expectedAttrs = scheme.getAttributes(CrystalSyntaxHighlighter.PARAMETER)
        val xAsParam = highlights.filter { h ->
            h.text == "x" && (
                h.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER ||
                h.forcedTextAttributes == expectedAttrs
            )
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
        val scheme = EditorColorsManager.getInstance().globalScheme
        val expectedAttrs = scheme.getAttributes(CrystalSyntaxHighlighter.PARAMETER)
        val paramHighlights = highlights.filter { h ->
            h.text == "name" && (
                h.forcedTextAttributesKey == CrystalSyntaxHighlighter.PARAMETER ||
                h.forcedTextAttributes == expectedAttrs
            )
        }
        assertEquals(
            "Only the parameter definition should be highlighted, not usage outside method",
            1, paramHighlights.size
        )
    }

    // ==================== Method declaration highlighting ====================

    fun testMethodNameHighlighted() {
        myFixture.configureByText("test.cr", "def greet\nend")
        assertTrue("Method name 'greet' should be highlighted as FUNCTION_DECLARATION",
            hasEnforcedHighlight("greet", CrystalSyntaxHighlighter.FUNCTION_DECLARATION))
    }
}
