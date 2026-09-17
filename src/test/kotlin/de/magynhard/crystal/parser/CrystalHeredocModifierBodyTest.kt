package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalAssignment
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalVariableReference

class CrystalHeredocModifierBodyTest : BasePlatformTestCase() {

    fun testRescueModifierBodyAttachesToAssignment() {
        val file = assertParsesCleanly(
            "value = <<-TEXT rescue puts fallback\n  body line\nTEXT",
        )
        assertAssignmentBody(file, "body line")
    }

    fun testIfModifierConditionKeepsNoCallArguments() {
        val file = assertParsesCleanly(
            "other = <<-OTHER if enabled\n  other body\nOTHER",
        )
        assertAssignmentBody(file, "other body")
        val bogusCall = PsiTreeUtil.findChildrenOfType(file, CrystalMethodCallExpression::class.java)
            .firstOrNull { it.text.startsWith("enabled") }
        assertNull("enabled must stay a plain reference, not a call: $bogusCall", bogusCall)
        assertNotNull(
            "enabled must remain a variable reference",
            PsiTreeUtil.findChildrenOfType(file, CrystalVariableReference::class.java)
                .firstOrNull { it.text == "enabled" },
        )
    }

    fun testHeaderMarkerStillBindsAsBareArgument() {
        // Header markers carry delimiter text, so they are unaffected by the
        // body-opener guard and keep binding as ordinary bare arguments.
        val file = assertParsesCleanly(
            "fail <<-MSG, file, line\n  message\nMSG",
        )
        val call = PsiTreeUtil.findChildrenOfType(file, CrystalMethodCallExpression::class.java)
            .firstOrNull { it.text.startsWith("fail") }
        assertNotNull("Expected the fail call, got: ${file.text}", call)
        val body = PsiTreeUtil.findChildOfType(file, CrystalHeredocLiteral::class.java)
        assertNotNull("Expected the heredoc body", body)
        assertTrue(
            "Body must carry content, got: ${body!!.text}",
            body.text.contains("message"),
        )
    }

    private fun assertAssignmentBody(file: PsiFile, expectedContent: String) {
        val assignment = PsiTreeUtil.findChildOfType(file, CrystalAssignment::class.java)
        assertNotNull("Expected an assignment in:\n${file.text}", assignment)
        val body = PsiTreeUtil.findChildOfType(assignment, CrystalHeredocLiteral::class.java)
        assertNotNull("Expected the heredoc body under the assignment", body)
        assertTrue(
            "Body must carry content, got: ${body!!.text}",
            body.text.contains(expectedContent),
        )
    }

    private fun assertParsesCleanly(code: String): PsiFile {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected no parse errors for: $code, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
        return file
    }
}
