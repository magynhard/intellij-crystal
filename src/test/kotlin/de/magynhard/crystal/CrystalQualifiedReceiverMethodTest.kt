package de.magynhard.crystal

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils

/**
 * Regression tests for explicitly qualified method receivers
 * (`def Time::Location.new`): the method name is the target after the
 * receiver DOT — never a receiver segment — and constant receivers classify
 * as static with a qualified owner.
 */
class CrystalQualifiedReceiverMethodTest : BasePlatformTestCase() {

    private fun methodOf(code: String): CrystalMethodDefinition {
        val file = myFixture.configureByText("test.cr", code)
        return PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)
            ?: throw AssertionError("Expected a CrystalMethodDefinition in:\n$code")
    }

    fun testSingleSegmentReceiverName() {
        val method = methodOf("def Float64.new(value)\nvalue\nend")
        assertEquals("new", method.name)
        assertEquals("new", method.nameIdentifier?.text)
        assertTrue(CrystalPsiUtils.isSelfMethod(method))
        assertEquals("Float64", CrystalPsiUtils.explicitMethodReceiverQualifiedName(method))
    }

    fun testMultiSegmentReceiverName() {
        val method = methodOf("def Time::Location.new(pull)\nload(pull)\nend")
        assertEquals("new", method.name)
        assertEquals("new", method.nameIdentifier?.text)
        assertTrue(CrystalPsiUtils.isSelfMethod(method))
        assertEquals("Time::Location", CrystalPsiUtils.explicitMethodReceiverQualifiedName(method))
        assertEquals("Time::Location", CrystalPsiUtils.methodOwnerQualifiedName(method))
    }

    fun testMultiSegmentReceiverPredicateName() {
        val method = methodOf(
            "def Time::Location.from_json_object_key?(key : String) : Time::Location\nload(key)\nend"
        )
        assertEquals("from_json_object_key?", method.name)
        assertEquals("from_json_object_key?", method.nameIdentifier?.text)
    }

    fun testQualifiedSetterName() {
        val method = methodOf("def Time::Location.zone=(zone)\n@zone = zone\nend")
        assertEquals("zone=", method.name)
        assertEquals("zone", method.nameIdentifier?.text)
    }

    fun testQualifiedOperatorName() {
        val method = methodOf("def Time::Location.[](index)\nload(index)\nend")
        assertEquals("[]", method.name)
    }

    fun testQualifiedKeywordNameExcludesReceiver() {
        val method = methodOf("def Time::Location.require(path)\nload(path)\nend")
        assertEquals("require", method.name)
    }

    fun testPlainMethodUnchanged() {
        val method = methodOf("def kung\nend")
        assertEquals("kung", method.name)
        assertFalse(CrystalPsiUtils.isSelfMethod(method))
        assertNull(CrystalPsiUtils.explicitMethodReceiverQualifiedName(method))
    }

    fun testMacroReceiverStaysUnclassified() {
        val method = methodOf("def {{type.id}}.from_digits(x)\nnum\nend")
        assertEquals("from_digits", method.name)
        assertFalse(CrystalPsiUtils.isSelfMethod(method))
        assertNull(CrystalPsiUtils.explicitMethodReceiverQualifiedName(method))
    }

    fun testExplicitReceiverBeatsLexicalEnclosure() {
        val file = myFixture.configureByText(
            "test.cr",
            "struct Int8\ndef Float64.new(value)\nvalue\nend\nend"
        )
        val method = PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)
            ?: throw AssertionError("Expected a CrystalMethodDefinition")
        assertEquals("new", method.name)
        assertEquals("Float64", CrystalPsiUtils.methodOwnerQualifiedName(method))
    }
}
