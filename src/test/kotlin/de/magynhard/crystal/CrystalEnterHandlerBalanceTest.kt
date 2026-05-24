package de.magynhard.crystal

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the block-balance logic used by CrystalEnterHandler.
 * Tests the word tokenizer and balance counting independently.
 */
class CrystalEnterHandlerBalanceTest {

    // Replicates the balance logic from CrystalEnterHandler
    private val OPENER_KEYWORDS = setOf(
        "def", "class", "module", "struct", "enum",
        "if", "unless", "while", "until", "case",
        "begin", "do", "macro", "lib", "fun", "annotation",
        "for"
    )

    private fun tokenizeWords(text: String): List<String> {
        val words = mutableListOf<String>()
        var i = 0
        val len = text.length
        while (i < len) {
            val c = text[i]
            when {
                c == '#' -> { while (i < len && text[i] != '\n') i++ }
                c == '"' -> { i++; while (i < len && text[i] != '"') { if (text[i] == '\\') i++; i++ }; if (i < len) i++ }
                c == '\'' -> { i++; while (i < len && text[i] != '\'') { if (text[i] == '\\') i++; i++ }; if (i < len) i++ }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < len && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '?')) i++
                    words.add(text.substring(start, i))
                }
                else -> i++
            }
        }
        return words
    }

    private fun isBalanced(text: String): Boolean {
        var depth = 0
        for (word in tokenizeWords(text)) {
            when {
                word in OPENER_KEYWORDS -> depth++
                word == "end" -> depth--
            }
        }
        return depth <= 0
    }

    @Test
    fun testEmptyTextIsBalanced() {
        assertTrue(isBalanced(""))
    }

    @Test
    fun testSingleEndIsBalanced() {
        assertTrue(isBalanced("\nend"))
    }

    @Test
    fun testNoEndIsUnbalanced() {
        // Simulates: after "def foo\n", there's code but no end
        assertFalse(isBalanced("\n  def bar\n    puts 'hello'\n"))
    }

    @Test
    fun testMatchedIfEnd() {
        assertTrue(isBalanced("\n  if x\n    y\n  end\nend"))
    }

    @Test
    fun testNestedBlocksBalanced() {
        val text = """
            def foo
              if x
                puts x
              end
            end
        """.trimIndent()
        assertTrue(isBalanced(text))
    }

    @Test
    fun testNestedBlocksMissingOuterEnd() {
        val text = """
            def foo
              if x
                puts x
              end
        """.trimIndent()
        assertFalse(isBalanced(text))
    }

    @Test
    fun testKeywordsInStringsIgnored() {
        // "if" inside string should not count
        val text = """
            puts "if this end"
            end
        """.trimIndent()
        assertTrue(isBalanced(text))
    }

    @Test
    fun testKeywordsInCommentsIgnored() {
        val text = """
            # def another_method
            end
        """.trimIndent()
        assertTrue(isBalanced(text))
    }

    @Test
    fun testDoBlockBalanced() {
        val text = """
            items.each do |item|
              puts item
            end
        """.trimIndent()
        assertTrue(isBalanced(text))
    }

    @Test
    fun testDoBlockMissingEnd() {
        val text = """
            items.each do |item|
              puts item
        """.trimIndent()
        assertFalse(isBalanced(text))
    }

    @Test
    fun testSpecStyleBalanced() {
        val text = """
            describe App do
              it "works" do
                true.should eq(true)
              end
            end
        """.trimIndent()
        assertTrue(isBalanced(text))
    }
}
