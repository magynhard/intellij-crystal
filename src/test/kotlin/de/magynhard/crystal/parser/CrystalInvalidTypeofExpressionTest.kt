package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidTypeofExpressionTest : BasePlatformTestCase() {

    fun testRejectsMissingTypeofArgument() = assertInvalid("typeof()")

    fun testRejectsLeadingComma() = assertInvalid("typeof(, value)")

    fun testRejectsDoubleComma() = assertInvalid("typeof(value,, other)")

    fun testRejectsNewlineBeforeComma() = assertInvalid("typeof(value\n, other)")

    private fun assertInvalid(source: String) {
        val file = myFixture.configureByText("test.cr", source)
        assertTrue(
            "Expected parse error for `$source`",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
