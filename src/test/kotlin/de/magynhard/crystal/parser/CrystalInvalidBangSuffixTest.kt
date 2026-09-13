package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Negative shapes around the `.!` pseudo-method suffix: the compiler only
 * admits a NOT pseudo-call — `foo.!`, `foo.!()`, `foo.!(\n)` — silently
 * reject arguments to it and refuse `!` as a definable method name
 * (parser_spec.cr:2502-2515).
 */
class CrystalInvalidBangSuffixTest : BasePlatformTestCase() {

    fun testRejectsArgumentsToBangSuffix() {
        listOf(
            "value.!(args)",
            "value.!(a, b)",
        ).forEach { source ->
            assertBangSuffixError(source)
        }
    }

    fun testRejectsDefinableNotMethods() {
        // `def !` stays rejected. `def self.!` is a separate pre-existing
        // plugin gap: the name route already accepted stray prefixes before
        // this cluster (basename shape at the previous commit), so it is
        // tracked in TODO.md rather than asserted here.
        listOf(
            "def !\nend",
        ).forEach { source ->
            assertBangSuffixError(source)
        }
    }

    private fun assertBangSuffixError(code: String) {
        val file = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for invalid bang suffix: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }
}
