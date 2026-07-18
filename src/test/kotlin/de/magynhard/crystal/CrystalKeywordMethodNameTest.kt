package de.magynhard.crystal

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalMethodDefinition

/**
 * Regression tests for `def self.<keyword>` and other keyword-as-method name
 * resolution. Before the fix, `getNameFromMethodName` walked the entire
 * method node including the body, producing strings like
 * `"def require(path)\nend"` as the method name — visible in the completion
 * popup as `def require(path)end(path)` and as a polluted stub index key.
 */
class CrystalKeywordMethodNameTest : BasePlatformTestCase() {

    private fun methodNameOf(code: String): String? {
        val file = myFixture.configureByText("test.cr", code)
        val method = PsiTreeUtil.findChildOfType(file, CrystalMethodDefinition::class.java)
        return method?.name
    }

    fun testKeywordMethodName_Require() {
        assertEquals(
            "require",
            methodNameOf("def self.require(path)\nend")
        )
    }

    fun testKeywordMethodName_Class() {
        assertEquals(
            "class",
            methodNameOf("def self.class(x)\nend")
        )
    }

    fun testKeywordMethodName_End() {
        assertEquals(
            "end",
            methodNameOf("def self.end(x)\nend")
        )
    }

    fun testKeywordMethodName_If() {
        assertEquals(
            "if",
            methodNameOf("def self.if(x)\nend")
        )
    }

    fun testKeywordMethodName_Begin() {
        assertEquals(
            "begin",
            methodNameOf("def self.begin(x)\nend")
        )
    }

    fun testKeywordMethodName_OperatorUnchanged() {
        assertEquals(
            "+",
            methodNameOf("def self.+(x)\nend")
        )
    }

    fun testKeywordMethodName_BracketOperatorUnchanged() {
        assertEquals(
            "[]",
            methodNameOf("def self.[](x)\nend")
        )
    }

    fun testKeywordMethodName_IdentifierUnchanged() {
        assertEquals(
            "kung",
            methodNameOf("def kung\nend")
        )
    }

    fun testKeywordMethodName_SelfIdentifierUnchanged() {
        assertEquals(
            "tanzen",
            methodNameOf("def self.tanzen\nend")
        )
    }

    fun testKeywordMethodName_ConstantUnchanged() {
        assertEquals(
            "Build",
            methodNameOf("def self.Build\nend")
        )
    }

    fun testKeywordMethodNotRenderedAsBody() {
        myFixture.configureByText("main.cr", """
            def self.require(path)
            end

            req<caret>
        """.trimIndent())
        val lookups = myFixture.complete(com.intellij.codeInsight.completion.CompletionType.BASIC)
        // `require` was a `def self.require` definition with a keyword
        // method name; it MUST NOT render as the body text in the lookup.
        if (lookups != null) {
            for (lookup in lookups) {
                val name = lookup.lookupString
                assertFalse(
                    "Lookup must not contain method body source: '$name'",
                    name.contains("def ") || name.contains("\nend") || name.contains("end(")
                )
            }
        }
    }
}