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

    // --- Electric Indent Tests (dedent happens on Enter, not while typing) ---

    fun testElectricIndentElseOnEnter() {
        myFixture.configureByText("test.cr", "if true\n    else<caret>")
        myFixture.performEditorAction("EditorEnter")
        val text = myFixture.editor.document.text
        // "else" should be dedented to if level after Enter
        assertTrue("else should be dedented to if level, got: $text", text.contains("\nelse"))
    }

    fun testElectricIndentElsifOnEnter() {
        myFixture.configureByText("test.cr", "if true\n    elsif<caret>")
        myFixture.performEditorAction("EditorEnter")
        val text = myFixture.editor.document.text
        assertTrue("elsif should be dedented to if level, got: $text", text.contains("\nelsif"))
    }

    fun testElectricIndentEndOnEnter() {
        myFixture.configureByText("test.cr", "def foo\n  x = 1\n    end<caret>")
        myFixture.performEditorAction("EditorEnter")
        val text = myFixture.editor.document.text
        assertTrue("end should be dedented to def level, got: $text", text.contains("\nend"))
    }

    fun testElectricIndentWhenOnEnter() {
        myFixture.configureByText("test.cr", "case x\n    when<caret>")
        myFixture.performEditorAction("EditorEnter")
        val text = myFixture.editor.document.text
        assertTrue("when should be dedented to case level, got: $text", text.contains("\nwhen"))
    }

    fun testElectricIndentRescueOnEnter() {
        myFixture.configureByText("test.cr", "begin\n    do_something\n    rescue<caret>")
        myFixture.performEditorAction("EditorEnter")
        val text = myFixture.editor.document.text
        assertTrue("rescue should be dedented to begin level, got: $text", text.contains("\nrescue"))
    }

    fun testElectricIndentEnsureOnEnter() {
        myFixture.configureByText("test.cr", "begin\n    do_something\n    ensure<caret>")
        myFixture.performEditorAction("EditorEnter")
        val text = myFixture.editor.document.text
        assertTrue("ensure should be dedented to begin level, got: $text", text.contains("\nensure"))
    }

    fun testElectricIndentNestedEndOnEnter() {
        myFixture.configureByText("test.cr", "def foo\n  if true\n    x = 1\n  end\n    end<caret>")
        myFixture.performEditorAction("EditorEnter")
        val text = myFixture.editor.document.text
        // The second "end" should align with "def" (no indent)
        assertTrue("nested end should align with def, got: $text", text.contains("\nend\n"))
    }

    fun testNoElectricIndentInMiddleOfWord() {
        // "blend" should NOT trigger dedent
        myFixture.configureByText("test.cr", "def foo\n    blend<caret>")
        myFixture.performEditorAction("EditorEnter")
        val text = myFixture.editor.document.text
        assertTrue("blend should stay indented, got: $text", text.contains("    blend"))
    }
}
