package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInvalidFunParameterTest : BasePlatformTestCase() {

    fun testRejectsDefaultValueOnUnnamedLibFunParameter() {
        val file = myFixture.configureByText(
            "test.cr",
            "lib LibC\n  fun bad_default(Int32 = 1)\nend"
        )
        assertTrue(
            "Expected parse error for default value on unnamed lib fun parameter",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsUnnamedParameterInDef() {
        val file = myFixture.configureByText(
            "test.cr",
            "def bad_def(Int32, x : String)\nend"
        )
        assertTrue(
            "Expected parse error for unnamed parameter outside lib fun",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsTopLevelFunAlias() {
        val file = myFixture.configureByText(
            "test.cr",
            "fun foo = bar\nend"
        )
        assertTrue(
            "Expected parse error for fun alias outside lib",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsTopLevelUppercaseFunName() {
        val file = myFixture.configureByText(
            "test.cr",
            "fun Foo : Int64\nend"
        )
        assertTrue(
            "Expected parse error for uppercase top-level fun name",
            PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
        )
    }

    fun testRejectsQualifiedLibFunName() {
        listOf(
            "lib LibC\n  fun Foo::Bar\nend",
            "lib LibC\n  fun ::Foo\nend",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(
                "Expected parse error for '$source'",
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
            )
        }
    }

    fun testRejectsInvalidLibFunAliasTargets() {
        listOf(
            "lib LibC\n  fun interpolated = \"bar#{suffix}\"\nend",
            "lib LibC\n  fun numeric = 1\nend",
            "lib LibC\n  fun symbolic = :bar\nend",
            "lib LibC\n  fun qualified = LibC::bar\nend",
            "lib LibC\n  fun incomplete =\nend",
        ).forEach { source ->
            val file = myFixture.configureByText("test.cr", source)
            assertTrue(
                "Expected parse error for '$source'",
                PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isNotEmpty()
            )
        }
    }
}
