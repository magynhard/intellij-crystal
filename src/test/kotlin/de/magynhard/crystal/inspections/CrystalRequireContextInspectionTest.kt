package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalRequireContextInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalRequireContextInspection::class.java)
    }

    fun testTopLevelRequireIsValid() {
        myFixture.configureByText("test.cr", "require \"./dependency\"")
        myFixture.checkHighlighting()
    }

    fun testRequireInMacroIfIsValid() {
        myFixture.configureByText("test.cr", """
            {% if flag?(:win32) %}
              require "./windows"
            {% end %}
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun testRequireInRuntimeIfIsDynamic() {
        checkError("""
            if true
              <error descr="Can't require dynamically">require</error> "./dependency"
            end
        """)
    }

    fun testRequireInBlockIsDynamic() {
        checkError("""
            1.times do
              <error descr="Can't require dynamically">require</error> "./dependency"
            end
        """)
    }

    fun testRequireAsAssignmentValueIsDynamic() {
        checkError("value = <error descr=\"Can't require dynamically\">require</error> \"./dependency\"")
    }

    fun testRequireAsArgumentIsDynamic() {
        checkError("load(<error descr=\"Can't require dynamically\">require</error> \"./dependency\")")
    }

    fun testRequireAsBareArgumentIsDynamic() {
        checkError("load <error descr=\"Can't require dynamically\">require</error> \"./dependency\"")
    }

    fun testRequireAsConditionIsDynamic() {
        checkError("""
            if <error descr="Can't require dynamically">require</error> "./dependency"
            end
        """)
    }

    fun testRequireWithPostfixConditionIsDynamic() {
        checkError("<error descr=\"Can't require dynamically\">require</error> \"./dependency\" if true")
    }

    fun testRequireInBinaryExpressionIsDynamic() {
        checkError("<error descr=\"Can't require dynamically\">require</error> \"./dependency\" || fallback")
    }

    fun testRequireInStringInterpolationIsDynamic() {
        checkError("value = \"#{<error descr=\"Can't require dynamically\">require</error> \"./dependency\"}\"")
    }

    fun testRequireInMacroInterpolationReportsCompilerError() {
        checkError("{{ <error descr=\"Can't execute Require in a macro\">require</error> \"./dependency\" }}")
    }

    fun testRequireInMacroControlReportsCompilerError() {
        checkError("{% <error descr=\"Can't execute Require in a macro\">require</error> \"./dependency\" %}")
    }

    fun testRequireMethodInMacroControlIsAllowed() {
        myFixture.configureByText("test.cr", "{% Loader.require \"./dependency\" %}")
        myFixture.checkHighlighting()
    }

    fun testRequireInMacroInterpolationInsideDefReportsDefError() {
        checkError("""
            def load
              {{ <error descr="Can't require inside def">require</error> "./dependency" }}
            end
        """)
    }

    fun testPostfixRequireInStringInterpolationIsDynamic() {
        checkError("value = \"#{<error descr=\"Can't require dynamically\">require</error> \"./dependency\" if true}\"")
    }

    fun testPostfixRequireInMacroInterpolationReportsCompilerError() {
        checkError("{{ <error descr=\"Can't execute Require in a macro\">require</error> \"./dependency\" if true }}")
    }

    fun testRequireAsRightBinaryOperandIsDynamic() {
        checkError("fallback + <error descr=\"Can't require dynamically\">require</error> \"./dependency\"")
    }

    fun testIncompleteRequireHasNoContextDiagnostic() {
        myFixture.configureByText("test.cr", """
            if true
              require
            end
        """.trimIndent())
        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }
        assertFalse(descriptions.any { it.startsWith("Can't require") })
    }

    fun testRequireInsideDefReportsCompilerError() {
        checkError("""
            def load
              <error descr="Can't require inside def">require</error> "./dependency"
            end
        """)
    }

    fun testRequireInsideFunReportsCompilerError() {
        checkError("""
            fun native_load
              <error descr="Can't require inside fun">require</error> "./dependency"
            end
        """)
    }

    fun testRequireInsideClassReportsCompilerError() {
        checkError("""
            class Loader
              <error descr="Can't require inside type declarations">require</error> "./dependency"
            end
        """)
    }

    fun testRequireInsideModuleReportsCompilerError() {
        checkError("""
            module Loader
              <error descr="Can't require inside type declarations">require</error> "./dependency"
            end
        """)
    }

    fun testRequireInsideStructReportsCompilerError() {
        checkError("""
            struct Loader
              <error descr="Can't require inside type declarations">require</error> "./dependency"
            end
        """)
    }

    private fun checkError(code: String) {
        myFixture.configureByText("test.cr", code.trimIndent())
        myFixture.checkHighlighting()
    }
}
