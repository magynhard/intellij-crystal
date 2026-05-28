package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.navigation.CrystalInstanceVarFinder
import de.magynhard.crystal.psi.CrystalInstanceVarAccess
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Tests for Go to Definition and Find Usages on instance variables (@name) and class variables (@@name).
 */
class CrystalInstanceVarNavigationTest : BasePlatformTestCase() {

    private fun gotoTargets(code: String): Array<out com.intellij.psi.PsiElement>? {
        myFixture.configureByText("test.cr", code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        val handler = CrystalGotoDeclarationHandler()
        return handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
    }

    fun testInstanceVarGoToPropertyDeclaration() {
        myFixture.configureByText("test.cr", """
            class Person
              @name : String
              def greet
                @na<caret>me
              end
            end
        """.trimIndent())
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("Element at caret should not be null", element)
        // Debug: print element type
        val elType = element!!.node.elementType
        assertTrue("Element should be INSTANCE_VAR token but got: $elType (text='${element.text}')",
            elType == CrystalTypes.INSTANCE_VAR)
        val handler = CrystalGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
        assertNotNull("Should resolve @name to property declaration", targets)
        assertTrue("Should have at least one target", targets!!.isNotEmpty())
    }

    fun testInstanceVarGoToGetterMacro() {
        val targets = gotoTargets("""
            class Person
              getter name : String
              def greet
                @na<caret>me
              end
            end
        """.trimIndent())
        assertNotNull("Should resolve @name to getter declaration", targets)
        assertTrue(targets!!.isNotEmpty())
        val targetText = targets[0].text
        assertTrue("Target should be getter call, got: $targetText",
            targetText.contains("getter"))
    }

    fun testInstanceVarFallbackToAssignment() {
        val targets = gotoTargets("""
            class Person
              def initialize
                @name = "test"
              end
              def greet
                @na<caret>me
              end
            end
        """.trimIndent())
        assertNotNull("Should resolve @name to assignment", targets)
        assertTrue(targets!!.isNotEmpty())
    }

    fun testClassVarGoToDefinition() {
        val targets = gotoTargets("""
            class Counter
              @@count = 0
              def self.increment
                @@cou<caret>nt += 1
              end
            end
        """.trimIndent())
        assertNotNull("Should resolve @@count", targets)
        assertTrue(targets!!.isNotEmpty())
    }

    fun testInstanceVarFindAllUsages() {
        myFixture.configureByText("test.cr", """
            class Person
              @name : String
              def initialize(@name : String)
              end
              def greet
                @name
              end
            end
        """.trimIndent())

        val usages = CrystalInstanceVarFinder.findAllUsages("@name", myFixture.file.findElementAt(myFixture.caretOffset)!!)
        assertTrue("Should find multiple usages of @name", usages.size >= 2)
    }

    fun testInstanceVarNotFoundOutsideClass() {
        val targets = gotoTargets("""
            @na<caret>me = "test"
        """.trimIndent())
        assertNull("Should not resolve @name outside class", targets)
    }

    fun testInstanceVarIsPsiNamedElement() {
        myFixture.configureByText("test.cr", """
            class Person
              def greet
                @na<caret>me
              end
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(leaf)
        val composite = leaf!!.parent
        assertTrue("Parent should be CrystalInstanceVarAccess", composite is CrystalInstanceVarAccess)
        assertTrue("Should be PsiNamedElement", composite is de.magynhard.crystal.psi.CrystalNamedElement)
        assertEquals("@name", (composite as de.magynhard.crystal.psi.CrystalNamedElement).name)
    }

    fun testInstanceVarReferenceResolves() {
        myFixture.configureByText("test.cr", """
            class Person
              @name : String
              def greet
                @na<caret>me
              end
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        val composite = leaf!!.parent as CrystalInstanceVarAccess
        val ref = composite.reference
        assertNotNull("Instance var should have a reference", ref)
        val resolved = ref!!.resolve()
        assertNotNull("Reference should resolve", resolved)
        assertEquals("@name", resolved!!.text)
    }

    fun testFindUsagesProviderAcceptsInstanceVar() {
        myFixture.configureByText("test.cr", """
            class Person
              def greet
                @na<caret>me
              end
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)
        val composite = leaf!!.parent
        val provider = de.magynhard.crystal.navigation.CrystalFindUsagesProvider()
        assertTrue("FindUsagesProvider should accept instance var",
            provider.canFindUsagesFor(composite))
    }

    fun testFindUsagesPlatformIntegration() {
        myFixture.configureByText("test.cr", """
            class Foo
              def initialize
                @na<caret>me = "hello"
              end
              def greet
                @name
              end
            end
        """.trimIndent())
        val usages = myFixture.findUsages(myFixture.elementAtCaret)
        assertEquals("Should find 2 usages of @name (including definition)", 2, usages.size)
    }
}
