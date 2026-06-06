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

    fun testDefSampleInsertsEnd() {
        myFixture.configureByText("test.cr", "def sample<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end' after 'def sample'", text.contains("\nend"))
        val endCount = Regex("\\bend\\b").findAll(text).count()
        assertEquals("Should have exactly one 'end'", 1, endCount)
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

    fun testEndIndentationAfterIf() {
        myFixture.configureByText("test.cr", "if 1 == 2<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        // end should be at indent 0, aligned with 'if'
        assertEquals("end should be at indent 0", "end", text.trim().lines().last().trim())
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

    fun testEndInsertedAfterVariableAssignedIf() {
        myFixture.configureByText("test.cr", "b = if 1 == 2<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain 'end' after 'b = if'", text.contains("end"))
        // end should align with 'if' keyword (column 4)
        assertTrue("end should be at indent 4", text.contains("    end"))
    }

    fun testIndentAfterVariableAssignedIf() {
        myFixture.configureByText("test.cr", "b = if 1 == 2<caret>")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        // 'b = if' — if at column 4, body = 4 + 2 = 6 spaces
        assertEquals("Body should align with content after 'if'", "      ", textBeforeCaret)
    }

    fun testElseAlignmentWithVariableAssignedIf() {
        myFixture.configureByText("test.cr", "b = if 1 == 2\n      x\nelse<caret>")
        myFixture.type("\n")
        val offset = myFixture.editor.caretModel.offset
        val text = myFixture.editor.document.text
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val textBeforeCaret = text.substring(lineStart, offset)
        assertEquals("Body after else should be indented 2 from 'if' position", "      ", textBeforeCaret)
    }

    fun testNoEndInsertedWhenAlreadyPresent() {
        // end is on the line directly below
        myFixture.configureByText("test.cr", "if 1 == 2\n<caret>\nend")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        val endCount = Regex("\\bend\\b").findAll(text).count()
        assertEquals("Should have exactly one 'end' (already present)", 1, endCount)
    }

    fun testNoEndInsertedWhenEndBelowWithBlankLines() {
        // end exists below but with blank lines in between
        myFixture.configureByText("test.cr", "if 1 == 2\n<caret>\n\nend")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        val endCount = Regex("\\bend\\b").findAll(text).count()
        assertEquals("Should have exactly one 'end' (already present below blanks)", 1, endCount)
    }

    fun testEndInsertedWhenEndBelongsToOuterBlock() {
        // The 'end' below belongs to 'def', not to 'if' — so a new 'end' for 'if' is needed
        myFixture.configureByText("test.cr", "def sample\n  if true<caret>\nend")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        val endCount = Regex("\\bend\\b").findAll(text).count()
        assertEquals("Should have two 'end's (one for def, one for if)", 2, endCount)
        assertTrue("if's end should be at indent 2", text.contains("  end"))
    }

    fun testNoEndInsertedWhenEndExistsBelowContent() {
        // if block already has content and end — pressing ENTER should not insert another end
        myFixture.configureByText("test.cr", "if 1 == 2\n  puts x<caret>\nend")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        val endCount = Regex("\\bend\\b").findAll(text).count()
        assertEquals("Should have exactly one 'end' (already present)", 1, endCount)
    }

    fun testEndInsertedForNewDefInLargeFile() {
        // Simulate a large file with existing def/end pairs.
        // Adding a new top-level def should still insert its own end,
        // even though another def/end pair exists below.
        val content = """
def existing_method
  "hello"
end

def sample<caret>

def another_method
  42
end
""".trimIndent()
        myFixture.configureByText("test.cr", content)
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        val endCount = Regex("\\bend\\b").findAll(text).count()
        assertEquals("Should have 3 'end's (one for each def)", 3, endCount)
    }

    // ==================== Heredoc handling ====================

    fun testHeredocStartInsertsDelimiter() {
        myFixture.configureByText("test.cr", "heredoc = <<-END<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain heredoc body indent", text.contains("\n  "))
        assertTrue("Should contain END delimiter", text.contains("\nEND"))
    }

    fun testQuotedHeredocStartInsertsUnquotedDelimiter() {
        myFixture.configureByText("test.cr", "heredoc = <<-'TEXT'<caret>")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        assertTrue("Should contain heredoc body indent", text.contains("\n  "))
        assertTrue("Should contain unquoted TEXT delimiter", text.contains("\nTEXT"))
        assertFalse("Delimiter should NOT have quotes", text.contains("\n'TEXT'"))
    }

    fun testHeredocWithExistingDelimiterNotDuplicated() {
        myFixture.configureByText("test.cr", "heredoc = <<-END<caret>\n  content\nEND")
        myFixture.type("\n")
        val text = myFixture.editor.document.text
        val endCount = Regex("^END$", RegexOption.MULTILINE).findAll(text).count()
        assertEquals("Should have exactly one END delimiter", 1, endCount)
    }
}
