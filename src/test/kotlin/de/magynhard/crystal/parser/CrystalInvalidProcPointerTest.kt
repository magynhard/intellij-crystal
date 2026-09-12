package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidProcPointerTest : BasePlatformTestCase() {

    fun testRejectsBareInstanceVarReceiver() {
        assertProcPointerError("->@worker")
    }

    fun testRejectsBareClassVarReceiver() {
        assertProcPointerError("->@@worker")
    }

    fun testRejectsLiteralReceiver() {
        assertProcPointerError("->1.run")
    }

    fun testRejectsNumericMethodName() {
        assertProcPointerError("->@worker.123")
    }

    private fun assertProcPointerError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid proc pointer: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
