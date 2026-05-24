package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.*

/**
 * Tests for Go to Definition (CrystalReference resolution) and CrystalNamedElement.
 */
class CrystalReferenceTest : BasePlatformTestCase() {

    /** Find the first non-empty reference on the element (covers both intrinsic and contributed). */
    private fun findReference(element: PsiElement) =
        element.references.firstOrNull { it is CrystalReference }

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

    fun testVariableReferenceResolvesToMethod() {
        // "greet" without args is parsed as variable_reference
        val file = myFixture.configureByText("test.cr", """
            def greet
            end
            greet
        """.trimIndent())
        val varRefs = PsiTreeUtil.findChildrenOfType(file, CrystalVariableReference::class.java)
        // Find the standalone "greet" usage (not inside method_name)
        val greetRef = varRefs.find { ref ->
            ref.text == "greet" &&
            ref.parent !is CrystalMethodName &&
            ref.parent?.parent !is CrystalMethodDefinition
        }
        assertNotNull("Should find 'greet' variable reference", greetRef)
        val reference = findReference(greetRef!!)
        assertNotNull("variable_reference should have a CrystalReference", reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve to the method definition", resolved)
        assertTrue("Should resolve to CrystalMethodDefinition",
            resolved is CrystalMethodDefinition)
        assertEquals("greet", (resolved as CrystalMethodDefinition).name)
    }

    fun testMethodCallResolvesToMethod() {
        // "greet(x)" with args is parsed as method_call_expression
        val file = myFixture.configureByText("test.cr", """
            def greet(name)
            end
            greet("world")
        """.trimIndent())
        val calls = PsiTreeUtil.findChildrenOfType(file, CrystalMethodCallExpression::class.java)
        val greetCall = calls.find { it.text.startsWith("greet(\"world\")") }
        assertNotNull("Should find method call expression", greetCall)
        val reference = findReference(greetCall!!)
        assertNotNull("method_call_expression should have a CrystalReference", reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve to the method definition", resolved)
        assertTrue("Should resolve to CrystalMethodDefinition",
            resolved is CrystalMethodDefinition)
    }

    fun testTypePathResolvesToClass() {
        // In "Foo.new", Foo is parsed as variable_reference (CONSTANT), not type_path
        val file = myFixture.configureByText("test.cr", """
            class Foo
            end
            x = Foo.new
        """.trimIndent())
        val varRefs = PsiTreeUtil.findChildrenOfType(file, CrystalVariableReference::class.java)
        val fooRef = varRefs.find { it.text == "Foo" }
        assertNotNull("Should find Foo variable reference", fooRef)
        val reference = findReference(fooRef!!)
        assertNotNull("Foo should have a CrystalReference", reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve to class definition", resolved)
        assertTrue("Should resolve to CrystalClassDefinition",
            resolved is CrystalClassDefinition)
    }

    // ==================== No self-reference ====================

    fun testDefinitionNameHasNoReference() {
        val file = myFixture.configureByText("test.cr", "class Foo\nend")
        val classDef = PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)
        assertNotNull(classDef)
        // The class definition itself should not have a CrystalReference
        val ref = findReference(classDef!!)
        assertNull("Definition should not have a self-reference", ref)
    }

    // ==================== Forward reference ====================

    fun testResolvesMethodDefinedAfterUsage() {
        val file = myFixture.configureByText("test.cr", """
            greet
            def greet
            end
        """.trimIndent())
        val varRefs = PsiTreeUtil.findChildrenOfType(file, CrystalVariableReference::class.java)
        val greetRef = varRefs.find { ref ->
            ref.text == "greet" &&
            ref.parent !is CrystalMethodName &&
            ref.parent?.parent !is CrystalMethodDefinition
        }
        assertNotNull(greetRef)
        val reference = findReference(greetRef!!)
        assertNotNull("Should have reference", reference)
        val resolved = reference!!.resolve()
        assertNotNull("Should resolve forward reference", resolved)
        assertTrue(resolved is CrystalMethodDefinition)
    }
}
