package de.magynhard.crystal

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.injection.CrystalStringExpressionInjection
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.CrystalStringExpression
import de.magynhard.crystal.psi.impl.CrystalHeredocLiteralEscaper
import de.magynhard.crystal.psi.impl.CrystalStringExpressionMixin
import de.magynhard.crystal.psi.impl.CrystalStringLiteralEscaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Tests for injection host write-back and escaper behavior of heredoc and
 * string hosts: escape decoding with offset mapping, re-encoding on update,
 * and the multi-place no-op policy for interpolated bodies.
 */
class CrystalInjectionHostTest : BasePlatformTestCase() {

    // ==================== String escaper: decode + offset mapping ====================

    fun testStringEscaperDecodesEscapes() {
        myFixture.configureByText("main.cr", "s = \"a\\nb\"")
        val host = stringHost()
        val escaper = CrystalStringLiteralEscaper(host)
        val contentRange = CrystalStringExpressionMixin.contentRange(host)
        val builder = StringBuilder()
        assertTrue(escaper.decode(contentRange, builder))
        assertEquals("a\nb", builder.toString())
    }

    fun testStringEscaperMapsDecodedOffsetsBackToEscapeStart() {
        myFixture.configureByText("main.cr", "s = \"a\\nb\"")
        val host = stringHost()
        val escaper = CrystalStringLiteralEscaper(host)
        val contentRange = CrystalStringExpressionMixin.contentRange(host)
        val builder = StringBuilder()
        escaper.decode(contentRange, builder)
        // decoded: a(0) \n(1) b(2) — the newline comes from the 2-char escape
        // at content offset 1; 'b' sits behind it at offset 4 (relative to host)
        assertEquals(2, escaper.getOffsetInHost(1, contentRange))
        assertEquals(4, escaper.getOffsetInHost(2, contentRange))
    }

    fun testStringEscaperIsOneLine() {
        myFixture.configureByText("main.cr", "s = \"x\"")
        assertTrue(CrystalStringLiteralEscaper(stringHost()).isOneLine())
    }

    fun testHeredocEscaperIsMultiLine() {
        myFixture.configureByText("main.cr", "q = <<-SQL\nSELECT 1\nSQL\n")
        val host = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalHeredocLiteral::class.java).first()
        val escaper = CrystalHeredocLiteralEscaper(host)
        assertFalse(escaper.isOneLine())
        val builder = StringBuilder()
        val range = TextRange(0, host.textLength)
        assertTrue(escaper.decode(range, builder))
        assertEquals(host.text, builder.toString())
    }

    // ==================== String write-back ====================

    fun testStringWriteBackReEncodesEscapes() {
        myFixture.configureByText("main.cr", "sql = \"SELECT 1\"")
        val host = stringHost()
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            host.updateText("new \"x\"\n\tvalue")
        }
        assertEquals("sql = \"new \\\"x\\\"\\n\\tvalue\"", myFixture.file.text.trimEnd())
    }

    fun testInterpolatedStringWriteBackIsNoOp() {
        val original = "s = \"a#{name}b\""
        myFixture.configureByText("main.cr", original)
        val host = stringHost()
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            host.updateText("changed")
        }
        assertEquals(original, myFixture.file.text.trimEnd())
    }

    fun testHeredocSinglePlaceWriteBack() {
        myFixture.configureByText("main.cr", "q = <<-SQL\nSELECT 1\nSQL\n")
        val host = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalHeredocLiteral::class.java).first()
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            host.updateText("SELECT 2\n")
        }
        assertEquals("q = <<-SQL\nSELECT 2\nSQL\n", myFixture.file.text)
    }

    fun testInterpolatedHeredocWriteBackIsNoOp() {
        val original = "q = <<-SQL\nSELECT * FROM #{table}\nSQL\n"
        myFixture.configureByText("main.cr", original)
        val host = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalHeredocLiteral::class.java).first()
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            host.updateText("changed")
        }
        assertEquals(original, myFixture.file.text)
    }

    // ==================== Escaped interpolation stays literal ====================

    fun testEscapedHashBraceIsNotAnInterpolationGap() {
        myFixture.configureByText("main.cr", "# language=SQL\ns = \"SELECT \\#{x}\"\n")
        val host = stringHost()
        val segments = CrystalStringExpressionInjection.computeContentSegments(host)
        assertEquals(1, segments.size)
        assertEquals("SELECT \\#{x}", segments.single().range.substring(host.text))
    }

    private fun stringHost(): CrystalStringExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalStringExpression::class.java).first()
}
