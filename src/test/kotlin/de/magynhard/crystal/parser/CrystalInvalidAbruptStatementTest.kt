package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidAbruptStatementTest : BasePlatformTestCase() {

    fun testRejectsTrailingCommaWithoutAnotherValue() {
        listOf(
            "def value\n  return 1,\nend",
            "def value\n  loop do\n    break 1,\n  end\nend",
            "def value\n  1.times do\n    next 1,\n  end\nend",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty())
        }
    }

    fun testRejectsPostfixAssignmentBeforeAnotherValue() {
        val file = myFixture.configureByText(
            "test.cr",
            "def value(flag)\n  return result = 1 if flag, 2\nend",
        )
        assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty())
    }
}
