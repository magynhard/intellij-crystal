package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalEnumDefinition
import de.magynhard.crystal.psi.CrystalMethodDefinition

class CrystalEnumBodyTest : BasePlatformTestCase() {

    fun testRejectsClassVarTypeDeclaration() {
        assertEnumBodyError("enum Kind\n  @@cache : Int32\nend")
    }

    fun testRejectsClassVarOpAssignment() {
        assertEnumBodyError("enum Kind\n  @@cache ||= 1\nend")
    }

    fun testRejectsLocalAssignment() {
        assertEnumBodyError("enum Kind\n  cache = 1\nend")
    }

    fun testRejectsBareMethodCall() {
        assertEnumBodyError("enum Kind\n  refresh\nend")
    }

    fun testRejectsPrivateConstant() {
        assertEnumBodyError("enum Kind\n  private CONST = 1\nend")
    }

    fun testClassVarOpAssignmentInsideEnumMethodParses() {
        val file = myFixture.configureByText(
            "test.cr",
            "enum Kind\n  ACTIVE\n  def label\n    @@cache ||= 1\n  end\nend",
        )
        assertTrue(
            "Expected no parse errors, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
    }

    fun testMethodsAfterClassVarAssignmentBelongToEnum() {
        val file = myFixture.configureByText(
            "test.cr",
            "enum Attribute : UInt64\n" +
                "  Alignment\n" +
                "  @@kind_ids = nil.as(Hash(Attribute, UInt32)?)\n" +
                "  protected def self.kind_ids\n" +
                "    @@kind_ids\n" +
                "  end\n" +
                "  ZExt\n" +
                "end",
        )
        assertTrue(
            "Expected no parse errors, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
        val enumDef = PsiTreeUtil.findChildOfType(file, CrystalEnumDefinition::class.java)
        assertNotNull("Expected an enum definition", enumDef)
        assertEquals("Attribute", enumDef!!.name)
        val methods = PsiTreeUtil.findChildrenOfType(enumDef, CrystalMethodDefinition::class.java)
        assertEquals(listOf("kind_ids"), methods.map { it.name })
    }

    private fun assertEnumBodyError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid enum body: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty(),
        )
    }
}
