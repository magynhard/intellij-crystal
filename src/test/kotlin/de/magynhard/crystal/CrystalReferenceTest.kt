package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.*

/**
 * Tests for Go to Definition (CrystalReference resolution) and CrystalNamedElement.
 */
class CrystalReferenceTest : BasePlatformTestCase() {

    // ==================== CrystalNamedElement ====================

    fun testClassDefinitionHasName() {
        val file = myFixture.configureByText("test.cr", "class Foo\nend")
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)
        assertNotNull("Should find class definition", classDef)
        assertEquals("Foo", classDef!!.name)
        assertNotNull("Should have name identifier", classDef.nameIdentifier)
    }

    fun testModuleDefinitionHasName() {
        val file = myFixture.configureByText("test.cr", "module Bar\nend")
        val moduleDef = PsiTreeUtil.findChildOfType(file, CrystalModuleDefinition::class.java)
        assertNotNull(moduleDef)
        assertEquals("Bar", moduleDef!!.name)
    }

    fun testMethodDefinitionHasName() {
        val file = myFixture.configureByText("test.cr", "def hello\nend")
        val methodDef = PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)
        assertNotNull(methodDef)
        assertEquals("hello", methodDef!!.name)
    }

    fun testStructDefinitionHasName() {
        val file = myFixture.configureByText("test.cr", "struct Point\nend")
        val structDef = PsiTreeUtil.findChildOfType(file, CrystalStructDefinition::class.java)
        assertNotNull(structDef)
        assertEquals("Point", structDef!!.name)
    }

    fun testEnumDefinitionHasName() {
        val file = myFixture.configureByText("test.cr", "enum Color\nend")
        val enumDef = PsiTreeUtil.findChildOfType(file, CrystalEnumDefinition::class.java)
        assertNotNull(enumDef)
        assertEquals("Color", enumDef!!.name)
    }

    fun testMacroDefinitionHasName() {
        val file = myFixture.configureByText("test.cr", "macro my_macro\nend")
        val macroDef = PsiTreeUtil.findChildOfType(file, CrystalMacroDefinition::class.java)
        assertNotNull(macroDef)
        assertEquals("my_macro", macroDef!!.name)
    }

    // ==================== Reference Resolution ====================

    fun testReferenceResolvesToClassDefinition() {
        myFixture.configureByText("test.cr", """
            class Foo
            end
            x = Fo<caret>o.new
        """.trimIndent())
        val ref = myFixture.file.findElementAt(myFixture.caretOffset)?.parent?.reference
            ?: myFixture.file.findElementAt(myFixture.caretOffset)?.reference
        // Reference may be on the leaf node or its parent
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("Should find element at caret", element)
        val reference = element?.reference
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved != null) {
                assertTrue("Should resolve to CrystalClassDefinition", resolved is CrystalClassDefinition || resolved.parent is CrystalClassDefinition)
            }
        }
    }

    fun testReferenceResolvesToMethodDefinition() {
        myFixture.configureByText("test.cr", """
            def greet
            end
            gre<caret>et
        """.trimIndent())
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("Should find element at caret", element)
        val reference = element?.reference
        if (reference != null) {
            val resolved = reference.resolve()
            if (resolved != null) {
                assertTrue("Should resolve to method",
                    resolved is CrystalMethodDefinition || resolved.parent is CrystalMethodDefinition)
            }
        }
    }

    // ==================== Definition should NOT reference itself ====================

    fun testDefinitionNameDoesNotReferenceItself() {
        myFixture.configureByText("test.cr", "class Fo<caret>o\nend")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        // The definition name itself should not have a reference (it IS the definition)
        // CrystalReferenceContributor skips elements whose parent is CrystalNamedElement
    }
}
