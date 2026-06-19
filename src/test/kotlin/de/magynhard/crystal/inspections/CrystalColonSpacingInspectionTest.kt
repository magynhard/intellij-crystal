package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for colon spacing inspection.
 *
 * Note: `speed :String` (no space after :) causes a parse error because
 * the lexer produces SYMBOL_COLON(":String") instead of COLON + IDENTIFIER.
 * This case is handled by the parser error, not by this inspection.
 * The inspection catches cases where the parser succeeds but spacing is wrong.
 */
class CrystalColonSpacingInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ==================== Missing space BEFORE colon ====================
    // "speed: String" → parser succeeds, COLON token exists

    fun testMissingSpaceBeforeColonInParameter() {
        myFixture.configureByText("test.cr", """
            def foo(speed: String)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Correct spacing ====================

    fun testCorrectSpacingNoWarning() {
        myFixture.configureByText("test.cr", """
            def foo(speed : String)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Return type annotation ====================

    fun testReturnTypeCorrectSpacing() {
        myFixture.configureByText("test.cr", """
            def foo() : String
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testReturnTypeNoSpaceBeforeColon() {
        myFixture.configureByText("test.cr", """
            def foo(): String
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Default value exception ====================

    fun testSymbolDefaultExempt() {
        myFixture.configureByText("test.cr", """
            def foo(speed : String = :name)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testIntegerDefaultExempt() {
        myFixture.configureByText("test.cr", """
            def foo(count : Int32 = 0)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Multiple parameters ====================

    fun testMultipleParametersCorrectSpacing() {
        myFixture.configureByText("test.cr", """
            def foo(a : Int32, b : String, c : Bool)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== No false positive ====================

    fun testHashAccessNotFlagged() {
        myFixture.configureByText("test.cr", """
            h = {:key => 1}
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSymbolInMethodCallNotFlagged() {
        myFixture.configureByText("test.cr", """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end

              def essen(speed : String, anders : Symbol = :name) : String
                speed
              end
            end

            a = Apfelsaft.new "test", 1
            a.essen "gol", :lol
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
