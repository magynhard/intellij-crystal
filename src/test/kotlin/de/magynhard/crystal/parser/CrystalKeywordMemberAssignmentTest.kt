package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalKeywordMemberAssignmentTest : BasePlatformTestCase() {

    fun testNestedKeywordSetter() {
        assertParsesCleanly("def push(node)\n  @tail = tail.next = node\nend")
    }

    fun testNestedIdentifierSetter() {
        assertParsesCleanly("def push(node)\n  @tail = tail.link = node\nend")
    }

    fun testSimpleKeywordSetter() {
        assertParsesCleanly("def refresh(node)\n  node.next = nil\nend")
    }

    fun testKeywordSetterCompoundAssignment() {
        assertParsesCleanly("def bump(node)\n  node.next += 1\nend")
    }

    fun testKeywordMemberRead() {
        assertParsesCleanly("def label(node)\n  node.next\nend")
    }

    fun testStandaloneNextControlFlow() {
        assertParsesCleanly("def skip(items)\n  items.each do |item|\n    next if item.done?\n  end\nend")
    }

    fun testRejectsNestedOperatorMemberAssignment() {
        assertParsesWithError("def broken(node)\n  x = tail.+ = node\nend")
    }

    private fun assertParsesCleanly(code: String) {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected no parse errors for: $code, got: " +
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java),
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty(),
        )
    }

    private fun assertParsesWithError(code: String) {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty(),
        )
    }
}
