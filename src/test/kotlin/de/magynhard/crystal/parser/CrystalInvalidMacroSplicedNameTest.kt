package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidMacroSplicedNameTest : BasePlatformTestCase() {

    fun testRejectsEmptyInterpolationInSplicedFunName() {
        val file = myFixture.configureByText(
            "test.cr",
            "lib LibC\n  fun foo_{{}} : Int32\nend"
        )
        assertTrue(
            "Expected parse error for empty interpolation in spliced fun name",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsUnterminatedInterpolationInSplicedFunName() {
        val file = myFixture.configureByText(
            "test.cr",
            "lib LibC\n  fun {{x} : Int32\nend"
        )
        assertTrue(
            "Expected parse error for unterminated interpolation in spliced fun name",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsEmptyInterpolationInSplicedEnumConstant() {
        val file = myFixture.configureByText(
            "test.cr",
            "enum E\n  {{}} = 1\nend"
        )
        assertTrue(
            "Expected parse error for empty interpolation in spliced enum constant",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
