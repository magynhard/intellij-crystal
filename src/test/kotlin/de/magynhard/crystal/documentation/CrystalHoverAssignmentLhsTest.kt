package de.magynhard.crystal.documentation

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalAssignment
import de.magynhard.crystal.psi.CrystalMethodDefinition

/**
 * Variable hovers over the ameba `colorize_markdown` shape: the gsub chain
 * overloads all declare `: String`, so the assignment left-hand sides infer
 * String (the resolveCall return merge); the macro-only
 * `colorize_text_styles` body infers Unknown, which the final read renders
 * as the honest "Unknown" placeholder; and no position renders an unrelated
 * method signature. The String reopenings stand in for the stdlib (unit
 * fixtures have no indexed stdlib root — the real-stdlib path is covered by
 * the headless kemal audit).
 */
class CrystalHoverAssignmentLhsTest : BasePlatformTestCase() {

    fun testColorizeMarkdownStringVariableHovers() {
        myFixture.configureByText("util.cr", """
            class String
              def gsub(pattern : Regex, replacement) : String
                self
              end

              def gsub(pattern : Regex, &) : String
                self
              end

              def gsub(string : String, replacement) : String
                self
              end

              def gsub(string : String, &) : String
                self
              end
            end

            def colorize_markdown(string : String, code_color : Int = 1)
              string = colorize_code_fences(string, code_color)
              string = colorize_text_styles(string)
              string
            end

            def colorize_code_fences(string : String, color : Int = 1)
              string
                .gsub(/x/, &.to_s)
            end

            def colorize_text_styles(string : String)
              {% begin %}
                {%
                  modes = {
                    underline: {/x/, "%1${'$'}s%2${'$'}s"},
                    bold:      {/y/, "%1${'$'}s%2${'$'}s%1${'$'}s"},
                  }
                %}

                string
                {% for mode, pattern in modes %}
                  .gsub({{ pattern[0] }}, %(<{{ mode.id }}>))
                {% end %}
              {% end %}
            end
        """.trimIndent())
        val method = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalMethodDefinition::class.java)
            .firstOrNull { it.name == "colorize_markdown" }!!
        val statements = method.methodBody!!.statementList!!.statementList
        assertEquals(3, statements.size)

        // Hover targets: the two assignment LHS identifiers + the final read.
        // The statement list wraps assignments in CrystalStatement composites.
        val assignment1 = PsiTreeUtil.getChildOfType(statements[0], CrystalAssignment::class.java)!!
        val assignment2 = PsiTreeUtil.getChildOfType(statements[1], CrystalAssignment::class.java)!!
        val lhs31 = (assignment1 as PsiNameIdentifierOwner).nameIdentifier!!
        val lhs32 = (assignment2 as PsiNameIdentifierOwner).nameIdentifier!!
        val read = lastReadIdentifier(statements[2])

        val provider = CrystalDocumentationProvider()

        fun stripTags(html: String?) = html.orEmpty()
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        val doc31 = stripTags(provider.generateDoc(lhs31, lhs31))
        assertTrue("LHS 31 renders String, got: $doc31", doc31.contains("String (Variable)"))
        assertFalse("LHS 31 must not render a method signature", doc31.contains("colorize_markdown"))

        // LHS 32 takes the post-assignment type: colorize_text_styles' body is
        // macro-generated and its return is not inferable — the honest
        // "Unknown" placeholder, never a guessed type.
        val doc32 = stripTags(provider.generateDoc(lhs32, lhs32))
        assertTrue("LHS 32 renders Unknown, got: $doc32", doc32.contains("Unknown (Variable) string"))
        assertFalse("LHS 32 must not render a method signature", doc32.contains("colorize_markdown"))

        val docRead = stripTags(provider.generateDoc(read, read))
        assertTrue(
            "Read 33 renders the honest Unknown placeholder, got: $docRead",
            docRead.contains("Unknown (Variable) string"),
        )

        // The custom-documentation path (the hover entry) honors the same rules.
        for (target in listOf(lhs31, lhs32, read)) {
            val element = provider.getCustomDocumentationElement(
                myFixture.editor, myFixture.file, target, target.textOffset
            )
            val doc = element?.let { provider.generateDoc(it, it) }
            assertFalse(
                "No method signature on hover, got: $doc",
                doc.orEmpty().contains("colorize_markdown"),
            )
        }
    }

    private fun lastReadIdentifier(statement: com.intellij.psi.PsiElement): PsiElement {
        // The final `string` statement: a CrystalExpression wrapping the variable reference.
        val varRef = PsiTreeUtil.findChildrenOfType(statement, de.magynhard.crystal.psi.CrystalVariableReference::class.java)
            .firstOrNull() ?: error("no variable reference in final statement")
        return varRef.node.findChildByType(de.magynhard.crystal.psi.CrystalTypes.IDENTIFIER)!!.psi
    }
}
