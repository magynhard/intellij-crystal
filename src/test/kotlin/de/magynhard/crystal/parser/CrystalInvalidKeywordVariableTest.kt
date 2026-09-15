package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidKeywordVariableTest : BasePlatformTestCase() {

    fun testRejectsReservedKeywordAssignment() {
        val file = myFixture.configureByText(
            "test.cr",
            "end = 1\nputs end"
        )
        assertTrue(
            "Reserved statements should never rebind as variables",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsReservedKeywordCallArgument() {
        val file = myFixture.configureByText(
            "test.cr",
            "puts end, if"
        )
        assertTrue(
            "Reserved keywords must stay rejected as call arguments",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testOutAssignmentStillTerminatesDefAtEnd() {
        // `out = 1` is an ordinary local (slice/sort.cr) — the def body must
        // still terminate at `end`, exactly like the other keyword variables.
        val file = myFixture.configureByText(
            "test.cr",
            "def f\n  out = 1\nend\nputs 1"
        )
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue(
            "A def body after an `out` assignment must still terminate at `end`, found ${errors.size} errors",
            errors.isEmpty()
        )
    }

    fun testDefBodyTerminatorStaysEnd() {
        val file = myFixture.configureByText(
            "test.cr",
            "def build\n  of = 1\nend\nputs 1"
        )
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertTrue(
            "A def body after keyword assignments must still terminate at `end`, found ${errors.size} errors",
            errors.isEmpty()
        )
    }

    fun testKeywordVariableIsTyped() {
        myFixture.configureByText(
            "test.cr",
            "def f\n  union = alloca type\n  union\nend"
        )
        myFixture.checkHighlighting()
    }
}
