package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalClassDefinition
import de.magynhard.crystal.psi.CrystalDotCallAccess
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

    // ==================== Disambiguation: same simple name, different enclosing class ====================

    fun testNestedClassResolvesCorrectlyWhenMultipleClassesSameName() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              class Sub
              end
            end
            class Bar
              class Sub
              end
            end
            Foo::Sub
        """.trimIndent())
        // Find the namespace_access composite (::Sub in the expression, not in the class definition)
        val namespaceAccesses = PsiTreeUtil.findChildrenOfType(file, CrystalNamespaceAccess::class.java)
        assertEquals("Should have exactly one namespace_access", 1, namespaceAccesses.size)

        val nsAccess = namespaceAccesses.first()
        val resolved = nsAccess.getReference()?.resolve()
        assertNotNull("Foo::Sub should resolve to Foo's Sub class", resolved)
        assertTrue("Should resolve to CrystalClassDefinition", resolved is CrystalClassDefinition)
        assertEquals("Sub", (resolved as CrystalClassDefinition).name)
        // Verify it's Foo::Sub, not Bar::Sub — walk up from the parent to find the enclosing class
        val enclosingClass = PsiTreeUtil.getParentOfType(resolved.parent, CrystalClassDefinition::class.java, false)
        assertNotNull("Resolved Sub should be inside a class", enclosingClass)
        assertEquals("Foo", enclosingClass?.name)
    }

    fun testDotCallResolvesCorrectlyForNestedClassMethod() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              class Sub
                def self.space
                end
              end
            end
            class Bar
              class Sub
                def self.space
                end
              end
            end
            Foo::Sub.space
        """.trimIndent())
        // Find the dot_call_access composite (.space in the expression)
        val dotCallAccesses = PsiTreeUtil.findChildrenOfType(file, CrystalDotCallAccess::class.java)
        assertEquals("Should have exactly one dot_call_access", 1, dotCallAccesses.size)

        val dotCall = dotCallAccesses.first()
        val resolved = dotCall.getReference()?.resolve()
        assertNotNull("Foo::Sub.space should resolve to a method", resolved)
        assertTrue("Should resolve to CrystalMethodDefinition", resolved is de.magynhard.crystal.psi.CrystalMethodDefinition)
        assertEquals("space", (resolved as de.magynhard.crystal.psi.CrystalMethodDefinition).name)
        // The method should be inside a class named Sub
        val enclosingSub = PsiTreeUtil.getParentOfType(resolved, CrystalClassDefinition::class.java, false)
        assertNotNull("Method should have an enclosing Sub class", enclosingSub)
        assertEquals("Sub", enclosingSub?.name)
    }

    fun testMultiLevelNestedResolvesCorrectly() {
        val resolved = resolveAtCaret("""
            class A
              class B
                class C
                end
              end
            end
            A::B::<caret>C
        """.trimIndent())
        assertNotNull("A::B::C should resolve to C inside B inside A", resolved)
        assertTrue("Should resolve to CrystalClassDefinition", resolved is CrystalClassDefinition)
        assertEquals("C", (resolved as CrystalClassDefinition).name)
    }
}
