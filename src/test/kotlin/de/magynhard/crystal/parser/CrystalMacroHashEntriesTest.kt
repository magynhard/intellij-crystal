package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalHashEntry
import de.magynhard.crystal.psi.CrystalMacroInterpolation
import de.magynhard.crystal.psi.CrystalTypes

class CrystalMacroHashEntriesTest : BasePlatformTestCase() {

    fun testMacroControlHashValue() {
        assertParsesCleanly("x = {code: {% if flag?(:win32) %} first_call(value) {% else %} second_call(value) {% end %}}")
    }

    fun testMacroControlRocketHashValue() {
        assertParsesCleanly("x = {\"code\" => {% if flag?(:win32) %} first_call(value) {% else %} second_call(value) {% end %}}")
    }

    fun testSplicedKeyEntry() {
        val file = assertParsesCleanly("x = {generated{{n}}: {code: bar}}")
        val entry = PsiTreeUtil.findChildOfType(file, CrystalHashEntry::class.java)
        assertNotNull("Expected a hash entry", entry)
        val keyExpression = entry!!.expressionList.firstOrNull()
        assertNotNull("Expected a key expression", keyExpression)
        assertTrue(
            "Spliced key must keep the interpolation inside the entry's first expression",
            PsiTreeUtil.findChildOfType(keyExpression, CrystalMacroInterpolation::class.java) != null,
        )
        assertEquals(
            "generated",
            keyExpression!!.node.findChildByType(CrystalTypes.IDENTIFIER)?.text,
        )
    }

    fun testMacroSeparatedEntriesWithoutCommas() {
        assertParsesCleanly(
            "{% begin %}\n" +
                "  X = {\n" +
                "    first: {\n" +
                "      code: foo,\n" +
                "    },\n" +
                "    {% for n in [8, 16] %}\n" +
                "      generated{{n}}: {\n" +
                "        code: bar,\n" +
                "      },\n" +
                "    {% end %}\n" +
                "    last: {\n" +
                "      code: baz,\n" +
                "    },\n" +
                "  }\n" +
                "{% end %}",
        )
    }

    fun testRejectsCommaLessEntriesWithoutMacro() {
        assertParsesWithError("x = {a: 1 b: 2}")
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

    private fun assertParsesWithError(code: String) {
        val file: PsiFile = myFixture.configureByText("test.cr", code)
        assertTrue(
            "Expected parse error for: $code",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty(),
        )
    }
}
