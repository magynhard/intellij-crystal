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
}
