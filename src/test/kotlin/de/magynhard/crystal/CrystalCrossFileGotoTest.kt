package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.*

/**
 * Tests for cross-file Go to Definition — definitions in one file, usages in another.
 *
 * Resolution path matches what the platform does for Ctrl+B:
 * 1. PsiReference on the element or its parent (CrystalDotCallAccess for DOT-calls,
 *    CrystalVariableReference for direct calls).
 * 2. GotoDeclarationHandler fallback for the `.new` constructor special case
 *    (resolves to self.new > record > initialize).
 */
class CrystalCrossFileGotoTest : BasePlatformTestCase() {

    /**
     * Resolves the DOT-call at the caret using the same path as the IDE:
     * leaf element → parent (CrystalDotCallAccess) → reference → resolve.
     */
    private fun dotCallTargets(): Array<out PsiElement>? {
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        // 1. Reference resolution via CrystalDotCallAccess mixin.
        val ref = element.reference ?: element.parent?.reference
        val resolved = ref?.resolve()
        if (resolved != null) return arrayOf(resolved)
        // 2. GotoDeclarationHandler fallback (e.g. ".new" constructor).
        val handler = CrystalGotoDeclarationHandler()
        return handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
    }

    fun testDotCallResolvesToSelfMethodInOtherFile() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def self.tanzen
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Apfel.tan<caret>zen")

        val targets = dotCallTargets()

        assertNotNull("Should resolve Apfel.tanzen across files", targets)
        assertTrue("Should have at least one target", targets!!.isNotEmpty())
        assertTrue("Target should be a method definition", targets[0] is CrystalMethodDefinition)
        assertEquals("tanzen", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testDotCallResolvesToInstanceMethodInOtherFile() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def essen
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            a = Apfel.new
            a.es<caret>sen
        """.trimIndent())

        val targets = dotCallTargets()

        assertNotNull("Should resolve a.essen across files", targets)
        assertTrue(targets!!.isNotEmpty())
        assertEquals("essen", (targets[0] as CrystalMethodDefinition).name)
    }

    // ==================== Direct call cross-file (CrystalReference via mixin) ====================

    fun testDirectCallResolvesToMethodInOtherFile() {
        myFixture.addFileToProject("helpers.cr", """
            def greet
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "greet")

        val varRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalVariableReference::class.java)
        val greetRef = varRefs.find { it.text == "greet" }
        assertNotNull("Should find greet variable reference", greetRef)

        val reference = greetRef!!.reference
        assertNotNull("Should have a reference", reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve greet across files", resolved)
        assertTrue("Should be a method definition", resolved is CrystalMethodDefinition)
    }

    fun testClassReferenceResolvesAcrossFiles() {
        myFixture.addFileToProject("models.cr", """
            class Apfel
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "x = Apfel.new")

        val varRefs = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalVariableReference::class.java)
        val apfelRef = varRefs.find { it.text == "Apfel" }
        assertNotNull("Should find Apfel variable reference", apfelRef)

        val reference = apfelRef!!.reference
        assertNotNull(reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve Apfel class across files", resolved)
        assertTrue("Should be a class definition", resolved is CrystalClassDefinition)
    }

    // ==================== Module/Struct cross-file ====================

    fun testModuleReferenceResolvesAcrossFiles() {
        myFixture.addFileToProject("utils.cr", """
            module Utils
              def self.helper
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Utils.hel<caret>per")

        val targets = dotCallTargets()

        assertNotNull("Should resolve Utils.helper across files", targets)
        assertTrue(targets!!.isNotEmpty())
        assertEquals("helper", (targets[0] as CrystalMethodDefinition).name)
    }
}
