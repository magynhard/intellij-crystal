package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidBangTildeOperatorTest : BasePlatformTestCase() {

    fun testRejectsIncompleteAndSplitOperators() {
        listOf(
            "value = !~ other",
            "value = other !~",
            "value = other ! ~= other",
        ).forEach { invalid ->
            val file = myFixture.configureByText(
                "test.cr",
                "$invalid\n\ndef after_invalid\n  true\nend",
            )

            assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty())
        }
    }
}
