package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalMethodDefinition

class CrystalIncompleteBinaryOperatorTest : BasePlatformTestCase() {

    fun testPrefixOperatorPreservesEnclosingAndFollowingDefs() {
        val file = assertParsesWithError(
            "def before_prefix\n  value = !~ other\nend\n\ndef after_prefix\n  true\nend",
        )
        assertMethodNames(file, listOf("before_prefix", "after_prefix"))
    }

    fun testPostfixOperatorPreservesEnclosingAndFollowingDefs() {
        val file = assertParsesWithError(
            "def before_postfix\n  value = other !~\nend\n\ndef after_postfix\n  true\nend",
        )
        assertMethodNames(file, listOf("before_postfix", "after_postfix"))
    }

    fun testTopLevelErrorPreservesFollowingDef() {
        val file = assertParsesWithError(
            "toplevel_broken = !~ other\n\ndef after_toplevel\n  true\nend",
        )
        assertMethodNames(file, listOf("after_toplevel"))
    }

    fun testStrandedNonComparisonOperatorsKeepErroring() {
        // The stray recovery must not mask genuine errors: without an
        // upstream assignment error (leading position, trailing non-comparison
        // operator, block-param bars) the previous error behavior holds.
        assertParsesWithError("def a\n  x = b &&\nend\ndef c\nend\n")
        assertParsesWithError("def a\n  && x\nend\ndef c\nend\n")
        assertParsesWithError("foo do |out|\n  out\nend")
    }

    private fun assertMethodNames(file: PsiFile, expected: List<String>) {
        val methods = PsiTreeUtil.findChildrenOfType(file, CrystalMethodDefinition::class.java)
        assertEquals(expected, methods.map { it.name })
    }

    private fun assertParsesWithError(code: String): PsiFile {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse errors for: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty(),
        )
        return file
    }
}
