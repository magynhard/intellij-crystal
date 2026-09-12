package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidMacroSplatParameterTest : BasePlatformTestCase() {

    fun testRejectsBareInterpolationParameter() {
        assertSplatParameterError("def h({{ x }})\nend\n")
    }

    fun testRejectsSecondBareInterpolationParameter() {
        assertSplatParameterError("def i({{x.splat}}, {{y}})\nend\n")
    }

    fun testRejectsNonSplatInterpolationWithSplatMarker() {
        assertSplatParameterError("def j({{ x }}*)\nend\n")
    }

    private fun assertSplatParameterError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid macro splat parameter: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
