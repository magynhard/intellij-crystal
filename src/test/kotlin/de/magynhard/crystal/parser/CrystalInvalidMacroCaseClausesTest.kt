package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidMacroCaseClausesTest : BasePlatformTestCase() {

    fun testRejectsStatementBeforeFirstClause() {
        assertCaseError("case value\nputs value\nwhen 1\nend\n")
    }

    fun testRejectsUnterminatedMacroTag() {
        assertCaseError("case value\n{% for i in x\nwhen 1\nend\n")
    }

    fun testRejectsMissingEnd() {
        assertCaseError("case value\nwhen 1\n1\n")
    }

    fun testRejectsMacroEndClosingCase() {
        assertCaseError("case value\nwhen 1\n1\n{% end %}\n")
    }

    private fun assertCaseError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid macro case clauses: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
