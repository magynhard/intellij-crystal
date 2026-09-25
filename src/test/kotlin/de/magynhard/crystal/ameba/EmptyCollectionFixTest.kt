package de.magynhard.crystal.ameba

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalArrayLiteral
import de.magynhard.crystal.psi.CrystalHashLiteral

class EmptyCollectionFixTest : BasePlatformTestCase() {

    private fun syntaxIssue() =
        AmebaIssue("Lint/Syntax", AmebaSeverity.ERROR, "m", 1, 5, null, null)

    fun testFindsEmptyArrayLiteral() {
        myFixture.configureByText("test.cr", "a = []")
        val found = findEmptyUntypedCollection(myFixture.file, 4)
        assertTrue("Expected array literal, got: $found", found is CrystalArrayLiteral)
    }

    fun testFindsEmptyHashLiteral() {
        myFixture.configureByText("test.cr", "a = {}")
        val found = findEmptyUntypedCollection(myFixture.file, 4)
        assertTrue("Expected hash literal, got: $found", found is CrystalHashLiteral)
    }

    fun testSkipsNonEmptyLiterals() {
        myFixture.configureByText("test.cr", "a = [1]")
        assertNull(findEmptyUntypedCollection(myFixture.file, 4))
        myFixture.configureByText("test.cr", "a = {a: 1}")
        assertNull(findEmptyUntypedCollection(myFixture.file, 4))
    }

    fun testSkipsTypedLiterals() {
        myFixture.configureByText("test.cr", "a = [] of Int32")
        assertNull(findEmptyUntypedCollection(myFixture.file, 4))
    }

    fun testSkipsEmptyBlock() {
        // `{}` after a call is a block, not a hash literal.
        myFixture.configureByText("test.cr", "foo {}")
        assertNull(findEmptyUntypedCollection(myFixture.file, 4))
    }

    fun testSkipsOtherOffsets() {
        myFixture.configureByText("test.cr", "a = []")
        assertNull(findEmptyUntypedCollection(myFixture.file, 0))
        assertNull(findEmptyUntypedCollection(myFixture.file, 100))
    }

    fun testShouldOfferOnlyForSyntaxOnEmptyLiteral() {
        myFixture.configureByText("test.cr", "a = []")
        assertTrue(shouldOfferCollectionFix(syntaxIssue(), myFixture.file, 4))
        assertFalse(
            shouldOfferCollectionFix(
                syntaxIssue().copy(ruleName = "Style/RedundantReturn"), myFixture.file, 4
            )
        )
        myFixture.configureByText("test.cr", "a = [1]")
        assertFalse(shouldOfferCollectionFix(syntaxIssue(), myFixture.file, 4))
    }

    fun testShouldOfferOnlyForEmptyUntyped() {
        myFixture.configureByText("test.cr", "a = [] of Int32")
        assertFalse(shouldOfferCollectionFix(syntaxIssue(), myFixture.file, 4))
    }

    fun testShouldNotOfferInEcr() {
        myFixture.configureByText("test.ecr", "<% x = [] %>")
        assertFalse(shouldOfferCollectionFix(syntaxIssue(), myFixture.file, 7))
    }

    fun testInvokeInsertsArrayAnnotationAndMovesCaret() {
        myFixture.configureByText("test.cr", "a = []")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(4)
        EmptyCollectionTypeFix().invoke(project, editor, myFixture.file)
        assertEquals("a = [] of ", editor.document.text)
        assertEquals(10, editor.caretModel.offset)
    }

    fun testInvokeInsertsHashAnnotationAndMovesCaret() {
        myFixture.configureByText("test.cr", "a = {}")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(4)
        EmptyCollectionTypeFix().invoke(project, editor, myFixture.file)
        assertEquals("a = {} of ", editor.document.text)
        assertEquals(10, editor.caretModel.offset)
    }

    fun testInvokeDoesNothingOutsideLiteral() {
        myFixture.configureByText("test.cr", "a = [1]")
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(4)
        EmptyCollectionTypeFix().invoke(project, editor, myFixture.file)
        assertEquals("a = [1]", editor.document.text)
    }
}
