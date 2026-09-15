package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidQualifiedReceiverTest : BasePlatformTestCase() {

    private fun assertHasParseError(code: String, message: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            message,
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsLeadingDoubleColonReceiver() {
        // The compiler rejects `def ::Time::Location.new` at parse time; the
        // receiver rule starts with CONSTANT and admits no leading `::`.
        assertHasParseError(
            "def ::Time::Location.new\nend",
            "Expected parse error for leading :: in a def receiver"
        )
    }

    fun testRejectsGenericReceiver() {
        // `def Box(Int32).new` is not a qualified receiver: generic arguments
        // are not part of a def receiver path.
        assertHasParseError(
            "def Box(Int32).new\nend",
            "Expected parse error for a generic def receiver"
        )
    }

    fun testRejectsTruncatedReceiverPath() {
        assertHasParseError(
            "def Time::.new\nend",
            "Expected parse error for a truncated def receiver path"
        )
    }

    fun testRejectsMissingMethodTarget() {
        assertHasParseError(
            "def Time::Location.\nend",
            "Expected parse error for a missing method target after the receiver DOT"
        )
    }
}
