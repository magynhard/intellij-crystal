package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalClassBody

class CrystalRecordBlockBoundaryTest : BasePlatformTestCase() {

    fun testOrdinaryMethodBlockDoesNotBecomeTypeBody() {
        val file = myFixture.configureByText(
            "test.cr",
            "wrapper do\n  def inner\n  end\nend\n\nvalue = 1\n"
        )

        // The block alternative stays a runtime block: no type body may appear,
        // and the invalid `def` inside the block remains a parse error.
        assertNull(PsiTreeUtil.findChildOfType(file, CrystalClassBody::class.java))
        assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty())
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
