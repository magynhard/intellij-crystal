package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalTypes

/**
 * A member access that is still being written (`token.` at end of line) must
 * parse as a nameless `dot_call_access` instead of failing the whole enclosing
 * statement: otherwise the editor reports an error on the enclosing `loop do`
 * while the user types and completion can never fill the typed member in.
 * Anything other than a comment-only end-of-line tail after the dot keeps the
 * historical error reporting, so invalid pseudo-method suffixes
 * (`value.!(args)`) stay rejected (CrystalInvalidBangSuffixTest).
 */
class CrystalIncompleteDotAccessTest : BasePlatformTestCase() {

    fun testIncompleteAccessParsesInsideLoopBlockAndMethodBody() {
        listOf(
            "class Wrapper\n  def consume\n    loop do\n      token = next_token\n      token.\n      break unless token\n    end\n  end\nend",
            "def value\n  loop do\n    token.\n  end\nend",
            "def value\n  token = x\n  token.\nend",
            "def value\n  token.\n  break x\nend",
            "def value\n  receiver.\nend",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
            assertTrue(
                "Expected clean parse for: $source ${errors.joinToString { it.errorDescription }}",
                errors.isEmpty()
            )
        }
    }

    fun testTrailingCommentStaysIncompleteAndParses() {
        val file = myFixture.configureByText("test.cr", "def value\n  token. # note\n  break x\nend")
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
    }

    fun testNamelessAccessKeepsOnlyItsDot() {
        val file = myFixture.configureByText(
            "test.cr",
            "class Wrapper\n  def consume\n    loop do\n      token.\n      break unless token\n    end\n  end\nend"
        )
        val access = PsiTreeUtil.findChildrenOfType(file, CrystalDotCallAccess::class.java)
            .singleOrNull { access ->
                access.node.getChildren(null).all { child ->
                    child.psi is com.intellij.psi.PsiWhiteSpace || child.elementType == CrystalTypes.DOT
                }
            }
        assertNotNull("Expected a nameless dot_call_access", access)
    }

    fun testSameLineContentAfterDotStaysAnError() {
        listOf(
            "value.!(args)",
            "value.!(a, b)",
            "value.(args)",
            "def value\n  token.(args)\nend",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(
                "Expected parse error for: $source",
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
            )
        }
    }
}
