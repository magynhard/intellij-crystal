package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalArgumentOrderInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalArgumentOrderInspection::class.java)
    }

    fun testPositionalAfterNamedIsReported() {
        myFixture.configureByText("test.cr", """
            def f(a, b = 0)
            end
            f(a: 1, <error descr="expected named argument, not 2">2</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSplatAfterNamedIsReported() {
        myFixture.configureByText("test.cr", """
            def f(a, *xs)
            end
            xs = {1, 2}
            f(a: 1, <error descr="expected named argument, not *">*xs</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDoubleSplatAfterNamedIsReported() {
        myFixture.configureByText("test.cr", """
            def f(a, **opts)
            end
            opts = {b: 2}
            f(a: 1, <error descr="expected named argument, not **">**opts</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testOutAfterNamedIsReported() {
        myFixture.configureByText("test.cr", """
            def f(a, out y)
              y = 1
            end
            x = 0
            f(a: 1, <error descr="expected named argument, not out">out x</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBarePositionalAfterNamedIsReported() {
        myFixture.configureByText("test.cr", """
            def f(a, b = 0)
            end
            f a: 1, <error descr="expected named argument, not 2">2</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBareSplatAfterNamedIsReported() {
        myFixture.configureByText("test.cr", """
            def f(a, *xs)
            end
            xs = {1, 2}
            f a: 1, <error descr="expected named argument, not *">*xs</error>
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testHeredocAfterNamedIsReported() {
        myFixture.configureByText("test.cr", """
            def f(a, b = 0)
            end
            f(a: 1, <error descr="expected named argument, not <<-A"><<-A</error>)
            hello
            A
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testAssignmentAfterNamedIsReported() {
        myFixture.configureByText("test.cr", """
            def f(a, b = 0)
            end
            f(a: 1, <error descr="expected named argument, not x">x = 2</error>)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testPositionalBeforeNamedIsValid() {
        myFixture.configureByText("test.cr", """
            def f(x, a = 0)
            end
            f(1, a: 2)
            f 1, a: 2
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSplatBeforeNamedIsValid() {
        myFixture.configureByText("test.cr", """
            def f(*xs, a = 0)
            end
            xs = {1, 2}
            f(*xs, a: 1)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBlockPassAfterNamedIsValid() {
        myFixture.configureByText("test.cr", """
            def f(a)
            end
            blk = ->(x : Int32) { x }
            f(a: 1, &blk)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDoBlockAfterNamedIsValid() {
        myFixture.configureByText("test.cr", """
            def f(a)
            end
            f(a: 1) do
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testSpacedColonDoesNotOpenNamedPhase() {
        myFixture.configureByText("test.cr", """
            def f(x, y = 0)
            end
            f(x : Int32, 2)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNamedOutValueIsValid() {
        myFixture.configureByText("test.cr", """
            def f(target)
            end
            x = 0
            f(target: out x)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTrailingCommaAfterNamedIsValid() {
        myFixture.configureByText("test.cr", """
            def f(a, b = 0)
            end
            f(a: 1,)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testMacroTailStopsReporting() {
        myFixture.configureByText("test.cr", """
            def f(a, b = 0)
            end
            f(a: 1, {% if true %}2{% end %})
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
