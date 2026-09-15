package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidMacroHashEntryTest : BasePlatformTestCase() {

    // NOTE: `{% if x %}` without `{% end %}` is NOT a plugin parse error —
    // macro_control is pure token consumption, so every complete `{% ... %}`
    // tag always parses.

    fun testRejectsMissingValueAfterMacroLabel() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = { {% for k in T %}{{ k }}:,{% end %} }\nputs x"
        )
        assertTrue(
            "Expected parse error for missing value after macro label",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsLeadingCommaBeforeFirstEntry() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = { ,a: 1 }\nputs x"
        )
        assertTrue(
            "Expected parse error for leading comma in hash",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsDoubleCommaInEntries() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = { a: 1,, b: 2 }\nputs x"
        )
        assertTrue(
            "Expected parse error for double comma in hash entries",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testAcceptsMacroOnlyHashEntryList() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = { {% if flag %}\n{% end %} }\nputs x"
        )
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertFalse(
            "Macro-only hash entry list should parse without errors, but found: ${errors.map { it.text }}",
            errors.isNotEmpty()
        )
    }

    fun testAcceptsStaticEntriesAroundMacroBlock() {
        val file = myFixture.configureByText(
            "test.cr",
            "x = { a: 1, {% if flag %} b: 2, {% end %} c: 3 }\nputs x"
        )
        val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
        assertFalse(
            "Static entries around macro block should parse without errors, but found: ${errors.map { it.text }}",
            errors.isNotEmpty()
        )
    }
}
