package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalClassDefinition

class CrystalInvalidPostfixIndexedAssignmentTest : BasePlatformTestCase() {

    fun testRejectsMissingConditionWithoutConsumingFollowingDeclaration() {
        val file = myFixture.configureByText(
            "test.cr",
            "values[0] = 1 if\nclass Following\nend",
        )

        assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty())
        assertEquals("Following", PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)?.name)
    }

    fun testAcceptsChainedPostfixModifiersWithoutConsumingFollowingDeclaration() {
        // Chained postfix modifiers are valid (the compiler loops expression
        // suffixes); the following declaration must still parse separately.
        val file = myFixture.configureByText(
            "test.cr",
            "values[0] = 1 if first unless second\nclass Following\nend",
        )

        assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty())
        assertEquals("Following", PsiTreeUtil.findChildOfType(file, CrystalClassDefinition::class.java)?.name)
    }
}
