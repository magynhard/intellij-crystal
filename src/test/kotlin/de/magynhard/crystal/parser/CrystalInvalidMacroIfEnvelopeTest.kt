package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidMacroIfEnvelopeTest : BasePlatformTestCase() {

    // NOTE: a missing `{% end %}` outside parentheses is NOT a plugin parse
    // error: every tag is complete, so the shape parses as flat control
    // statements (same model as lone `{% if %}` tags). Inside call
    // parentheses the truncation does fail, covered below.

    fun testRejectsEnvelopeMissingEndTagInCall() {
        val file = myFixture.configureByText(
            "test.cr",
            "f({% if t %} A {% else %} B)"
        )
        assertTrue(
            "Expected parse error for envelope missing its end tag in call",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
