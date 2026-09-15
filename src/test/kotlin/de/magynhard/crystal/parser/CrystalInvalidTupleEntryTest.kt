package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidTupleEntryTest : BasePlatformTestCase() {

    fun testRejectsMissingAssignmentValueInTuple() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = {a = }\nputs x"
        )
        assertTrue(
            "Expected parse error for a missing assignment value in a tuple entry",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
