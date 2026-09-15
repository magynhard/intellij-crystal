package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidMacroExpressionTest : BasePlatformTestCase() {

    // NOTE: `{% if x %}` without `{% end %}` is NOT a plugin parse error:
    // macro_control is pure token consumption, so a complete `{% ... %}`
    // tag always parses (same model as the lib-body boundary tests).

    fun testRejectsEmptyMacroInterpolationInExpression() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = {{}}\nputs x"
        )
        assertTrue(
            "Expected parse error for empty macro interpolation in expression",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsUnterminatedMacroInterpolationInExpression() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = {{ y }\nputs x"
        )
        assertTrue(
            "Expected parse error for unterminated macro interpolation in expression",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsSpacedMacroGeneratedSymbol() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = : {{name.id}}\nputs x"
        )
        assertTrue(
            "Expected parse error for a space between colon and macro interpolation",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsSpacedMacroInterpolationBraces() {
        // `{{`/`}}` are only interpolation delimiters when tight
        // (compiler lexer.cr); `{{y} }` cannot close the interpolation.
        val file = myFixture.configureByText(
            "test.cr",
            "x = {{y} }\nputs x"
        )
        assertTrue(
            "Expected parse error for spaced macro interpolation braces",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsSpacedNumericSuffixInMacroInterpolation() {
        // `27_u32` is one literal; `27 _u32` is two juxtaposed expressions.
        val file = myFixture.configureByText(
            "test.cr",
            "x = {{ 27 _u32 }}\nputs x"
        )
        assertTrue(
            "Expected parse error for a spaced numeric suffix in macro interpolation",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
