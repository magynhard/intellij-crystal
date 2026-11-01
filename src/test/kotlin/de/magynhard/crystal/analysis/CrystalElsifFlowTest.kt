package de.magynhard.crystal.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalElsifFlowTest : BasePlatformTestCase() {
    fun testLaterBareIsAOperandSeesGuardedType() {
        myFixture.configureByText("test.cr", """
            class Parent
            end
            class Child < Parent
            end
            def visit(value : Parent)
              if previous
                false
              elsif value.is_a? Child && va<caret>lue.present?
                value
              end
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals("Child", CrystalTypeSetResolver.session(leaf).resolveVariable("value", leaf).render())
    }

    fun testCompoundElsifConditionDoesNotReenterItsOwnVariableFlow() {
        myFixture.configureByText("test.cr", """
            def visit(left, right)
              if previous
                false
              elsif (left) && ri<caret>ght
                true
              end
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val session = CrystalTypeSetResolver.session(leaf)
        assertEquals(CrystalTypeResolution.Unknown, session.resolveVariable("right", leaf))
    }

    fun testElsifTypeCheckReceiverKeepsItsIncomingType() {
        myFixture.configureByText("test.cr", """
            class Parent
            end
            class Child < Parent
            end
            def visit(value : Parent)
              if previous
                false
              elsif va<caret>lue.is_a? Child
                value
              end
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals("Parent", CrystalTypeSetResolver.session(leaf).resolveVariable("value", leaf).render())
    }
}
