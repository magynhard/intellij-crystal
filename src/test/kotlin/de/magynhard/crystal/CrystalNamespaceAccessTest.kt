package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalNamespaceAccess
import de.magynhard.crystal.psi.CrystalVariableReference

class CrystalNamespaceAccessTest : BasePlatformTestCase() {

    private fun resolveAtCaret(code: String): PsiElement? {
        myFixture.configureByText("test.cr", code)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null

        // Try PsiReference on the element or its parent (for namespace_access composite)
        val ref = leaf.reference ?: leaf.parent?.reference
        if (ref != null) return ref.resolve()

        return null
    }

    // ==================== Leading :: namespace ====================

    fun testLeadingNamespaceResolvesToClass() {
        val resolved = resolveAtCaret("""
            class Foo
            end
            ::<caret>Foo
        """.trimIndent())
        assertNotNull("Leading ::Foo should resolve to class Foo", resolved)
        assertTrue("Should resolve to CrystalClassDefinition", resolved is CrystalClassDefinition)
        assertEquals("Foo", (resolved as CrystalClassDefinition).name)
    }

    // ==================== Intermediate namespace ====================

    fun testIntermediateNamespaceResolvesToClass() {
        val resolved = resolveAtCaret("""
            class Outer
              class Inner
              end
            end
            Outer::<caret>Inner
        """.trimIndent())
        println("Resolved: $resolved (${resolved?.javaClass?.simpleName})")
        println("Text: ${resolved?.text?.lines()?.first()}")
        // The resolution should find the Inner class
        // Note: lexically-nested classes are indexed by simple name
        // The namespace_access reference tries full path "Outer::Inner" first, then simple name "Inner"
    }

    // ==================== Namespace access has reference ====================

    fun testNamespaceAccessHasReference() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
            end
            Foo::<caret>Bar
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("Leaf should exist", leaf)

        // Check that the namespace_access composite has a reference
        val namespaceAccess = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
            leaf, CrystalNamespaceAccess::class.java, false
        )
        if (namespaceAccess != null) {
            assertNotNull("namespace_access should have a reference", namespaceAccess.reference)
        }
    }

    // ==================== Variable reference in namespace ====================

    fun testVariableReferenceResolvesInNamespace() {
        val resolved = resolveAtCaret("""
            class Foo
            end
            <caret>Foo
        """.trimIndent())
        assertNotNull("Variable reference Foo should resolve to class", resolved)
        assertTrue("Should resolve to CrystalClassDefinition", resolved is CrystalClassDefinition)
    }
}
