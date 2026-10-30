package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.inspections.CrystalDotCallTargetResolver
import de.magynhard.crystal.inspections.DotCallResolution
import de.magynhard.crystal.psi.CrystalDotCallAccess

class CrystalEnumPredicateTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.addFileToProject("kinds.cr", """
            enum Kind
              HEREDOC
              StringArray
              HTTPRequest
              HTTP2Response
              A1BCd
            end
            enum OtherKind
              UNRELATED
            end
        """.trimIndent())
    }

    fun testCompilerPredicateNamesHandleCaseAcronymsAndDigits() {
        myFixture.configureByText("main.cr", "require \"./kinds\"\ndef consume(value : Kind)\nvalue.<caret>\nend")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Expected underscore names among $names", names.containsAll(listOf(
            "heredoc?", "string_array?", "http_request?", "http2_response?", "a1_b_cd?",
        )))
        assertFalse("unrelated?" in names)
    }

    fun testEnumPredicatesAreNotClassMethods() {
        myFixture.configureByText("main.cr", "require \"./kinds\"\nKind.heredoc?")
        val access = PsiTreeUtil.findChildOfType(myFixture.file, CrystalDotCallAccess::class.java)!!
        assertEquals(DotCallResolution.Unresolved, CrystalDotCallTargetResolver.resolve(access))
    }

    fun testUnrequiredEnumDoesNotSupplyPredicates() {
        myFixture.configureByText("main.cr", "def consume(value : Kind)\nvalue.<caret>\nend")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertFalse("heredoc?" in names)
    }

    fun testMissingPredicateIsNotBorrowedFromAnotherEnum() {
        myFixture.configureByText("main.cr", "require \"./kinds\"\ndef consume(value : Kind)\nvalue.unrelated?\nend")
        val access = PsiTreeUtil.findChildOfType(myFixture.file, CrystalDotCallAccess::class.java)!!
        assertEquals(DotCallResolution.Unresolved, CrystalDotCallTargetResolver.resolve(access))
    }

    fun testConditionalEnumMembersStayConservative() {
        myFixture.addFileToProject("conditional.cr", "enum Conditional\n{% if flag %}\nHEREDOC\n{% end %}\nend")
        myFixture.configureByText("main.cr", "require \"./conditional\"\ndef consume(value : Conditional)\nvalue.heredoc?\nend")
        val access = PsiTreeUtil.findChildOfType(myFixture.file, CrystalDotCallAccess::class.java)!!
        assertEquals(DotCallResolution.Suppressed, CrystalDotCallTargetResolver.resolve(access))
    }

    fun testInheritedUnrelatedMacroPrefixDoesNotHideProvenPredicate() {
        assertInheritedMacroResolution("to_{{name}}", accessor = true)
    }

    fun testInheritedMatchingMacroPrefixRemainsConservative() {
        assertInheritedMacroResolution("here{{name}}", accessor = false)
    }

    fun testInheritedUnrestrictedMacroNameRemainsConservative() {
        assertInheritedMacroResolution("{{name}}", accessor = false)
    }

    private fun assertInheritedMacroResolution(name: String, accessor: Boolean) {
        myFixture.addFileToProject("base.cr", "struct Enum\ndef $name()\nend\nend")
        myFixture.configureByText("main.cr", "require \"./base\"\nrequire \"./kinds\"\ndef consume(value : Kind)\nvalue.heredoc?\nend")
        val access = PsiTreeUtil.findChildOfType(myFixture.file, CrystalDotCallAccess::class.java)!!
        val result = CrystalDotCallTargetResolver.resolve(access)
        if (accessor) assertTrue("Expected predicate accessor, got $result", result is DotCallResolution.Accessor)
        else assertEquals(DotCallResolution.Suppressed, result)
    }
}
