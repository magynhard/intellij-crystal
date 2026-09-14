package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidSplatRestrictionTest : BasePlatformTestCase() {

    fun testRejectsDoubleSplatRestrictionWithoutSplatPrefix() {
        listOf(
            "def foo(x : **T)\nend",
            "def foo(&x : **T)\nend",
            "def foo(*x : **T)\nend",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(
                "Expected parse error for '$source'",
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
            )
        }
    }
}
