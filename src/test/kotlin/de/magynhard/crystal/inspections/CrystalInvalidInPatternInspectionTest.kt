package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidInPatternInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalInvalidInPatternInspection::class.java)
    }

    fun testBareIdentifierPatternIsError() {
        myFixture.configureByText("test.cr", """
            def foo(c)
              case c
              in String
                puts 1
              in <error descr="Invalid 'in' pattern: expected a constant, generic type, bool/nil literal, or question method">y</error>
                puts 2
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testConstantTypeAndLiteralPatternsAreClean() {
        myFixture.configureByText("test.cr", """
            enum Color
              Red
              Green
            end
            def foo(c : Color)
              case c
              in Color::Red
                puts 1
              in Color::Green
                puts 2
              end
            end
            def bar(b)
              case b
              in true
                puts 1
              in false
                puts 2
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testNestedCallPatternIsClean() {
        myFixture.configureByText("test.cr", """
            def foo(c)
              case c
              in String
                puts 1
              in foo(limit)
                puts 2
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testWhenClauseIsUnaffected() {
        myFixture.configureByText("test.cr", """
            def foo(c)
              y = 1
              case c
              when y
                puts 1
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
