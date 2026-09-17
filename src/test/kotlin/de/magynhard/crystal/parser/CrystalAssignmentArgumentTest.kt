package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalArgument
import de.magynhard.crystal.psi.CrystalAssignment

class CrystalAssignmentArgumentTest : BasePlatformTestCase() {

    fun testMultipleAssignmentArguments() {
        val file = assertParsesCleanly("compute(x = 5, y = 6)")
        val assignments = PsiTreeUtil.findChildrenOfType(file, CrystalAssignment::class.java)
        assertEquals(2, assignments.size)
    }

    fun testSingleAssignmentArgument() {
        assertParsesCleanly("compute(x = 5)")
    }

    fun testIvarAssignmentArgument() {
        assertParsesCleanly("compute(@x = 5)")
    }

    fun testChainedAssignmentArgument() {
        assertParsesCleanly("compute(x = y = 5)")
    }

    fun testMixedComparisonAndAssignment() {
        assertParsesCleanly("compute(x == 5, y = 6)")
    }

    fun testNamedArgumentUnchanged() {
        val file = assertParsesCleanly("compute(x: 1)")
        assertTrue(
            "Named arguments must not become assignments",
            PsiTreeUtil.findChildrenOfType(file, CrystalAssignment::class.java).isEmpty(),
        )
    }

    fun testNamedTypeArgumentUnchanged() {
        val file = assertParsesCleanly("compute(x : Int32)")
        assertTrue(
            "Type-shaped arguments must not become assignments",
            PsiTreeUtil.findChildrenOfType(file, CrystalAssignment::class.java).isEmpty(),
        )
    }

    fun testOutArgumentUnchanged() {
        assertParsesCleanly("read(path, out value)")
    }

    fun testRejectsMissingValue() {
        assertParsesWithError("compute(x = )")
    }

    fun testRejectsBarePostfixModifier() {
        // The compiler rejects a bare trailing modifier in call arguments
        // ("expecting token ')', not 'if'"); only parenthesized modifiers
        // inside the right-hand side stay valid.
        assertParsesWithError("compute(x = 5 if ready, y = 6)")
    }

    fun testParenthesizedModifierInValue() {
        assertParsesCleanly("compute(x = (5 if ready))")
    }

    private fun assertParsesCleanly(code: String): PsiFile {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected no parse errors for: $code, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
        assertFalse(
            "Expected call arguments in: $code",
            PsiTreeUtil.findChildrenOfType(file, CrystalArgument::class.java).isEmpty(),
        )
        return file
    }

    private fun assertParsesWithError(code: String) {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty(),
        )
    }
}
