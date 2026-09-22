package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalParameterOrderInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalParameterOrderInspection::class.java)
    }

    fun testRequiredAfterOptionalIsReported() {
        myFixture.configureByText("test.cr", """
            def foo(a = 1, <error descr="Required parameter must have a default value">b</error>)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOptionalAfterRequiredIsValid() {
        myFixture.configureByText("test.cr", """
            def foo(a, b = 2)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredAfterNamedOnlySplatIsValid() {
        myFixture.configureByText("test.cr", """
            def foo(*rest, named : Int32)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredAfterBareSplatIsValid() {
        myFixture.configureByText("test.cr", """
            def foo(*, named : Int32)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequiredAfterSplatFollowingOptionalIsValid() {
        myFixture.configureByText("test.cr", """
            def foo(a = 1, *rest, named : Int32)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOptionalBeforeBlockAndDoubleSplatIsValid() {
        myFixture.configureByText("test.cr", """
            def with_block(a = 1, &block)
            end
            def with_kwargs(a = 1, **options)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMacroRequiredAfterOptionalIsReported() {
        myFixture.configureByText("test.cr", """
            macro foo(a = 1, <error descr="Required parameter must have a default value">b</error>)
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
