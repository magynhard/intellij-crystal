package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalUnusedVariableInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalUnusedVariableInspection::class.java)
    }

    // ==================== Basic Unused Variables ====================

    fun testUnusedVariable() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Variable 'x' is never used">x</weak_warning> = 42
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUsedVariable() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 42
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testUnderscorePrefixNoWarning() {
        myFixture.configureByText("test.cr", """
            def foo
              _unused = 42
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Reassignment ====================

    fun testReassignmentFirstUnused() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 1
              x = 2
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testReassignmentLastUnused() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 1
              puts x
              <weak_warning descr="Value assigned to 'x' is never used">x</weak_warning> = 2
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testReassignmentBothUsed() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 1
              puts x
              x = 2
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Compound Assignment ====================

    fun testCompoundAssignmentNoWarning() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 0
              x += 1
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Method Parameters ====================

    fun testMethodParameterNoWarning() {
        myFixture.configureByText("test.cr", """
            def foo(x : Int32, y : String)
              puts x
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Instance/Class Vars ====================

    fun testInstanceVarNotChecked() {
        myFixture.configureByText("test.cr", """
            def foo
              @name = "hello"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testClassVarNotChecked() {
        myFixture.configureByText("test.cr", """
            def foo
              @@count = 0
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Variable Used in Branch ====================

    fun testVariableUsedInIfBranch() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 42
              if true
                puts x
              end
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Multiple Unused Variables ====================

    fun testMultipleUnusedVariables() {
        myFixture.configureByText("test.cr", """
            def foo
              <weak_warning descr="Variable 'a' is never used">a</weak_warning> = 1
              <weak_warning descr="Variable 'b' is never used">b</weak_warning> = 2
              c = 3
              puts c
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Top-Level Variables ====================

    fun testTopLevelUnusedVariable() {
        myFixture.configureByText("test.cr", """
            <weak_warning descr="Variable 'f' is never used">f</weak_warning> = "hello"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testTopLevelUsedVariable() {
        myFixture.configureByText("test.cr", """
            f = "hello"
            puts f
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Variable Used in Expression ====================

    fun testVariableUsedInArithmeticExpression() {
        myFixture.configureByText("test.cr", """
            def foo
              local_var = "local"
              p = local_var + "sdf"
              puts "a:#{p}"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInArithmeticExpressionTopLevel() {
        myFixture.configureByText("test.cr", """
            local_var = "local"
            p = local_var + "sdf"
            puts "a:#{p}"
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInAddition() {
        myFixture.configureByText("test.cr", """
            def foo
              x = 1
              y = x + 2
              puts y
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInStringConcat() {
        myFixture.configureByText("test.cr", """
            def foo
              name = "world"
              greeting = "hello " + name
              puts greeting
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testVariableUsedInStringInterpolation() {
        myFixture.configureByText("test.cr", """
            def foo
              name = "world"
              puts "hello #{name}"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}
