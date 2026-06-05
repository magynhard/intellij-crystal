package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalEnterHandlerTest : BasePlatformTestCase() {

    fun testEndInsertedAfterDef() {
        myFixture.configureByText("test.cr", "def foo<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end' after Enter on 'def foo'", text.contains("end"))
        assertTrue("end should be after def foo", text.indexOf("end") > text.indexOf("def foo"))
    }

    fun testEndInsertedAfterClass() {
        myFixture.configureByText("test.cr", "class Foo<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end'", text.contains("end"))
    }

    fun testEndInsertedAfterModule() {
        myFixture.configureByText("test.cr", "module Bar<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end'", text.contains("end"))
    }

    fun testEndInsertedAfterDo() {
        myFixture.configureByText("test.cr", "items.each do<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end'", text.contains("end"))
    }

    fun testEndInsertedAfterIf() {
        myFixture.configureByText("test.cr", "if x > 0<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end'", text.contains("end"))
    }

    fun testEndInsertedAfterWhile() {
        myFixture.configureByText("test.cr", "while running<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end'", text.contains("end"))
    }

    fun testEndInsertedAfterCase() {
        myFixture.configureByText("test.cr", "case x<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end'", text.contains("end"))
    }

    fun testEndInsertedAfterDefWithParams() {
        myFixture.configureByText("test.cr", "def foo(x, y)<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end' after def with params", text.contains("end"))
    }

    fun testNoEndWhenAlreadyBalanced() {
        myFixture.configureByText("test.cr", "def foo<caret>\nend")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        val endCount = Regex("\\bend\\b").findAll(text).count()
        assertEquals("Should have exactly one 'end' (already balanced)", 1, endCount)
    }

    fun testNoEndOnNonBlockLine() {
        myFixture.configureByText("test.cr", "puts \"hello\"<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertFalse("Should NOT contain 'end' after puts", text.contains("\nend"))
    }

    fun testNoEndOnEmptyLine() {
        myFixture.configureByText("test.cr", "<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertFalse("Should NOT contain 'end' on empty line", text.contains("end"))
    }

    fun testNestedBlocksInsertEnd() {
        myFixture.configureByText("test.cr", "class Foo\n  def bar<caret>\n  end\nend")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        // Already balanced (def has end, class has end) - no new end
        val endCount = Regex("\\bend\\b").findAll(text).count()
        assertEquals("Should still have exactly 2 'end's (already balanced)", 2, endCount)
    }

    fun testEndIndentationMatchesOpener() {
        myFixture.configureByText("test.cr", "  def foo<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("end should be indented to match 'def'", text.contains("  end"))
    }

    fun testIndentAfterClass() {
        myFixture.configureByText("test.cr", "class Beispiel<caret>")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        // Cursor should be at indented position (2 spaces)
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        assertEquals("Cursor should be after 2-space indent", "  ", textBeforeCaret)
    }

    fun testIndentAfterDef() {
        myFixture.configureByText("test.cr", "def foo<caret>")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        assertEquals("Cursor should be after 2-space indent", "  ", textBeforeCaret)
    }

    fun testIndentAfterDefWithParams() {
        myFixture.configureByText("test.cr", "def foo(x, y)<caret>")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        assertEquals("Cursor should be after 2-space indent", "  ", textBeforeCaret)
    }

    fun testIndentAfterNestedDef() {
        myFixture.configureByText("test.cr", "class Foo\n  def bar<caret>\n  end\nend")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        assertEquals("Cursor should be after 4-space indent (nested)", "    ", textBeforeCaret)
    }

    fun testNoIndentAfterNormalLine() {
        myFixture.configureByText("test.cr", "puts \"hello\"<caret>")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        assertEquals("Cursor should have no indent after normal line", "", textBeforeCaret)
    }

    fun testArrayNewlineIndent() {
        myFixture.configureByText("test.cr", "a = [1, <caret>]")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        // 'a = [1' — first element '1' is at column 5
        assertEquals("Cursor should align with first array element", "     ", textBeforeCaret)
    }

    fun testArrayClosingBracketAlignsWithOpener() {
        myFixture.configureByText("test.cr", "a = [<caret>]\n")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        // The ] should be on its own line at indent 0 (aligned with 'a')
        assertTrue("] should be on its own line", text.contains("a = [\n  \n]"))
    }

    fun testHashNewlineIndent() {
        myFixture.configureByText("test.cr", "h = {a: 1, <caret>}")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        // 'h = {a:' — first element 'a:' is at column 5
        assertEquals("Cursor should align with first hash element", "     ", textBeforeCaret)
    }

    fun testNestedArrayNewlineIndent() {
        myFixture.configureByText("test.cr", "  x = [1, <caret>]")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        // '  x = [1' — first element '1' is at column 7
        assertEquals("Cursor should align with first element of nested array", "       ", textBeforeCaret)
    }

    fun testClosingBracketAlignsAfterMultiLineContent() {
        // Scenario: a = [1,\n     2,3<caret>]
        // After Enter after 3, ] should align with 'a' (indent 0), not with '2' (indent 5)
        myFixture.configureByText("test.cr", "a = [1,\n     2,3<caret>]")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("] should be on its own line aligned with 'a'", text.contains("2,3\n]"))
    }

    fun testClosingBracketAlignsWithVariableInHash() {
        myFixture.configureByText("test.cr", "h = {a: 1,\n     b: 2<caret>}")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("} should be on its own line aligned with 'h'", text.contains("b: 2\n}"))
    }
}
