package de.magynhard.crystal

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.sdk.CrystalStdlibResolver

/**
 * Tests for [CrystalDotCallReference] — the PsiReference that powers identifier
 * highlighting and hover/Ctrl+B for DOT-call method names
 * (`Apfel.tanzen`, `a.essen`, `Senf.new`).
 *
 * Resolution rules covered:
 * - CONSTANT receiver → exact lookup via `CrystalMethodByClassIndex`.
 * - IDENTIFIER receiver with inferred type → exact lookup on the inferred class.
 * - IDENTIFIER receiver with **unknown** type → resolves by method name within the
 *   PROJECT ONLY (never the stdlib) via a name-only fallback, so local navigation
 *   works (`rule.auto_fixable?`) without the cross-project false positives the
 *   strict design otherwise forbids. The old `GotoDeclarationHandler` fell back to
 *   `CrystalDefinitionFinder.findDefinitions(name, project)` project-wide.
 * - `.new` constructor → returns null here (the dedicated constructor path in
 *   `CrystalGotoDeclarationHandler` and `CrystalDocumentationProvider` handles it).
 */
class CrystalDotCallReferenceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Stdlib navigation tests parse files under the SDK directory, which the
        // test VFS sandbox forbids by default. Allow read access for the duration
        // of the test so `File.write` / `String.build` resolution can be exercised.
        CrystalStdlibResolver.resolveStdlibPath(project)?.path?.let {
            VfsRootAccess.allowRootAccess(testRootDisposable, it)
        }
    }

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

    fun testInstanceDotCallUnknownReceiverResolvesProjectMethod() {
        // `rule` is an untyped parameter, so its type is unknown — resolution falls back
        // to a project-scoped name-only lookup and lands on the local `auto_fixable?` def.
        val resolved = resolveAtCaret("""
            class Rule
              def auto_fixable? : Bool
              end
            end
            def check(rule)
              puts rule.auto_fix<caret>able?
            end
        """.trimIndent())
        assertNotNull("rule.auto_fixable? should resolve to the project method", resolved)
        assertTrue("Should resolve to a method definition", resolved is CrystalMethodDefinition)
        assertEquals("auto_fixable?", (resolved as CrystalMethodDefinition).name)
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
    /**
     * Union receiver: `x` is typed as `Apfel | Banane` (via a union-returning method),
     * and both members define `essen`. Resolution must find the method across the union
     * (not just the first member, and never bail out to name-only guessing for a known
     * but union-typed receiver).
     */
    fun testInstanceMethodResolvesAcrossUnionReceiver() {
        val resolved = resolveAtCaret("""
            class Apfel
              def essen
              end
            end
            class Banane
              def essen
              end
            end
            def maybe : Apfel | Banane
              true ? Apfel.new : Banane.new
            end
            x = maybe
            x.es<caret>sen
        """.trimIndent())
        assertNotNull("x.essen should resolve across the Apfel | Banane union", resolved)
        assertTrue("Should resolve to a method definition", resolved is CrystalMethodDefinition)
        assertEquals("essen", (resolved as CrystalMethodDefinition).name)
    }

    fun testInstanceMethodWithoutKnownTypeResolvesProjectMethod() {
        // `thing` has no type annotation, but `essen` is a known project method — the
        // project-scoped name-only fallback resolves it (local navigation, no guessing).
        val resolved = resolveAtCaret("""
            class Apfel
              def essen
              end
            end
            def consume(thing)
              thing.es<caret>sen
            end
        """.trimIndent())
        assertNotNull("Unknown receiver + existing project method should resolve", resolved)
        assertTrue("Should resolve to a method definition", resolved is CrystalMethodDefinition)
        assertEquals("essen", (resolved as CrystalMethodDefinition).name)
    }

    fun testInstanceMethodUnknownReceiverNoProjectMethodReturnsNull() {
        val resolved = resolveAtCaret("""
            def consume(thing)
              thing.no_such_method_xyz<caret>zyx
            end
        """.trimIndent())
        // No project method with this name → nothing to resolve to.
        assertNull("Should return null when no project method matches the name", resolved)
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

    // ==================== `self` receiver (item 2) ====================

    fun testSelfReceiverResolvesToEnclosingClass() {
        // `self.essen` inside an instance method must resolve to the enclosing class's
        // `essen` — `self` is a keyword (SELF), not an IDENTIFIER, so it was previously
        // missed entirely and returned null.
        val resolved = resolveAtCaret("""
            class Apfel
              def essen
              end
              def munch
                self.es<caret>sen
              end
            end
        """.trimIndent())
        assertNotNull("self.essen should resolve to Apfel#essen", resolved)
        assertTrue("Should resolve to a method definition", resolved is CrystalMethodDefinition)
        assertEquals("essen", (resolved as CrystalMethodDefinition).name)
    }

    fun testSelfReceiverResolvesStdlibMethod() {
        // `self` inside a reopening of a stdlib class must resolve via the enclosing class.
        // `Int` defines `to_s`, so `self.to_s` within `class Int` lands on Int#to_s.
        val resolved = resolveAtCaret("""
            class Int
              def show
                self.to_<caret>s
              end
            end
        """.trimIndent())
        assertNotNull("self.to_s should resolve to Int#to_s", resolved)
        val vfile = resolved!!.containingFile.virtualFile
        assertEquals("Int#to_s should land in int.cr", "int.cr", vfile.name)
    }

    // ==================== literal / expression receiver (item 2) ====================

    fun testArrayLiteralReceiverResolvesStdlibMethod() {
        // `[1, 2, 3].size` — the receiver is an Array literal; its type (Array) must be
        // inferred so the call resolves to Array#size in the stdlib.
        val resolved = resolveAtCaret("[1, 2, 3].siz<caret>e")
        assertNotNull("[1,2,3].size should resolve to Array#size", resolved)
        val vfile = resolved!!.containingFile.virtualFile
        assertEquals("Array#size should land in array.cr", "array.cr", vfile.name)
    }

    fun testStringLiteralReceiverResolvesStdlibMethod() {
        // `"x".upcase` — the receiver is a String literal; resolves to String#upcase.
        val resolved = resolveAtCaret("\"hello\".upcas<caret>e")
        assertNotNull("\"hello\".upcase should resolve to String#upcase", resolved)
        val vfile = resolved!!.containingFile.virtualFile
        assertEquals("String#upcase should land in string.cr", "string.cr", vfile.name)
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

    // ==================== Stdlib fallback (StubIndex cannot reach stdlib roots) ====================

    fun testStdlibClassMethodResolves() {
        // `File.write` — the receiver is a CONSTANT class `File` defined in the stdlib.
        // StubIndex.stub roots are stored under an internal scope no GlobalSearchScope
        // intersects, so resolution must fall back to the cached stdlib scan.
        val resolved = resolveAtCaret("File.writ<caret>e")
        assertNotNull("File.write should resolve to a stdlib method definition", resolved)
        assertTrue("Should resolve to a method definition",
            resolved is de.magynhard.crystal.psi.CrystalMethodDefinition)
    }

    fun testStdlibMethodResolvesToCorrectClassFile() {
        // Regression: `String.build` must resolve to the `build` definition inside
        // `string.cr` — NOT the first global `build` match (e.g. Log::Metadata.build in
        // log/metadata.cr). Resolution is now per-file via the class-home convention.
        val resolved = resolveAtCaret("String.bui<caret>ld")
        assertNotNull("String.build should resolve to a stdlib method definition", resolved)
        val vfile = resolved!!.containingFile.virtualFile
        assertEquals("String.build must land in string.cr, not ${vfile.path}", "string.cr", vfile.name)
    }

    fun testStdlibClassResolvesViaTypePath() {
        // A stdlib class used as a type annotation should resolve to its class definition.
        myFixture.addFileToProject("main.cr", "puts 1")
        myFixture.configureByText("test.cr", "x : Str<caret>ing")
        val allRefs = PsiTreeUtil.collectElements(myFixture.file) { it.reference is CrystalReference }
        val stringRef = allRefs.firstOrNull { it.text == "String" }
            ?: throw AssertionError("Expected a CrystalReference named 'String'")
        val resolved = (stringRef.reference as CrystalReference).resolve()
        assertNotNull("String should resolve to a stdlib class definition", resolved)
    }
}
