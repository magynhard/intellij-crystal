package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidStringNamedArgumentTest : BasePlatformTestCase() {

    fun testRejectsIncompleteStringNamedArgument() {
        val file = myFixture.configureByText(
            "test.cr",
            "f(\"FOO\""
        )
        assertTrue(
            "Expected parse error for incomplete string named argument",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsStringLabelMissingColon() {
        val file = myFixture.configureByText(
            "test.cr",
            "f(\"a\" 1)"
        )
        assertTrue(
            "Expected parse error for string label missing its colon",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
