package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalOutArgumentParsingTest : BasePlatformTestCase() {

    fun testRejectsInvalidOutTargets() {
        val allCallForms = listOf(
            "consume(out %s)",
            "consume out %s",
            "consume(target: out %s)",
            "consume target: out %s",
        )
        val cases = listOf(
            "@@target" to allCallForms,
            "\$target" to allCallForms,
            "Target" to allCallForms,
            "1" to allCallForms,
            // In a bare call, `.field` applies to the call result rather than the out target.
            "object.field" to listOf("consume(out %s)", "consume(target: out %s)"),
        )

        for ((target, calls) in cases) {
            for (call in calls) {
                val firstLine = call.format(target)
                myFixture.configureByText("test.cr", firstLine)
                val error = PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java)
                assertNotNull("$firstLine must not parse as a valid out argument", error)
                assertTrue("The invalid target must cause the first-line error", error!!.textOffset <= firstLine.length)
            }
        }
    }
}
