package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
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
 * - `.new` constructor → resolves through shared constructor metadata; overloads are
 *   exposed through the polyvariant reference and remain ambiguous to `resolve()`.
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

    private fun multiResolveAtCaret(code: String): List<PsiElement> {
        myFixture.configureByText("test.cr", code)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset) ?: return emptyList()
        val dotCall = PsiTreeUtil.getParentOfType(leaf, CrystalDotCallAccess::class.java, false)
            ?: return emptyList()
        val reference = dotCall.reference as? PsiPolyVariantReference ?: return emptyList()
        return reference.multiResolve(false).mapNotNull { it.element }
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
        val resolved = resolveAtCaret("""
            class Apfel
              def essen
              end
            end
            Apfel.es<caret>sen
        """.trimIndent())
        assertNull("Apfel.essen must not resolve an instance method", resolved)
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

    fun testIncludedInstanceMethodResolvesThroughNeutralHierarchy() {
        val resolved = resolveAtCaret("""
            module Feature
              def work
              end
            end
            class Service
              include Feature
            end
            service = Service.new
            service.wo<caret>rk
        """.trimIndent())

        assertNotNull(resolved)
        assertEquals("work", (resolved as CrystalMethodDefinition).name)
        assertEquals("Feature", CrystalPsiUtils.getEnclosingType(resolved)?.let(CrystalPsiUtils::buildQualifiedName))
    }

    // ==================== .new constructor ====================

    fun testDotNewResolvesToInitialize() {
        // .new resolves to initialize (Crystal's constructor: def self.new > record > initialize)
        val resolved = resolveAtCaret("""
            class Senf
              def initialize(x : Int32)
              end
            end
            Senf.n<caret>ew
        """.trimIndent())
        assertNotNull(".new should resolve via CrystalDotCallReference", resolved)
        assertTrue("Should resolve to a method named 'initialize'",
            resolved is de.magynhard.crystal.psi.CrystalMethodDefinition
                && resolved.name == "initialize")
    }

    fun testRecordWinsSameIdentityConstructorCollisionLikeDotTargetResolver() {
        val resolved = resolveAtCaret("""
            record Config, value : Int32
            class Config
              def initialize(value : String)
              end
            end
            Config.n<caret>ew
        """.trimIndent())

        assertTrue(resolved is CrystalMethodCallExpression)
        assertTrue(resolved?.text?.startsWith("record Config") == true)
    }

    fun testOverloadedConstructorReferenceIsAmbiguous() {
        val resolved = resolveAtCaret("""
            class Service
              def self.new(value : Int32)
              end
              def self.new(value : String)
              end
            end
            Service.n<caret>ew
        """.trimIndent())

        assertNull(resolved)
    }

    fun testStaticOverloadsExposeEveryExactTargetThroughPolyvariantReference() {
        val code = """
            module Other
              class Service
                def self.build(value : Bool)
                end
              end
            end
            class Service
              def self.build(value : Int32)
              end
              def self.build(value : String)
              end
            end
            Service.bu<caret>ild(1)
        """.trimIndent()

        assertNull(resolveAtCaret(code))
        val targets = multiResolveAtCaret(code).filterIsInstance<CrystalMethodDefinition>()
        assertEquals(2, targets.size)
        assertTrue(targets.all {
            CrystalPsiUtils.getEnclosingType(it)?.let(CrystalPsiUtils::buildQualifiedName) == "Service"
        })
    }

    fun testInstanceOverloadsExposeEveryExactTargetThroughPolyvariantReference() {
        val code = """
            module Other
              class Service
                def run(value : Bool)
                end
              end
            end
            class Service
              def run(value : Int32)
              end
              def run(value : String)
              end
            end
            service = Service.new
            service.ru<caret>n(1)
        """.trimIndent()

        assertNull(resolveAtCaret(code))
        val targets = multiResolveAtCaret(code).filterIsInstance<CrystalMethodDefinition>()
        assertEquals(2, targets.size)
        assertTrue(targets.all {
            CrystalPsiUtils.getEnclosingType(it)?.let(CrystalPsiUtils::buildQualifiedName) == "Service"
        })
    }

    fun testConstructorOverloadsExposeEveryExactTargetThroughPolyvariantReference() {
        val code = """
            class Service
              def self.new(value : Int32)
              end
              def self.new(value : String)
              end
            end
            Service.n<caret>ew(1)
        """.trimIndent()

        assertNull(resolveAtCaret(code))
        assertEquals(2, multiResolveAtCaret(code).filterIsInstance<CrystalMethodDefinition>().size)
    }

    fun testGotoHandlerReturnsPolyvariantOverloadTargets() {
        myFixture.configureByText("test.cr", """
            class Service
              def self.build(value : Int32)
              end
              def self.build(value : String)
              end
            end
            Service.bu<caret>ild(1)
        """.trimIndent())
        val source = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = CrystalGotoDeclarationHandler().getGotoDeclarationTargets(
            source,
            myFixture.caretOffset,
            myFixture.editor
        ).orEmpty()

        assertEquals(2, targets.filterIsInstance<CrystalMethodDefinition>().size)
    }
}
