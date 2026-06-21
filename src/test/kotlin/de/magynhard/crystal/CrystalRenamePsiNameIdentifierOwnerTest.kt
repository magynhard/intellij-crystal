package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.*

/**
 * Tests for the PsiNameIdentifierOwner implementations on Crystal PSI composites.
 *
 * These verify that CrystalVariableReference, CrystalParameter, and CrystalAssignment
 * correctly implement PsiNameIdentifierOwner (getNameIdentifier, getName, setName),
 * and that CrystalReference.resolve() promotes IDENTIFIER leaf results to their
 * parent composite when it implements PsiNameIdentifierOwner.
 */
class CrystalRenamePsiNameIdentifierOwnerTest : BasePlatformTestCase() {

    // ==================== CrystalVariableReference — PsiNameIdentifierOwner ====================

    fun testVariableReferenceImplementsPsiNameIdentifierOwner() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              x = 1
              puts x
            end
        """.trimIndent())
        val varRefs = PsiTreeUtil.findChildrenOfType(file, CrystalVariableReference::class.java)
        val xRef = varRefs.find { it.text == "x" && it.parent !is CrystalAssignment }
        assertNotNull("Should find 'x' variable reference", xRef)
        assertTrue("CrystalVariableReference should implement PsiNameIdentifierOwner",
            xRef is PsiNameIdentifierOwner)
    }

    fun testVariableReferenceGetNameIdentifier() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              x = 1
              puts x
            end
        """.trimIndent())
        val varRefs = PsiTreeUtil.findChildrenOfType(file, CrystalVariableReference::class.java)
        val xRef = varRefs.find { it.text == "x" && it.parent !is CrystalAssignment } as? PsiNameIdentifierOwner
        assertNotNull(xRef)
        val nameIdent = xRef!!.nameIdentifier
        assertNotNull("Should have name identifier", nameIdent)
        assertEquals("x", nameIdent!!.text)
    }

    fun testVariableReferenceGetName() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              name = "world"
              puts name
            end
        """.trimIndent())
        val varRefs = PsiTreeUtil.findChildrenOfType(file, CrystalVariableReference::class.java)
        val nameRef = varRefs.find { it.text == "name" && it.parent !is CrystalAssignment } as? PsiNameIdentifierOwner
        assertNotNull(nameRef)
        assertEquals("name", nameRef!!.name)
    }

    fun testVariableReferenceConstantGetNameIdentifier() {
        val file = myFixture.configureByText("test.cr", "x = Foo.new")
        val varRefs = PsiTreeUtil.findChildrenOfType(file, CrystalVariableReference::class.java)
        val fooRef = varRefs.find { it.text == "Foo" } as? PsiNameIdentifierOwner
        assertNotNull(fooRef)
        val nameIdent = fooRef!!.nameIdentifier
        assertNotNull(nameIdent)
        assertEquals("Foo", nameIdent!!.text)
    }

    // ==================== CrystalParameter — PsiNameIdentifierOwner ====================

    fun testParameterImplementsPsiNameIdentifierOwner() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name : String)
              puts name
            end
        """.trimIndent())
        val params = PsiTreeUtil.findChildrenOfType(file, CrystalParameter::class.java)
        val nameParam = params.find { it.text.contains("name") }
        assertNotNull("Should find 'name' parameter", nameParam)
        assertTrue("CrystalParameter should implement PsiNameIdentifierOwner",
            nameParam is PsiNameIdentifierOwner)
    }

    fun testParameterGetNameIdentifier() {
        val file = myFixture.configureByText("test.cr", """
            def greet(name : String)
              puts name
            end
        """.trimIndent())
        val params = PsiTreeUtil.findChildrenOfType(file, CrystalParameter::class.java)
        val nameParam = params.find { it.text.contains("name") } as? PsiNameIdentifierOwner
        assertNotNull(nameParam)
        val nameIdent = nameParam!!.nameIdentifier
        assertNotNull("Should have name identifier", nameIdent)
        assertEquals("name", nameIdent!!.text)
    }

    fun testParameterGetName() {
        val file = myFixture.configureByText("test.cr", """
            def greet(loud : Bool)
              puts loud
            end
        """.trimIndent())
        val params = PsiTreeUtil.findChildrenOfType(file, CrystalParameter::class.java)
        val loudParam = params.find { it.text.contains("loud") } as? PsiNameIdentifierOwner
        assertNotNull(loudParam)
        assertEquals("loud", loudParam!!.name)
    }

    fun testParameterWithExternalNameUsesInternalName() {
        // Crystal convention: `external internal : Type` — last IDENTIFIER is the name
        val file = myFixture.configureByText("test.cr", """
            def greet(user_name name : String)
              puts name
            end
        """.trimIndent())
        val params = PsiTreeUtil.findChildrenOfType(file, CrystalParameter::class.java)
        val nameParam = params.find { it.text.contains("name") } as? PsiNameIdentifierOwner
        assertNotNull(nameParam)
        // Should return the internal name "name", not the external name "user_name"
        assertEquals("name", nameParam!!.name)
    }

    // ==================== CrystalAssignment — PsiNameIdentifierOwner ====================

    fun testAssignmentImplementsPsiNameIdentifierOwner() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              x = 1
              puts x
            end
        """.trimIndent())
        val assignments = PsiTreeUtil.findChildrenOfType(file, CrystalAssignment::class.java)
        val xAssign = assignments.find { it.text.startsWith("x") }
        assertNotNull("Should find 'x' assignment", xAssign)
        assertTrue("CrystalAssignment should implement PsiNameIdentifierOwner",
            xAssign is PsiNameIdentifierOwner)
    }

    fun testAssignmentGetNameIdentifier() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              x = 1
              puts x
            end
        """.trimIndent())
        val assignments = PsiTreeUtil.findChildrenOfType(file, CrystalAssignment::class.java)
        val xAssign = assignments.find { it.text.startsWith("x") } as? PsiNameIdentifierOwner
        assertNotNull(xAssign)
        val nameIdent = xAssign!!.nameIdentifier
        assertNotNull("Should have name identifier", nameIdent)
        assertEquals("x", nameIdent!!.text)
    }

    fun testAssignmentGetName() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              result = "hello"
              puts result
            end
        """.trimIndent())
        val assignments = PsiTreeUtil.findChildrenOfType(file, CrystalAssignment::class.java)
        val resultAssign = assignments.find { it.text.startsWith("result") } as? PsiNameIdentifierOwner
        assertNotNull(resultAssign)
        assertEquals("result", resultAssign!!.name)
    }

    // ==================== CrystalReference.resolve() promotion ====================

    fun testResolveParameterReturnsComposite() {
        val file = myFixture.configureByText("test.cr", """
            def greet(loud : Bool)
              puts <caret>loud
            end
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef = element.parent as? CrystalVariableReference
            ?: error("Expected CrystalVariableReference, got ${element.parent?.javaClass?.simpleName}")
        val ref = varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
            ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve", resolved)
        // Should resolve to CrystalParameter (composite), not IDENTIFIER leaf
        assertTrue("Should resolve to CrystalParameter (PsiNameIdentifierOwner)",
            resolved is CrystalParameter)
        assertTrue("Resolved element should be PsiNameIdentifierOwner",
            resolved is PsiNameIdentifierOwner)
    }

    fun testResolveMethodReturnsMethodDefinition() {
        val file = myFixture.configureByText("test.cr", """
            def greet
            end
            <caret>greet
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef = element.parent as? CrystalVariableReference
            ?: error("Expected CrystalVariableReference")
        val ref = varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
            ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve", resolved)
        assertTrue("Should resolve to CrystalMethodDefinition",
            resolved is CrystalMethodDefinition)
    }

    fun testResolveClassConstantReturnsClassDefinition() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
            end
            x = <caret>Foo.new
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef = element.parent as? CrystalVariableReference
            ?: error("Expected CrystalVariableReference")
        val ref = varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
            ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve", resolved)
        assertTrue("Should resolve to CrystalClassDefinition",
            resolved is CrystalClassDefinition)
    }

    // ==================== Rename processor availability ====================

    fun testRenameUsesDefaultProcessorForAllElements() {
        // Without a custom renamePsiElementProcessor, all Crystal elements
        // use the DEFAULT processor. This is the current design: leaf tokens
        // are handled by TokenInplaceRenameHandler (token-based), and
        // composites with PsiNameIdentifierOwner are handled by
        // MemberInplaceRenameHandler via the DEFAULT processor.
        val file = myFixture.configureByText("test.cr", """
            def greet
              <caret>x = 1
            end
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val processor = com.intellij.refactoring.rename.RenamePsiElementProcessor.forElement(element)
        assertTrue("Should use DEFAULT processor",
            processor === com.intellij.refactoring.rename.RenamePsiElementProcessor.DEFAULT)
    }
}
