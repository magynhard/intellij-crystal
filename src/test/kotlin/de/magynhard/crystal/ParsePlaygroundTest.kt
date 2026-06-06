package de.magynhard.crystal

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ParsePlaygroundTest : BasePlatformTestCase() {
    fun testPlaygroundHasNoParseErrors() {
        val file = java.io.File("playground.cr")
        val content = file.readText()
        myFixture.configureByText("playground.cr", content)
        val errors = PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)
        if (errors.isNotEmpty()) {
            val sb = StringBuilder()
            sb.appendLine("=== Parse Errors in playground.cr (first 5 shown) ===")
            val doc = myFixture.editor.document
            errors.take(5).forEach {
                val line = doc.getLineNumber(it.textOffset)
                val lineStart = doc.getLineStartOffset(line)
                val lineEnd = doc.getLineEndOffset(line)
                val lineText = doc.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
                sb.appendLine("Error at line ${line + 1}: ${it.errorDescription}")
                sb.appendLine("  Line text: '$lineText'")
                sb.appendLine("  Context: '${it.text.take(100)}'")
            }
            sb.appendLine("Total errors: ${errors.size}")
            fail(sb.toString())
        }
    }

    fun testPlaygroundSection9() {
        val content = """
(1..10).reject { |n| n > 5
}
(1..10).reduce(0) { |sum, n| sum + n }
(1..5).each_with_index { |val, idx| puts "${'$'}{idx}: ${'$'}{val}" }
(1..5).flat_map { |n| [n, n * 10] }

%w(hello world).each_with_object({} of String => Int32) do |word, hash|
  hash[word] = word.size
end

# Times
5.times { |i| puts i }
3.upto(7) { |i| puts i }
10.downto(1) { |i| puts i }

# -----------------------------------------------------------------------------
# 9. Methods
# -----------------------------------------------------------------------------

def simple_method
  "hello"
end
        """.trimIndent()
        myFixture.configureByText("test.cr", content)
        val errors = PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)
        if (errors.isNotEmpty()) {
            val sb = StringBuilder()
            sb.appendLine("=== Parse Errors ===")
            val doc = myFixture.editor.document
            errors.forEach {
                val line = doc.getLineNumber(it.textOffset)
                val lineStart = doc.getLineStartOffset(line)
                val lineEnd = doc.getLineEndOffset(line)
                val lineText = doc.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
                sb.appendLine("Error at line ${line + 1}: ${it.errorDescription}")
                sb.appendLine("  Line text: '$lineText'")
            }
            fail(sb.toString())
        }
    }

    fun testMultiLineBlockBrace() {
        val content = """
(1..10).reject { |n| n > 5
}
(1..10).reduce(0) { |sum, n| sum + n }
        """.trimIndent()
        myFixture.configureByText("test.cr", content)
        val errors = PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)
        assertEmpty("Multi-line block brace should parse", errors.toList())
    }
}
