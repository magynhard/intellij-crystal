package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils

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
     * - If no reference resolves, ask the GotoDeclarationHandler, which returns all
     *   polyvariant targets or an authoritative empty result for DOT references.
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
        //    PsiReference resolves. Handles the combined explicit self.new and
        //    initializer-backed .new constructor targets.
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
    fun testTopLevelMethodViaDotCallOnUnknownReceiverReturnsNoTargets() {
        val targets = gotoTargets("""
            def greet
            end
            x = 1
            x.gre<caret>et
        """.trimIndent())
        // `greet` exists as a top-level def but is NOT a method of `Int` (x's type).
        // No false-positive name-only matching; the exact reference returns no targets.
        assertNotNull("The exact DOT reference must remain authoritative", targets)
        assertEquals("Should not resolve via name-only when receiver type is unknown/unrelated", 0, targets!!.size)
    }

    fun testNonMethodIdentifierAfterDotReturnsNoTargets() {
        val targets = gotoTargets("""
            x = 1
            x.nonexist<caret>ent
        """.trimIndent())
        // No method named "nonexistent"; the exact reference returns no targets.
        assertNotNull("The exact DOT reference must remain authoritative", targets)
        assertEquals("Should return no targets for unknown method", 0, targets!!.size)
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

    fun testNewGoesToSelfNewAndInitializeOverloads() {
        val targets = gotoTargets("""
            class Senf
              def self.new
              end
              def initialize(value : Int32)
              end
            end
            Senf.n<caret>ew
        """.trimIndent())
        assertNotNull("Should resolve Senf.new to def self.new and def initialize", targets)
        assertEquals(listOf("new", "initialize"), targets!!.map { (it as CrystalMethodDefinition).name })
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

    fun testHashStyleDefaultValueNewSurvivesNilableProcParameter() {
        val targets = gotoTargets("""
            class Dictionary(K, V)
              def initialize(block : (Dictionary(K, V), K -> V)? = nil, *, initial_capacity = nil)
              end

              def self.new(default_value : V, initial_capacity = nil)
              end
            end

            Dictionary(String, Int32).n<caret>ew(0)
        """.trimIndent())

        assertNotNull("Should resolve the Hash-style default-value constructor", targets)
        assertTrue(
            "Expected self.new(default_value : V, ...), got ${targets!!.map { it.text.take(80) }}",
            targets.any {
                it is CrystalMethodDefinition &&
                    it.name == "new" &&
                    it.parameterList?.text?.contains("default_value : V") == true
            }
        )
    }

    fun testNewOnClassWithMacroGeneratedMethodsStillResolves() {
        // A textually written def self.new is real even when the same class also
        // generates methods through {% for %} loops with macro-interpolated names
        // (stdlib http/client.cr pattern). Macro uncertainty must not suppress
        // explicitly defined names.
        val targets = gotoTargets("""
            class Client
              {% for method in %w(get post) %}
                def {{method.id}}(path)
                  nil
                end
              {% end %}

              def self.new(url : String)
                instance
              end

              def initialize
              end
            end

            Client.n<caret>ew("http://localhost")
        """.trimIndent())
        assertNotNull("Should resolve Client.new despite macro-generated sibling names", targets)
        assertTrue(targets!!.isNotEmpty())
        assertTrue(targets[0] is CrystalMethodDefinition)
        assertEquals("new", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testInitializeOnClassWithMacroGeneratedMethodsStillResolves() {
        val targets = gotoTargets("""
            class Client
              {% for method in %w(get post) %}
                def {{method.id}}(path)
                  nil
                end
              {% end %}

              def initialize
              end
            end

            Client.n<caret>ew
        """.trimIndent())
        assertNotNull("Should resolve to explicit initialize behind macro-generated siblings", targets)
        assertTrue(targets!!.isNotEmpty())
        assertEquals("initialize", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testUndefinedNameOnMacroGeneratingClassStaysSuppressed() {
        // The name is NOT explicitly defined anywhere and the class generates methods
        // through macros — the member set is genuinely uncertain here, so the exact
        // reference must stay authoritative-empty instead of guessing.
        val targets = gotoTargets("""
            class Client
              {% for method in %w(get post) %}
                def {{method.id}}(path)
                  nil
                end
              {% end %}
            end

            Client.myste<caret>rious_method
        """.trimIndent())
        assertNotNull("Exact reference stays authoritative", targets)
        assertEquals(0, targets!!.size)
    }

    fun testNewOnUnknownClassReturnsNoTargets() {
        val targets = gotoTargets("""
            class Senf
              def initialize
              end
            end
            Unbekannt.n<caret>ew
        """.trimIndent())
        // No class "Unbekannt"; do not return every "new" method in the project.
        assertNotNull("The exact DOT reference must remain authoritative", targets)
        assertEquals("Should return no targets for unknown class .new", 0, targets!!.size)
    }

    fun testEmptyExactConstructorResultDoesNotFallBackToSameSimpleNameTypes() {
        myFixture.configureByText("test.cr", """
            module A
              class Service
                def self.new(value : Int32)
                end
              end
            end
            module B
              class Service
                def self.new(value : String)
                end
              end
            end
            Service.n<caret>ew(1)
        """.trimIndent())
        val source = myFixture.file.findElementAt(myFixture.caretOffset)
        val targets = CrystalGotoDeclarationHandler().getGotoDeclarationTargets(
            source,
            myFixture.caretOffset,
            myFixture.editor
        )

        assertNotNull("The exact polyvariant reference must be authoritative", targets)
        assertEquals("Ambiguous exact identity must not fall back to A::Service or B::Service", 0, targets!!.size)
    }

    fun testConstructorFallbackWithoutDotReferencePreservesExactReceiverIdentity() {
        val declarations = """
            module A
              class Service
                def initialize(value : Int32)
                end
              end
            end
            module B
              class Service
                def initialize(value : String)
                end
              end
              {% if flag %}
                class Uncertain
                  def initialize
                  end
                end
              {% end %}
              CALL
            end
        """.trimIndent()

        fun fallbackTargets(call: String): List<CrystalMethodDefinition> {
            myFixture.configureByText("test.cr", declarations.replace("CALL", call))
            val source = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset))
            assertNull(
                "The recovery call '$call' must exercise the handler fallback, not CrystalDotCallAccess",
                PsiTreeUtil.getParentOfType(source, CrystalDotCallAccess::class.java, false)
            )
            return CrystalGotoDeclarationHandler().getGotoDeclarationTargets(
                source,
                myFixture.caretOffset,
                myFixture.editor
            ).orEmpty().filterIsInstance<CrystalMethodDefinition>()
        }

        listOf("alias Probe = A::Service.n<caret>ew", "alias Probe = ::A::Service.n<caret>ew").forEach { call ->
            val targets = fallbackTargets(call)
            assertEquals(1, targets.size)
            assertEquals(
                "A::Service",
                CrystalPsiUtils.getEnclosingType(targets.single())?.let(CrystalPsiUtils::buildQualifiedName)
            )
        }

        val simpleTargets = fallbackTargets("alias Probe = Service.n<caret>ew")
        assertEquals(1, simpleTargets.size)
        assertEquals(
            "B::Service",
            CrystalPsiUtils.getEnclosingType(simpleTargets.single())?.let(CrystalPsiUtils::buildQualifiedName)
        )

        assertEmpty(fallbackTargets("alias Probe = Uncertain.n<caret>ew"))
        assertEmpty(fallbackTargets("alias Probe = A::::Service.n<caret>ew"))
    }

    fun testConstructorFallbackWithoutDotReferenceSuppressesUnavailableSimpleName() {
        myFixture.configureByText(
            "test.cr",
            """
                module A
                  class Service
                    def initialize
                    end
                  end
                end
                module B
                  class Service
                    def initialize
                    end
                  end
                end
                alias Probe = Service.n<caret>ew
            """.trimIndent()
        )
        val source = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset))
        assertNull(PsiTreeUtil.getParentOfType(source, CrystalDotCallAccess::class.java, false))

        val resolution = CrystalTypeSetResolver.session(source).resolveConstructor("Service", source)
        assertTrue(resolution is CrystalConstructorResolution.Unavailable)
        assertNull(
            CrystalGotoDeclarationHandler().getGotoDeclarationTargets(
                source,
                myFixture.caretOffset,
                myFixture.editor
            )
        )
    }

    fun testConstructorFallbackWithoutDotReferenceSuppressesAmbiguousExactIdentity() {
        myFixture.addFileToProject(
            "one/service.cr",
            """
                class Service
                  def initialize(value : Int32)
                  end
                end
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "two/service.cr",
            """
                class Service
                  def initialize(value : Int32)
                  end
                end
            """.trimIndent()
        )

        myFixture.configureByText(
            "test.cr",
            "require \"./one/service\"\nrequire \"./two/service\"\nalias Probe = Service.n<caret>ew"
        )
        val source = requireNotNull(myFixture.file.findElementAt(myFixture.caretOffset))
        assertNull(
            "Recovery syntax must bypass the normal polyvariant DOT reference",
            PsiTreeUtil.getParentOfType(source, CrystalDotCallAccess::class.java, false)
        )

        val resolution = CrystalTypeSetResolver.session(source).resolveConstructor("Service", source)
        assertTrue(
            "Duplicate exact Service constructor signatures in separate files must be ambiguous",
            resolution is CrystalConstructorResolution.Incomplete
        )
        assertEquals("Service", resolution.identity.qualifiedName)
        assertNull(
            CrystalGotoDeclarationHandler().getGotoDeclarationTargets(
                source,
                myFixture.caretOffset,
                myFixture.editor
            )
        )
    }
}
