package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidLibExternalVarTest : BasePlatformTestCase() {

    fun testRejectsInvalidLibExternalVarAliasTargets() {
        listOf(
            "lib LibC\n  \$numeric = 1 : Int32\nend",
            "lib LibC\n  \$symbolic = :free : Int32\nend",
            "lib LibC\n  \$qualified = LibC::free : Int32\nend",
            "lib LibC\n  \$incomplete =\nend",
            "lib LibC\n  \$newline_after_assign =\n  : Int32\nend",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(
                "Expected parse error for '$source'",
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
            )
        }
    }

    fun testRejectsLibExternalVarWithoutType() {
        val file = myFixture.configureByText(
            "test.cr",
            "lib LibC\n  \$notype = pcre_free\nend"
        )
        assertTrue(
            "Expected parse error for lib external var without type",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
