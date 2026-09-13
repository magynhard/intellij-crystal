package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidKeywordParameterTest : BasePlatformTestCase() {

    fun testRejectsReservedKeywordParameterName() {
        for (kw in listOf("end", "def", "if", "select", "alias")) {
            val file = myFixture.configureByText(
                "test_$kw.cr",
                "def f($kw)\nend"
            )
            assertTrue(
                "Keyword `$kw` must stay rejected as a lone parameter name",
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
            )
        }
    }

    fun testRejectsReservedKeywordParameterWithDefault() {
        val file = myFixture.configureByText(
            "test.cr",
            "def f(select = nil)\nend"
        )
        assertTrue(
            "A reserved keyword must stay rejected as a parameter name even with a default",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testExternalKeywordLabelNeedsInternalName() {
        val file = myFixture.configureByText(
            "test.cr",
            "def pos(end end_pos = false)\n  end_pos\nend"
        )
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue(
            "The external-label form must still parse without errors (found ${errors.size})",
            errors.isEmpty()
        )
    }

    fun testKeywordParameterResolvesInsideBody() {
        myFixture.configureByText(
            "test.cr",
            "def f(of, union)\n  [of, union]\nend"
        )
        myFixture.checkHighlighting()
    }
}
