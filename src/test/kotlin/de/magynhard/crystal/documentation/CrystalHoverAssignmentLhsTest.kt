package de.magynhard.crystal.documentation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalAssignment

/**
 * Hovering the left-hand side of an assignment is a variable hover: the
 * name-based fallbacks resolve through the require-graph lens and must never
 * render an unrelated method signature for a reassignment like
 * `string = colorize_text_styles(string)` (ameba util.cr:31-32).
 */
class CrystalHoverAssignmentLhsTest : BasePlatformTestCase() {

    fun testAssignmentLhsHoverReturnsVariableElement() {
        myFixture.configureByText("util.cr", """
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
              string
            end
        """.trimIndent())
        val provider = CrystalDocumentationProvider()
        val assignments = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalAssignment::class.java)
        assertTrue(assignments.isNotEmpty())
        for (assignment in assignments) {
            val nameId = (assignment as com.intellij.psi.PsiNameIdentifierOwner).nameIdentifier!!
            val resolved = provider.getCustomDocumentationElement(
                myFixture.editor, myFixture.file, nameId, nameId.textOffset
            )
            assertEquals(
                "The assignment LHS hover resolves to the variable itself",
                nameId, resolved,
            )
            val doc = provider.generateDoc(resolved, nameId)
            assertTrue(
                "The variable doc renders the inferred type, got: $doc",
                doc == null || !doc.contains("def colorize_markdown"),
            )
        }
        // The generateDoc path (direct element) honors the same rule: the
        // rendered doc must not contain the method signature.
        for (assignment in assignments) {
            val nameId = (assignment as com.intellij.psi.PsiNameIdentifierOwner).nameIdentifier!!
            val doc = provider.generateDoc(nameId, nameId)
            assertTrue(
                "generateDoc must not render the method signature, got: $doc",
                doc == null || !doc.contains("def colorize_markdown"),
            )
        }
    }
}
