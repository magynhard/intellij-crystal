package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Negative shapes for indexed multi-assignment targets, mirroring the
 * compiler's multi-assign target acceptance (compiler-exchange: Call targets
 * with parentheses, blocks, or closing separators stay rejected).
 */
class CrystalInvalidMultiAssignIndexedTargetTest : BasePlatformTestCase() {

    fun testRejectsCallsOnSelfAndBrackets() {
        listOf(
            "self.foo(), b = 1, 2",
            "self.foo {}, b = 1, 2",
            "a[0](), b = 1, 2",
            "a[0] {}, b = 1, 2",
        ).forEach { source ->
            assertIndexedTargetError(source)
        }
    }

    fun testRejectsParenthesizedAndTypedWholeTargets() {
        listOf(
            "(a[0]), b = 1, 2",
            "a[0] : Int32, b = 1, 2",
        ).forEach { source ->
            assertIndexedTargetError(source)
        }
    }

    fun testRejectsMalformedIndexContents() {
        listOf(
            "a[i += ], b = 1, 2",
            "a[i, b = 1, 2",
        ).forEach { source ->
            assertIndexedTargetError(source)
        }
    }

    private fun assertIndexedTargetError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid indexed multi-assign target: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
