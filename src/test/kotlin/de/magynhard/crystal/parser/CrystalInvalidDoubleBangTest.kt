package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Negative shapes around prefix `!` handling: the former not_expression level
 * is gone, so `!` binds only a prefix chain and an operand-less `!` fails.
 * (Defining `!` as a method name also stays rejected by the compiler,
 * parser_spec.cr:2502-2515 — that family belongs to the separate `.!`
 * pseudo-method cluster.)
 */
class CrystalInvalidDoubleBangTest : BasePlatformTestCase() {

    fun testRejectsOperandlessBang() {
        assertBangError("value = !")
    }

    fun testRejectsBangBrokenComparison() {
        assertBangError("value = !!a !=")
    }

    private fun assertBangError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid bang expression: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
