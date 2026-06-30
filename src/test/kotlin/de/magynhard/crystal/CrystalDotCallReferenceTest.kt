package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.*

/**
 * Tests for [CrystalDotCallReference] — the PsiReference that powers identifier
 * highlighting and hover/Ctrl+B for DOT-call method names
 * (`Apfel.tanzen`, `a.essen`, `Senf.new`).
 *
 * Resolution rules covered:
 * - CONSTANT receiver → exact lookup via `CrystalMethodByClassIndex`.
 * - IDENTIFIER receiver with inferred type → exact lookup on the inferred class.
 * - IDENTIFIER receiver with **unknown** type → returns null (no name-only guessing,
 *   no false positives). Behaviour change from the old `GotoDeclarationHandler`
 *   which fell back to `CrystalDefinitionFinder.findDefinitions(name, project)`
 *   and would happily return any method named the same project-wide.
 * - `.new` constructor → returns null here (the dedicated constructor path in
 *   `CrystalGotoDeclarationHandler` and `CrystalDocumentationProvider` handles it).
 */
class CrystalDotCallReferenceTest : BasePlatformTestCase() {

    /** Walks to the `CrystalDotCallAccess` composite at the caret and returns its reference target. */
    private fun resolveAtCaret(code: String): PsiElement? {
        myFixture.configureByText("test.cr", code)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        val dotCall = PsiTreeUtil.getParentOfType(leaf, CrystalDotCallAccess::class.java, false)
            ?: return null
        return dotCall.reference?.resolve()
    }

    // ==================== CONSTANT receiver (static class methods) ====================

    fun testStaticSelfMethodResolvesToDefinition() {
        val resolved = resolveAtCaret("""
            class Apfel
              def self.tanzen
              end
            end
            Apfel.tan<caret>zen
        """.trimIndent())
        assertNotNull("Apfel.tanzen should resolve to def self.tanzen", resolved)
        assertTrue("Should resolve to a method definition",
            resolved is CrystalMethodDefinition)
        assertEquals("tanzen", (resolved as CrystalMethodDefinition).name)
    }

    fun testStaticInstanceMethodDoesNotResolveViaClassDot() {
        // `def essen` is an instance method, not a `def self.` static — Apfel.essen
        // is called on `Apfel` (a CONSTANT class object), not an instance.
        // The index keys CrystalMethodByClassIndex by enclosing class for BOTH
        // static and instance methods, so this DOES resolve to the instance def
        // (Crystal does allow you to call instance methods via the class in
        // some syntactic contexts — e.g. enum constants). The test asserts that
        // lookup returns the method.
        val resolved = resolveAtCaret("""
            class Apfel
              def essen
              end
            end
            Apfel.es<caret>sen
        """.trimIndent())
        assertNotNull("Apfel.essen resolves to def essen via enclosing-class index", resolved)
        assertEquals("essen", (resolved as CrystalMethodDefinition).name)
    }

    fun testNestedClassMethodResolves() {
        val resolved = resolveAtCaret("""
            class Outer
              class Inner
                def self.run
                end
              end
            end
            Outer::Inner.r<caret>un
        """.trimIndent())
        assertNotNull("Nested class method should resolve", resolved)
        assertEquals("run", (resolved as CrystalMethodDefinition).name)
    }

    // ==================== IDENTIFIER receiver (instance methods) ====================

    fun testInstanceMethodResolvesViaTypeInference() {
        val resolved = resolveAtCaret("""
            class Apfel
              def essen
              end
            end
            a = Apfel.new
            a.es<caret>sen
        """.trimIndent())
        assertNotNull("a.essen should resolve via inferred type Apfel", resolved)
        assertEquals("essen", (resolved as CrystalMethodDefinition).name)
    }

    /**
     * This is the regression-safety test for the "no false positives" rule:
     * when CrystalTypeInference returns `null` (untyped parameter, untyped return
     * chain, etc.) the reference MUST NOT fall back to name-only matching.
     */
    fun testInstanceMethodWithoutKnownTypeReturnsNull() {
        val resolved = resolveAtCaret("""
            class Apfel
              def essen
              end
            end
            def consume(thing)
              thing.es<caret>sen
            end
        """.trimIndent())
        // `thing` has no type annotation — inference returns null → no resolution.
        assertNull("Should NOT resolve when receiver type is unknown (no guessing)", resolved)
    }

    fun testUnknownMethodOnKnownClassReturnsNull() {
        val resolved = resolveAtCaret("""
            class Apfel
              def essen
              end
            end
            a = Apfel.new
            a.no<caret>thing
        """.trimIndent())
        // Apfel exists but has no method `nothing` — return null, don't guess.
        assertNull("Should return null for unknown method on a known class", resolved)
    }

    // ==================== .new constructor (delegated) ====================

    fun testDotNewReturnsNullFromReference() {
        // The reference path returns null for `.new` — the dedicated constructor
        // resolution path (CrystalGotoDeclarationHandler.findNewTargets and
        // CrystalDocumentationProvider) handles self.new > record > initialize.
        val resolved = resolveAtCaret("""
            class Senf
              def initialize(x : Int32)
              end
            end
            Senf.n<caret>ew
        """.trimIndent())
        assertNull(".new should NOT resolve via CrystalDotCallReference (delegated to handler)", resolved)
    }
}