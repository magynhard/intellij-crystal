package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalPointerofExpression
import de.magynhard.crystal.psi.CrystalVariableReference

class CrystalPointerofTargetTest : BasePlatformTestCase() {

    fun testLibExternalVarTarget() {
        val file = assertParsesCleanly("new(pointerof(LibFFI.ffi_type_void))")
        val pointerof = PsiTreeUtil.findChildOfType(file, CrystalPointerofExpression::class.java)
        assertNotNull("Expected a pointerof expression", pointerof)
        val receiver = PsiTreeUtil.findChildOfType(pointerof, CrystalVariableReference::class.java)
        assertNotNull("Expected a constant receiver", receiver)
        assertEquals("LibFFI", receiver!!.text)
        val access = PsiTreeUtil.findChildOfType(pointerof, CrystalDotCallAccess::class.java)
        assertNotNull("Expected a dot-call access", access)
        assertTrue("Access must carry no arguments", access!!.callArgs == null)
        assertTrue("Access must carry no bare arguments", access.bareArgumentList == null)
    }

    fun testPlainTargetsUnchanged() {
        assertParsesCleanly("pointerof(value)")
        assertParsesCleanly("pointerof(@value)")
        assertParsesCleanly("pointerof(@@loaded)")
        assertParsesCleanly("pointerof(fiber.@context)")
    }

    fun testRejectsOrdinaryCall() {
        assertParsesWithError("pointerof(foo.bar)")
    }

    fun testRejectsLibCallWithArguments() {
        assertParsesWithError("pointerof(LibC.foo(1))")
    }

    // NOTE: a block *after* the closing paren (`pointerof(value) { bar }`)
    // parses without error both before and after this change — pre-existing
    // leniency outside the parens, untouched by the target rule.

    fun testRejectsChainedLibAccess() {
        assertParsesWithError("pointerof(LibC.foo.bar)")
    }

    private fun assertParsesCleanly(code: String): PsiFile {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected no parse errors for: $code, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
        return file
    }

    private fun assertParsesWithError(code: String) {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty(),
        )
    }
}
