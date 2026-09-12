package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidIndexedAssignmentTest : BasePlatformTestCase() {

    fun testRejectsMissingRightHandSide() {
        assertIndexedAssignmentError("a[1] =")
    }

    fun testRejectsMissingChainedTail() {
        assertIndexedAssignmentError("a[1] = b[2] =")
    }

    fun testRejectsMalformedIndexAssignment() {
        assertIndexedAssignmentError("a[i += ] = v")
    }

    private fun assertIndexedAssignmentError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid indexed assignment: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
