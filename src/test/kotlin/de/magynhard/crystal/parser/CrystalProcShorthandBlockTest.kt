package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalBlock
import de.magynhard.crystal.psi.CrystalImplicitObjectCall

class CrystalProcShorthandBlockTest : BasePlatformTestCase() {

    fun testDoBlockBindsInsideShorthand() {
        val file = assertParsesCleanly(
            "backtrace.try &.each do |frame|\n  puts frame\nend",
        )
        assertBlockOwner(file, "each")
    }

    fun testBraceBlockBindsInsideShorthand() {
        val file = assertParsesCleanly(
            "list.select &.even? { |x| x }",
        )
        assertBlockOwner(file, "even?")
    }

    fun testBraceBlockParameterResolvesInsideShorthand() {
        myFixture.configureByText(
            "test.cr",
            "list.select &.even? { |x| <caret>x }",
        )
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("Expected an element at caret", element)
        val resolved = element!!.reference?.resolve()
            ?: element.parent?.reference?.resolve()
        assertNotNull("Expected x to resolve, got null", resolved)
        val block = PsiTreeUtil.getParentOfType(resolved, CrystalBlock::class.java)
        assertNotNull("Resolved target must sit inside the shorthand block", block)
        assertBlockOwner(myFixture.file, "even?")
    }

    fun testBlockParameterResolvesInsideShorthand() {
        myFixture.configureByText(
            "test.cr",
            "backtrace.try &.each do |frame|\n  puts <caret>frame\nend",
        )
        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull("Expected an element at caret", element)
        val resolved = element!!.reference?.resolve()
            ?: element.parent?.reference?.resolve()
        assertNotNull("Expected frame to resolve, got null", resolved)
        val block = PsiTreeUtil.getParentOfType(resolved, CrystalBlock::class.java)
        assertNotNull("Resolved target must sit inside the shorthand block", block)
        assertBlockOwner(myFixture.file, "each")
    }

    private fun assertBlockOwner(file: PsiFile, methodName: String) {
        val block = PsiTreeUtil.findChildOfType(file, CrystalBlock::class.java)
        assertNotNull("Expected a block", block)
        val owner = block!!.parent
        assertTrue(
            "Block must bind inside the &. proc, got ${owner?.javaClass?.simpleName}",
            owner is CrystalImplicitObjectCall,
        )
        assertTrue(
            "Block owner must be the $methodName call, got: ${owner?.text?.take(40)}",
            owner.text.startsWith(".$methodName"),
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
