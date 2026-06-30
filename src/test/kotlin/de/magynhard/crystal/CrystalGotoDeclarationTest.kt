package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.CrystalMethodDefinition

/**
 * Tests for Go to Definition on DOT-call expressions (e.g. Apfel.tanzen, obj.method).
 *
 * Resolution path mirrors what the platform does on Ctrl+B:
 * 1. PsiReference on the element or its parent (CrystalDotCallAccess for DOT-calls)
 * 2. GotoDeclarationHandler fallback for the `.new` constructor special case.
 */
class CrystalGotoDeclarationTest : BasePlatformTestCase() {

    /**
     * Resolves the target at the caret using the real platform flow:
     * - Element at caret (leaf IDENTIFIER / CONSTANT)
     * - Walk up to find a PsiReference (CrystalDotCallAccess for DOT-calls,
     *   CrystalVariableReference for top-level calls)
     * - If the reference resolves, return that target — this is what
     *   IdentifierHighlightingComputer and the Documentation Provider both use.
     * - If no reference resolves, fall back to the GotoDeclarationHandler
     *   (handles the `.new` constructor resolution order).
     */
    private fun gotoTargets(code: String): Array<out PsiElement>? {
        myFixture.configureByText("test.cr", code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null

        // 1. Reference resolution via PsiReference on the element or its parent.
        //    Covers CrystalDotCallAccess for DOT-calls and CrystalVariableReference
        //    for top-level calls.
        val ref = element.reference ?: element.parent?.reference
        val resolved = ref?.resolve()
        if (resolved != null) return arrayOf(resolved)

        // 2. GotoDeclarationHandler fallback — exercised by the platform when no
        //    PsiReference resolves. Handles the .new constructor special case
        //    (resolves to self.new > record > initialize).
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

    /**
     * Behaviour change after the unified-reference refactor:
     * `x.greet` where `x = 1` (Int) is no longer resolved via name-only matching.
     * `greet` is a top-level `def`, not a method on `Int`, so `CrystalMethodByClassIndex`
     * returns no match. Resolution returns null (no false positives) instead of the
     * old behaviour of jumping to the first method named `greet` project-wide.
     */
    fun testTopLevelMethodViaDotCallOnUnknownReceiverReturnsNull() {
        val targets = gotoTargets("""
            def greet
            end
            x = 1
            x.gre<caret>et
        """.trimIndent())
        // `greet` exists as a top-level def but is NOT a method of `Int` (x's type).
        // No false-positive name-only matching — return null per the no-guessing rule.
        assertNull("Should not resolve via name-only when receiver type is unknown/unrelated", targets)
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
        // No DOT before "hello" — GotoDeclarationHandler returns null for DOT-only logic.
        // The variable_reference PsiReference also resolves to the method definition,
        // so the GotoDeclarationHandler is not invoked. But we still expect the test to
        // NOT go through the handler (it goes through the reference instead). The
        // handler's targets array should be empty here.
        // This test confirms the handler itself returns null for bare identifiers
        // (not preceded by DOT) — the resolution is purely via the reference.
        val handler = CrystalGotoDeclarationHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val handlerTargets = handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
        assertNull("Handler should return null when no DOT precedes identifier", handlerTargets)
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

    // ==================== ".new" constructor resolution ====================

    fun testNewGoesToInitialize() {
        val targets = gotoTargets("""
            class Senf
              def initialize(x : Int32)
              end
            end
            Senf.n<caret>ew
        """.trimIndent())
        assertNotNull("Should resolve Senf.new to def initialize", targets)
        assertTrue(targets!!.isNotEmpty())
        assertTrue("Target should be a method definition", targets[0] is CrystalMethodDefinition)
        assertEquals("initialize", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testNewGoesToSelfNewWhenDefined() {
        val targets = gotoTargets("""
            class Senf
              def self.new
              end
              def initialize
              end
            end
            Senf.n<caret>ew
        """.trimIndent())
        assertNotNull("Should resolve Senf.new to def self.new (priority over initialize)", targets)
        assertTrue(targets!!.isNotEmpty())
        assertTrue("Target should be a method definition", targets[0] is CrystalMethodDefinition)
        assertEquals("new", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testNewGoesToRecord() {
        val targets = gotoTargets("""
            record Config, host : String, port : Int32 = 80
            Config.n<caret>ew
        """.trimIndent())
        assertNotNull("Should resolve Config.new to record definition", targets)
        assertTrue(targets!!.isNotEmpty())
        assertTrue("Target should be the record macro call",
            targets[0].text.contains("record"))
    }

    fun testNewOnUnknownClassReturnsNull() {
        val targets = gotoTargets("""
            class Senf
              def initialize
              end
            end
            Unbekannt.n<caret>ew
        """.trimIndent())
        // No class "Unbekannt" — should return null, not every "new" method in the project.
        assertNull("Should return null for unknown class .new", targets)
    }
}
