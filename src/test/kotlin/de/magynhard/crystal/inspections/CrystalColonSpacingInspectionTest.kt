package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalColonSpacingInspectionTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ==================== Missing space BEFORE colon ====================
    // Parser can parse: "speed: String" (no space before :) → COLON token exists
    // Parser CANNOT parse: "speed:String" or "speed :String" (no space after :) → parse error

    fun testMissingSpaceBeforeColonInParameter() {
        myFixture.configureByText("test.cr", """
            def foo(speed: String)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

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
}
