package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidLibMacroTest : BasePlatformTestCase() {

    fun testRejectsUnterminatedMacroControlInLib() {
        val file = myFixture.configureByText(
            "test.cr",
            "lib LibC\n  {% if flag?(:win32)\nend"
        )
        assertTrue(
            "Expected parse error for unterminated macro control in lib",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsEmptyMacroInterpolationInLib() {
        val file = myFixture.configureByText(
            "test.cr",
            "lib LibC\n  {{}}\nend"
        )
        assertTrue(
            "Expected parse error for empty macro interpolation in lib",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
