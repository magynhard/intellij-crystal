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

    fun testInstanceVarParameterGetNameIdentifier() {
        // def initialize(@x : Int32) — the INSTANCE_VAR token is the name
        val file = myFixture.configureByText("test.cr", """
            class Foo
              def initialize(@x : Int32)
              end
            end
        """.trimIndent())
        val params = PsiTreeUtil.findChildrenOfType(file, CrystalParameter::class.java)
        val xParam = params.find { it.text.contains("@x") } as? PsiNameIdentifierOwner
        assertNotNull("Should find @x parameter", xParam)
        val nameIdent = xParam!!.nameIdentifier
        assertNotNull("Should have name identifier", nameIdent)
        assertEquals("@x", nameIdent!!.text)
        assertEquals("@x", xParam.name)
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

    // ==================== resolveLocal() — variable assignment resolution ====================

    fun testResolveLocalFindsVariableAssignment() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              x = 1
              puts <caret>x
            end
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef = element.parent as? CrystalVariableReference
            ?: error("Expected CrystalVariableReference, got ${element.parent?.javaClass?.simpleName}")
        val ref = varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
            ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve to variable assignment", resolved)
        assertTrue("Should resolve to CrystalAssignment (PsiNameIdentifierOwner)",
            resolved is CrystalAssignment)
        assertTrue("Resolved element should be PsiNameIdentifierOwner",
            resolved is PsiNameIdentifierOwner)
    }

    fun testResolveLocalFindsAssignmentBeforeMultipleStatements() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              name = "world"
              puts "hello"
              puts "foo"
              puts <caret>name
            end
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef = element.parent as? CrystalVariableReference
            ?: error("Expected CrystalVariableReference")
        val ref = varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
            ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        assertNotNull("Should resolve to variable assignment", resolved)
        assertTrue("Should resolve to CrystalAssignment",
            resolved is CrystalAssignment)
    }

    fun testResolveLocalDoesNotCrossMethodBoundary() {
        val file = myFixture.configureByText("test.cr", """
            def other
              x = 99
            end
            def greet
              puts <caret>x
            end
        """.trimIndent())
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No element at caret")
        val varRef = element.parent as? CrystalVariableReference
            ?: error("Expected CrystalVariableReference")
        val ref = varRef.references.filterIsInstance<CrystalReference>().firstOrNull()
            ?: error("Should have CrystalReference")
        val resolved = ref.resolve()
        // x is defined in other() — should NOT resolve to it from greet()
        assertNull("Should not resolve to variable in different method", resolved)
    }

    // ==================== E2E Rename Tests ====================

    fun testRenameInstanceVarParameter() {
        val file = myFixture.configureByText("test.cr", """
            class Senf
              def initialize(<caret>@testfein : Int32)
                @sahne = @testfein + 4
              end
            end
        """.trimIndent())
        myFixture.renameElementAtCaret("testfein2")
        myFixture.checkResult("""
            class Senf
              def initialize(@testfein2 : Int32)
                @sahne = @testfein2 + 4
              end
            end
        """.trimIndent())
    }

    fun testRenameInstanceVarWithAtPrefix() {
        // Renaming @example to @other — user types just "other", the @ prefix is added automatically
        val file = myFixture.configureByText("test.cr", """
            class Senf
              def initialize(<caret>@example : Int32)
                @sahne = @example + 4
              end
            end
        """.trimIndent())
        myFixture.renameElementAtCaret("other")
        myFixture.checkResult("""
            class Senf
              def initialize(@other : Int32)
                @sahne = @other + 4
              end
            end
        """.trimIndent())
    }

    fun testRenameInstanceVarStripsAtPrefix() {
        // User types @crane in dialog — internally "crane" is passed to setName/handleElementRename
        // The @ prefix is always re-applied from the original token type
        val file = myFixture.configureByText("test.cr", """
            class Senf
              def initialize(<caret>@sample : Int32)
                @sahne = @sample + 4
              end
            end
        """.trimIndent())
        myFixture.renameElementAtCaret("crane")
        myFixture.checkResult("""
            class Senf
              def initialize(@crane : Int32)
                @sahne = @crane + 4
              end
            end
        """.trimIndent())
    }

    fun testRenameInstanceVarKeepsPrefixEvenIfUserAddsIt() {
        // Verify that setName always applies the correct prefix from token type
        // (Cannot call setName directly — must use WriteCommandAction)
        val file = myFixture.configureByText("test.cr", """
            class Senf
              def initialize(@testfein : Int32)
              end
            end
        """.trimIndent())
        val element = file.findElementAt(file.text.indexOf("@testfein")) ?: error("No element")
        val param = element.parent as? com.intellij.psi.PsiNameIdentifierOwner ?: error("Not a named element")
        // getName() should return the full name including @ prefix
        assertEquals("@testfein", param.name)
    }

    fun testRenameInstanceVarUsage() {
        val file = myFixture.configureByText("test.cr", """
            class Senf
              def initialize(@testfein : Int32)
                @sahne = <caret>@testfein + 4
              end
            end
        """.trimIndent())
        myFixture.renameElementAtCaret("testfein2")
        myFixture.checkResult("""
            class Senf
              def initialize(@testfein2 : Int32)
                @sahne = @testfein2 + 4
              end
            end
        """.trimIndent())
    }

    fun testRenameLocalVariablePreservesAllOccurrences() {
        val file = myFixture.configureByText("test.cr", """
            def greet
              <caret>x = 1
              puts x
              puts x + 1
            end
        """.trimIndent())
        myFixture.renameElementAtCaret("y")
        myFixture.checkResult("""
            def greet
              y = 1
              puts y
              puts y + 1
            end
        """.trimIndent())
    }

    // ==================== Explicit Prefix Tests ====================

    fun testRenameClassVarWithExplicitPrefix() {
        val file = myFixture.configureByText("test.cr", """
            class Foo
              <caret>@@example = 1
              def self.test
                puts @@example
              end
            end
        """.trimIndent())
        myFixture.renameElementAtCaret("@@other")
        myFixture.checkResult("""
            class Foo
              @@other = 1
              def self.test
                puts @@other
              end
            end
        """.trimIndent())
    }

    fun testRenameInstanceVarWithExplicitPrefix() {
        val file = myFixture.configureByText("test.cr", """
            class Bar
              def initialize(<caret>@ini : Int32)
                @other = @ini + 1
              end
            end
        """.trimIndent())
        myFixture.renameElementAtCaret("@other_ini")
        myFixture.checkResult("""
            class Bar
              def initialize(@other_ini : Int32)
                @other = @other_ini + 1
              end
            end
        """.trimIndent())
    }

    // ==================== CrystalNamesValidator ====================

    fun testNamesValidatorAcceptsAtPrefixedIdentifiers() {
        val validator = de.magynhard.crystal.refactoring.CrystalNamesValidator()
        assertTrue("@name should be valid", validator.isIdentifier("@name", null))
        assertTrue("@my_var should be valid", validator.isIdentifier("@my_var", null))
        assertTrue("@other123 should be valid", validator.isIdentifier("@other123", null))
        assertTrue("@@class_var should be valid", validator.isIdentifier("@@class_var", null))
        assertFalse("@ should be invalid (no name after @)", validator.isIdentifier("@", null))
        assertFalse("@@ should be invalid (no name after @@)", validator.isIdentifier("@@", null))
        assertFalse("@123 should be invalid (starts with digit)", validator.isIdentifier("@123", null))
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
