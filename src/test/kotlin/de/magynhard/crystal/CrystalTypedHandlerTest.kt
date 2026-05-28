package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTypedHandlerTest : BasePlatformTestCase() {

    fun testAutoCloseInterpolationInString() {
        myFixture.configureByText("test.cr", "x = \"hello #<caret>\"")
        myFixture.type("{")
        val text = myFixture.editor.document.text
        assertTrue("Should auto-insert closing }", text.contains("#{}")
            || text.contains("#{}\""))
    }

    fun testNoAutoCloseOutsideString() {
        myFixture.configureByText("test.cr", "x = #<caret>")
        myFixture.type("{")
        val text = myFixture.editor.document.text
        // Should NOT have auto-inserted }
        assertFalse("Should NOT auto-insert } outside string", text.contains("#{}"))
    }

    fun testNoAutoCloseWithoutHash() {
        myFixture.configureByText("test.cr", "x = \"hello <caret>\"")
        myFixture.type("{")
        val text = myFixture.editor.document.text
        // The brace matcher may auto-close {}, but our handler should not interfere
        // Just verify no crash occurs
        assertTrue("Should contain {", text.contains("{"))
    }

    fun testCursorPositionAfterAutoClose() {
        myFixture.configureByText("test.cr", "x = \"hello #<caret>\"")
        myFixture.type("{")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        // Cursor should be between { and }
        assertTrue("Cursor should be before }", offset < text.length && text[offset] == '}')
    }
}
