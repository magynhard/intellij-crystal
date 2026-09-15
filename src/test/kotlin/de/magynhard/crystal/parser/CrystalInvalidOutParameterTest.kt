package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidOutParameterTest : BasePlatformTestCase() {

    private fun assertHasParseError(code: String, message: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            message,
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsOutAsDefParameterName() {
        // The compiler rejects `out` as a parameter name
        // (invalid_internal_name?, parser.cr); `out = v` as a local stays valid.
        assertHasParseError(
            "def f(out)\nend",
            "Expected parse error for `out` as a def parameter name"
        )
    }

    fun testRejectsOutAsBlockParameterName() {
        assertHasParseError(
            "each do |out|\nend",
            "Expected parse error for `out` as a block parameter name"
        )
    }
}
