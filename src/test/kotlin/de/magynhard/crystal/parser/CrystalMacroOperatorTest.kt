package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalBareArgument
import de.magynhard.crystal.psi.CrystalExpression
import de.magynhard.crystal.psi.CrystalMacroInterpolation
import de.magynhard.crystal.psi.CrystalMethodCallExpression

class CrystalMacroOperatorTest : BasePlatformTestCase() {

    fun testJuxtaposedOperandsStayOneExpression() {
        val file = assertParsesCleanly(
            "{% for op in %w(+) %}\n  def calc(other)\n    Vec3.new(@x {{op.id}} other.x)\n  end\n{% end %}",
        )
        val argument = PsiTreeUtil.findChildrenOfType(file, CrystalExpression::class.java)
            .firstOrNull { it.text.startsWith("@x") }
        assertNotNull("Expected the juxtaposed expression, got:\n${file.text}", argument)
        assertNotNull(
            "Receiver must keep its access node",
            PsiTreeUtil.findChildOfType(argument, de.magynhard.crystal.psi.CrystalInstanceVarAccess::class.java),
        )
        assertNotNull(
            "Operator interpolation must stay inside the expression",
            PsiTreeUtil.findChildOfType(argument, CrystalMacroInterpolation::class.java),
        )
    }

    fun testInterpolatedBareCallKeepsCallShape() {
        // `foo {{x}} bar` stays a call with two bare arguments: the call
        // alternatives match first, deep inside primary, so the postfix
        // macro-operator tail never engages.
        val file = assertParsesCleanly("foo {{x}} bar")
        val call = PsiTreeUtil.findChildOfType(file, CrystalMethodCallExpression::class.java)
        assertNotNull("Expected a method call, got:\n${file.text}", call)
        val arguments = PsiTreeUtil.findChildrenOfType(call, CrystalBareArgument::class.java)
        assertEquals(2, arguments.size)
    }

    private fun assertParsesCleanly(code: String): PsiFile {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected no parse errors for: $code, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
        return file
    }
}
