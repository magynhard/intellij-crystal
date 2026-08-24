package de.magynhard.crystal.highlighting

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Multi-line unions without a backslash continuation parse (the parser stays
 * permissive so GrammarKit's error marker does not land on the rescue keyword),
 * but CrystalUnionContinuationAnnotator flags the dangling pipe with a
 * compiler-style message and offers an add-backslash quick fix.
 */
class CrystalUnionContinuationAnnotatorTest : BasePlatformTestCase() {

    fun testDanglingPipeInRescueUnionIsFlagged() {
        myFixture.configureByText("test.cr", """
            begin
              some_thing
            rescue ex : ArgumentError <error descr="Expected a type after '|' — multi-line union requires a '\' line continuation (crystal: expecting token 'CONST', not 'NEWLINE')">|</error>
                IndexError
              puts "handled"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testDanglingPipeInParameterSignatureIsFlagged() {
        myFixture.configureByText("test.cr", """
            def handle(payload : String <error descr="Expected a type after '|' — multi-line union requires a '\' line continuation (crystal: expecting token 'CONST', not 'NEWLINE')">|</error>
                Int32) : Nil
              nil
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testBackslashContinuationIsClean() {
        myFixture.configureByText("test.cr", """
            begin
              some_thing
            rescue ex : ArgumentError | \
                        IndexError
              puts "handled"
            end
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testQuickFixAddsBackslashBeforeNewline() {
        myFixture.configureByText("test.cr", """
            begin
              some_thing
            rescue ex : ArgumentError |<caret>
                IndexError
              puts "handled"
            end
        """.trimIndent())
        val action = myFixture.findSingleIntention("Add backslash line continuation")
        myFixture.launchAction(action)
        val text = myFixture.file.text
        assertTrue(
            "Backslash must sit directly before the newline",
            text.contains("ArgumentError | \\\n") || text.contains("ArgumentError |\\\n")
        )
    }
}
