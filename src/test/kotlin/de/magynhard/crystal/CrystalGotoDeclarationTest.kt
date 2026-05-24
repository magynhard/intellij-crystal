package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.CrystalMethodDefinition

/**
 * Tests for Go to Definition on DOT-call expressions (e.g. Apfel.tanzen, obj.method).
 */
class CrystalGotoDeclarationTest : BasePlatformTestCase() {

    private fun gotoTargets(code: String): Array<out com.intellij.psi.PsiElement>? {
        myFixture.configureByText("test.cr", code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val handler = CrystalGotoDeclarationHandler()
        return handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
    }

    fun testSelfMethodViaClassDotCall() {
        val targets = gotoTargets("""
            class Apfel
              def self.tanzen
              end
            end
            Apfel.tan<caret>zen
        """.trimIndent())
        assertNotNull("Should resolve Apfel.tanzen to def self.tanzen", targets)
        assertTrue("Should have at least one target", targets!!.isNotEmpty())
        assertTrue("Target should be a method definition",
            targets[0] is CrystalMethodDefinition)
        assertEquals("tanzen", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testInstanceMethodViaDotCall() {
        val targets = gotoTargets("""
            class Apfel
              def essen
              end
            end
            a = Apfel.new
            a.es<caret>sen
        """.trimIndent())
        assertNotNull("Should resolve a.essen to def essen", targets)
        assertTrue(targets!!.isNotEmpty())
        assertTrue(targets[0] is CrystalMethodDefinition)
        assertEquals("essen", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testTopLevelMethodViaDotCall() {
        val targets = gotoTargets("""
            def greet
            end
            x = 1
            x.gre<caret>et
        """.trimIndent())
        // "greet" exists as a top-level def — handler should find it
        assertNotNull("Should find greet method", targets)
        assertTrue(targets!!.isNotEmpty())
    }

    fun testNonMethodIdentifierAfterDotReturnsNull() {
        val targets = gotoTargets("""
            x = 1
            x.nonexist<caret>ent
        """.trimIndent())
        // No method named "nonexistent" — should return null
        assertNull("Should return null for unknown method", targets)
    }

    fun testDotCallDoesNotTriggerWithoutDot() {
        val targets = gotoTargets("""
            def hello
            end
            hell<caret>o
        """.trimIndent())
        // No DOT before "hello" — GotoDeclarationHandler should return null
        // (variable_reference mixin handles this case instead)
        assertNull("Should return null when no DOT precedes identifier", targets)
    }

    fun testNestedClassMethodViaDotCall() {
        val targets = gotoTargets("""
            class Outer
              class Inner
                def self.run
                end
              end
            end
            Outer::Inner.r<caret>un
        """.trimIndent())
        assertNotNull("Should resolve nested class method", targets)
        assertTrue(targets!!.isNotEmpty())
        assertEquals("run", (targets[0] as CrystalMethodDefinition).name)
    }
}
