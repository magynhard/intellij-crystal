package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidPostfixModifierTest : BasePlatformTestCase() {

    fun testRejectsTrailingWhileAndUntilModifiers() {
        listOf(
            "x += 1 while x < 10",
            "puts x while x < 0",
            "return 1, 2 while false",
            "values[0] = 1 until true",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty())
        }
    }

    fun testAcceptsChainedIfUnlessRescueModifiers() {
        listOf(
            "x += 1 if a if b",
            "puts x if a unless b",
            "y = f rescue g rescue h",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(
                "Chained postfix modifiers must parse without errors: $source",
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty()
            )
        }
    }
}
