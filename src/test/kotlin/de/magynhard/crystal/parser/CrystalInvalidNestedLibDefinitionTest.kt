package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidNestedLibDefinitionTest : BasePlatformTestCase() {

    fun testRejectsUnterminatedNestedLib() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Outer\n  lib Inner\n    fun answer : Int32\n  end\n"
        )
        assertTrue(
            "Expected parse error for an unterminated nested lib",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsLibInsideMethod() {
        val file = myFixture.configureByText(
            "test.cr",
            "def outer\n  lib Inner\n  end\nend\n"
        )
        assertTrue(
            "Expected parse error for a lib defined inside a method",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
