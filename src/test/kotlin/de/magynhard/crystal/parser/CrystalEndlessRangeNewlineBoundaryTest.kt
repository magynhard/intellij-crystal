package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalAssignment

class CrystalEndlessRangeNewlineBoundaryTest : BasePlatformTestCase() {

    fun testEndlessRangeArgumentDoesNotSwallowNextStatement() {
        // lib/reply/src/reader.cr passes `(indent + shift).clamp 0..` as a
        // call argument and continues on the next line. The range must end
        // at the line break instead of consuming the next statement as its
        // right-hand side.
        val file = assertParsesCleanly(
            "@editor.update do\n" +
                "  new_indent = (indent + shift).clamp 0..\n" +
                "  @editor.current_line = \"x\"\n" +
                "end",
        )
        val assignment = PsiTreeUtil.findChildrenOfType(file, CrystalAssignment::class.java)
            .firstOrNull { it.text.startsWith("new_indent") }
        assertNotNull("Expected the new_indent assignment, got:\n${file.text}", assignment)
        assertFalse(
            "Range must not swallow the next line, got: ${assignment!!.text}",
            assignment.text.contains('\n'),
        )
        assertTrue(
            "Range argument must stay endless, got: ${assignment.text}",
            assignment.text.trimEnd().endsWith("0.."),
        )
    }

    fun testStatementLevelEndlessRangeEndsAtNewline() {
        // The same boundary applies outside call arguments: `first` is an
        // endless range and `second` stays a separate statement.
        val file = assertParsesCleanly("first = items.size..\nsecond = items.first")
        val assignments = PsiTreeUtil.findChildrenOfType(file, CrystalAssignment::class.java)
        assertEquals(
            "Expected two separate assignments, got:\n${file.text}",
            2,
            assignments.size,
        )
        assertTrue(
            "First assignment must be the endless range, got: ${assignments.first().text}",
            assignments.first().text.trimEnd().endsWith(".."),
        )
        assertTrue(
            "Second line must stay its own assignment, got: ${assignments.last().text}",
            assignments.last().text.startsWith("second"),
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
