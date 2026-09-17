package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalBlock
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalProcLiteral

class CrystalDoProcLiteralTest : BasePlatformTestCase() {

    fun testDoBodyBelongsToProcArgument() {
        val file = myFixture.configureByText(
            "test.cr",
            "LibGC.set_start_callback -> do\n  GC.lock_write\nend",
        )
        assertNoErrors(file)
        val call = PsiTreeUtil.findChildOfType(file, CrystalDotCallAccess::class.java)
        assertNotNull("Expected a receiver call", call)
        assertNotNull(
            "Expected the do body inside a proc literal argument",
            PsiTreeUtil.findChildOfType(call, CrystalProcLiteral::class.java),
        )
        assertTrue(
            "The do must not attach as an ordinary block to the outer call",
            PsiTreeUtil.findChildrenOfType(call, CrystalBlock::class.java).isEmpty(),
        )
    }

    fun testNestedBlockInsideDoProc() {
        val file = myFixture.configureByText(
            "test.cr",
            "run -> do\n  list.each do |item|\n    item\n  end\nend\ndef after_nested\nend",
        )
        assertNoErrors(file)
        assertEquals(
            "Only the nested each block may appear as a block; the proc do body belongs to the proc literal",
            1,
            PsiTreeUtil.findChildrenOfType(file, CrystalBlock::class.java).size,
        )
        assertEquals(
            "The trailing def must survive the nested ends",
            1,
            PsiTreeUtil.findChildrenOfType(file, de.magynhard.crystal.psi.CrystalMethodDefinition::class.java).size,
        )
    }

    fun testRejectsMissingEnd() {
        assertDoProcError("run -> do\n  work\n")
    }

    fun testRejectsBlockParameters() {
        assertDoProcError("run -> do |item|\n  item\nend")
    }

    private fun assertNoErrors(file: PsiFile) {
        assertTrue(
            "Expected no parse errors, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
    }

    private fun assertDoProcError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid do proc literal: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty(),
        )
    }
}
