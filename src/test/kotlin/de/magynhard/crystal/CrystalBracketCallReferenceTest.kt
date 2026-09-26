package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalBracketCallAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition

class CrystalBracketCallReferenceTest : BasePlatformTestCase() {

    /** Walks to the `CrystalBracketCallAccess` composite at the caret and returns its reference target. */
    private fun resolveAtCaret(code: String): PsiElement? {
        myFixture.configureByText("test.cr", code)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        val bracket = PsiTreeUtil.getParentOfType(leaf, CrystalBracketCallAccess::class.java, false)
            ?: return null
        return bracket.reference?.resolve()
    }

    fun testStaticZeroArityResolvesToDefinition() {
        val resolved = resolveAtCaret("""
            class Foo
              def self.[]
                42
              end
            end
            Foo[<caret>]
        """.trimIndent())
        assertNotNull("Foo[] should resolve to def self.[]", resolved)
        assertTrue("Should resolve to a method definition",
            resolved is CrystalMethodDefinition)
        assertEquals("[]", (resolved as CrystalMethodDefinition).name)
    }

    fun testStaticWithArgumentResolvesToDefinition() {
        val resolved = resolveAtCaret("""
            class Foo
              def self.[](x)
                x
              end
            end
            Foo[<caret>1]
        """.trimIndent())
        assertNotNull("Foo[1] should resolve to def self.[](x)", resolved)
        assertTrue("Should resolve to a method definition",
            resolved is CrystalMethodDefinition)
        assertEquals("[]", (resolved as CrystalMethodDefinition).name)
    }

    fun testStructReceiverResolvesToDefinition() {
        val resolved = resolveAtCaret("""
            struct Config
              def self.[]
                42
              end
            end
            Config[<caret>]
        """.trimIndent())
        assertNotNull("Config[] should resolve to def self.[]", resolved)
        assertTrue("Should resolve to a method definition",
            resolved is CrystalMethodDefinition)
    }

    fun testModuleReceiverResolvesToDefinition() {
        val resolved = resolveAtCaret("""
            module Settings
              def self.[]
                42
              end
            end
            Settings[<caret>]
        """.trimIndent())
        assertNotNull("Settings[] should resolve to def self.[]", resolved)
        assertTrue("Should resolve to a method definition",
            resolved is CrystalMethodDefinition)
    }

    fun testMacroBackedReceiverResolvesToNothing() {
        val resolved = resolveAtCaret("""
            Int64[<caret>]
        """.trimIndent())
        assertNull("Int64[] is macro-backed, not a def — must stay suppressed", resolved)
    }

    fun testUnknownReceiverResolvesToNothing() {
        val resolved = resolveAtCaret("""
            Nope[<caret>]
        """.trimIndent())
        assertNull("Should NOT resolve when the receiver type is unknown", resolved)
    }

    fun testAmbiguousReceiverResolvesToNothing() {
        val resolved = resolveAtCaret("""
            module A
              class Foo
                def self.[]
                  1
                end
              end
            end
            module B
              class Foo
                def self.[]
                  2
                end
              end
            end
            Foo[<caret>]
        """.trimIndent())
        assertNull("Should NOT resolve when the receiver type is ambiguous", resolved)
    }

    fun testInstanceReceiverResolvesToNothing() {
        val resolved = resolveAtCaret("""
            class Bar
              def []
                42
              end
            end
            obj = Bar.new
            obj[<caret>]
        """.trimIndent())
        assertNull("Instance receivers stay suppressed (index reads keep their own resolution)", resolved)
    }
}
