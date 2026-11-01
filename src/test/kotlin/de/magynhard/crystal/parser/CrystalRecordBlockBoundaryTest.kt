package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalClassBody
import de.magynhard.crystal.psi.CrystalBlock
import de.magynhard.crystal.psi.CrystalMethodDefinition

class CrystalRecordBlockBoundaryTest : BasePlatformTestCase() {

    fun testOrdinaryMethodBlockDoesNotBecomeTypeBody() {
        val file = myFixture.configureByText(
            "test.cr",
            "wrapper do\n  def inner\n  end\nend\n\nvalue = 1\n"
        )

        // Calls may be declaration macros receiving a def as block input.
        // Preserve the call block rather than reclassifying it as a type body.
        assertNull(PsiTreeUtil.findChildOfType(file, CrystalClassBody::class.java))
        assertEmpty(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java))
        val block = PsiTreeUtil.findChildOfType(file, CrystalBlock::class.java)
        assertNotNull(block)
        assertNotNull(PsiTreeUtil.findChildOfType(block, CrystalMethodDefinition::class.java))
    }

    fun testRecordCalleeWithoutBlockParsesAsOrdinaryCall() {
        val file = myFixture.configureByText(
            "test.cr",
            "record Config, name : String\nvalue = 1\n"
        )

        assertNull(PsiTreeUtil.findChildOfType(file, CrystalClassBody::class.java))
        assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty())
    }
}
