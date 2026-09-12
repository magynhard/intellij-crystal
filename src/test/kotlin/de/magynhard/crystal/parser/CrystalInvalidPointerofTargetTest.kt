package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidPointerofTargetTest : BasePlatformTestCase() {

    fun testRejectsSelf() {
        assertPointerofTargetError("pointerof(self)")
    }

    fun testRejectsLiteral() {
        assertPointerofTargetError("pointerof(1)")
    }

    fun testRejectsOrdinaryCall() {
        assertPointerofTargetError("pointerof(foo.bar)")
    }

    fun testRejectsCallWithArguments() {
        assertPointerofTargetError("pointerof(foo.bar())")
    }

    fun testRejectsIndexAccess() {
        assertPointerofTargetError("pointerof(foo[0])")
    }

    fun testRejectsIvarBeforeTerminalAccess() {
        assertPointerofTargetError("pointerof(foo.@bar.baz)")
    }

    private fun assertPointerofTargetError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid pointerof target: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
