package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidStringParameterNameTest : BasePlatformTestCase() {

    fun testRejectsEmptyStringExternalName() {
        assertParseError("def fetch(\"\" internal)\n  internal\nend")
    }

    fun testRejectsInterpolatedStringExternalName() {
        assertParseError("def fetch(\"#{" + "x}\" internal)\n  internal\nend")
    }

    fun testRejectsStringExternalNameWithoutInternalBinding() {
        assertParseError("def fetch(\"http-header\")\nend")
    }

    private fun assertParseError(source: String) {
        val file = myFixture.configureByText("test.cr", source)
        assertTrue(
            "Expected parse error for invalid string parameter name in: $source",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
