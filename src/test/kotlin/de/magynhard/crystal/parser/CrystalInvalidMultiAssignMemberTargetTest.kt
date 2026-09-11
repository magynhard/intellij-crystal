package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidMultiAssignMemberTargetTest : BasePlatformTestCase() {

    fun testRejectsMemberTargetsWithArgumentsOrBlocks() {
        listOf(
            "a.foo(), b = 1, 2",
            "a.foo(1), b = 1, 2",
            "a.b {}, c = 1",
            "a.@x, b = 1, 2",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(
                "Expected parse error for '$source'",
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
            )
        }
    }
}
