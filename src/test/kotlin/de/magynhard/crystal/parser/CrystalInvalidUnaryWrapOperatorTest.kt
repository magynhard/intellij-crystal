package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Negative shapes for unary wrapping operators: the compiler admits `&+`/`&-`
 * as prefix operators (parser_spec.cr:294/295) but rejects `&*`/`&**` prefix
 * and operand-less forms; wrap compound assignments beyond `&+=`/`&-=`/`&*=`
 * have no `&**=` token in the compiler's token set.
 */
class CrystalInvalidUnaryWrapOperatorTest : BasePlatformTestCase() {

    fun testRejectsUnaryWrapStarAndDoubleStar() {
        listOf(
            "value = &*operand",
            "value = &**operand",
        ).forEach { source ->
            assertUnaryWrapError(source)
        }
    }

    fun testRejectsOperandlessUnaryWraps() {
        listOf(
            "value = &-",
            "value = &+",
        ).forEach { source ->
            assertUnaryWrapError(source)
        }
    }

    private fun assertUnaryWrapError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid unary wrap operator: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
