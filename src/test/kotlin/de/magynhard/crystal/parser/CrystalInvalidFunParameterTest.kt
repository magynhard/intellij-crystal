package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidFunParameterTest : BasePlatformTestCase() {

    fun testRejectsDefaultValueOnUnnamedLibFunParameter() {
        val file = myFixture.configureByText(
            "test.cr",
            "lib LibC\n  fun bad_default(Int32 = 1)\nend"
        )
        assertTrue(
            "Expected parse error for default value on unnamed lib fun parameter",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsUnnamedParameterInDef() {
        val file = myFixture.configureByText(
            "test.cr",
            "def bad_def(Int32, x : String)\nend"
        )
        assertTrue(
            "Expected parse error for unnamed parameter outside lib fun",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
