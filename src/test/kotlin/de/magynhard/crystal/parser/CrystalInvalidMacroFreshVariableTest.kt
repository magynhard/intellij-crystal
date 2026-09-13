package de.magynhard.crystal.parser

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertTrue

/**
 * Rejects malformed macro fresh variable forms. The compile assertions mirror
 * Crystal's parser: a `%`-prefixed name must be a lowercase identifier, the
 * optional braces key must hold expressions (`parse_macro_var_exps` accepts a
 * bare `{}` pair), and reads need some fresh var admission channel depending
 * on surrounding context.
 */
class CrystalInvalidMacroFreshVariableTest : BasePlatformTestCase() {

    fun testRejectsNumericFreshVariableName() {
        assertFreshVariableError("%1 = 1")
    }

    fun testRejectsKeyedLeadingComma() {
        assertFreshVariableError("%var{,}.foo")
    }

    fun testRejectsMissingKeyClosure() {
        assertFreshVariableError("%var{key = 1")
    }

    fun testRejectsConstantFreshVariableAssignmentAsOrdinaryExpression() {
        // As pure operator orphan it fails exactly like a stray `%` operator.
        assertFreshVariableError("%Val = 1")
    }

    private fun assertFreshVariableError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid macro fresh variable: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
