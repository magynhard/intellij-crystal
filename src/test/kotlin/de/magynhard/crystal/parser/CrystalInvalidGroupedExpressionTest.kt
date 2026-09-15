package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidGroupedExpressionTest : BasePlatformTestCase() {

    fun testRejectsEmptySemicolonGroup() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = (;)\nputs x"
        )
        assertTrue(
            "Expected parse error for a parenthesized group without expressions",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
