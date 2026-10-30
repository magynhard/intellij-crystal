package de.magynhard.crystal.analysis

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.inspections.CrystalUnresolvedNameInspection

class CrystalGuardedAssignmentFlowTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
    }

    private fun assertFlow(body: String, vararg expected: String) {
        myFixture.configureByText("definitions.cr", """
            module Crystal
              class ASTNode
              end
              class InstanceVar < ASTNode
                getter name : String
              end
              class ClassVar < ASTNode
                getter name : String
              end
              class Assign < ASTNode
                getter target : ASTNode
              end
              class TypeDeclaration < ASTNode
                getter var : ASTNode
              end
            end
            def visit(node : Crystal::Assign, declaration : Crystal::TypeDeclaration)
              $body
            end
        """.trimIndent())
        assertNull(PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java))
        val position = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val name = position.text
        val result = CrystalTypeSetResolver.session(position).resolveVariable(name, position)
        assertEquals(expected.toList(), (result as? CrystalTypeResolution.Known)?.types?.map { it.name })
        assertEmpty(myFixture.doHighlighting().filter { it.description == "Cannot find 'name'" })
    }

    fun testIsAGuardRefinesTheRightHandSideOfAnd() = assertFlow("""
        target = node.target
        next unless target.is_a?(Crystal::InstanceVar) && <caret>target.name == "@server"
    """.trimIndent(), "Crystal::InstanceVar")

    fun testCaseAssignmentRefinesTheAssignedTarget() = assertFlow("""
        case target = node.target
        when Crystal::InstanceVar, Crystal::ClassVar
          <caret>target.name
        end
    """.trimIndent(), "Crystal::InstanceVar", "Crystal::ClassVar")

    fun testCaseAssignmentRefinesTheAssignedVar() = assertFlow("""
        case var = declaration.var
        when Crystal::InstanceVar, Crystal::ClassVar
          <caret>var.name
        end
    """.trimIndent(), "Crystal::InstanceVar", "Crystal::ClassVar")

    fun testAndRefinementDoesNotLeakPastTheExpression() = assertFlow("""
        target = node.target
        target.is_a?(Crystal::InstanceVar) && target.name
        <caret>target
    """.trimIndent(), "Crystal::ASTNode")

    fun testOrDoesNotAssumeThePositiveIsAGuard() = assertFlow("""
        target = node.target
        target.is_a?(Crystal::InstanceVar) || <caret>target
    """.trimIndent(), "Crystal::ASTNode")

    fun testMixedLogicalConditionsRemainConservative() = assertFlow("""
        target = node.target
        target.is_a?(Crystal::InstanceVar) || true && <caret>target
    """.trimIndent(), "Crystal::ASTNode")

    fun testAssignmentInLaterAndOperandReplacesTheRefinement() = assertFlow("""
        target = node.target
        target.is_a?(Crystal::InstanceVar) && (target = node.target) && <caret>target
    """.trimIndent(), "Crystal::ASTNode")

    fun testAndGuardStillReportsAnActuallyMissingMember() {
        assertFlow("""
            target = node.target
            target.is_a?(Crystal::InstanceVar) && <caret>target.missing_member
        """.trimIndent(), "Crystal::InstanceVar")
        assertEquals(1, myFixture.doHighlighting().count { it.description == "Cannot find 'missing_member'" })
    }

    fun testCaseElseUsesTheAssignedValueRatherThanThePreviousBinding() = assertFlow("""
        target = Crystal::InstanceVar.new
        case target = node.target
        when Crystal::ClassVar
          target.name
        else
          <caret>target
        end
    """.trimIndent(), "Crystal::ASTNode")

    fun testCaseBranchAssignmentReplacesTheMatchedType() = assertFlow("""
        case target = node.target
        when Crystal::InstanceVar
          target = node.target
          <caret>target
        end
    """.trimIndent(), "Crystal::ASTNode")

    fun testCaseAssignmentDoesNotNarrowNonTypePatterns() = assertFlow("""
        case target = node.target
        when 1
          <caret>target
        end
    """.trimIndent(), "Crystal::ASTNode")

    fun testCaseAssignmentDoesNotLeakTheLastArmType() = assertFlow("""
        case target = node.target
        when Crystal::InstanceVar
          target.name
        when Crystal::ClassVar
          target.name
        end
        <caret>target
    """.trimIndent(), "Crystal::InstanceVar", "Crystal::ClassVar", "Crystal::ASTNode")

    fun testSingleCaseAssignmentTargetHasExactMemberNavigation() {
        assertFlow("""
            case target = node.target
            when Crystal::InstanceVar
              <caret>target.name
            end
        """.trimIndent(), "Crystal::InstanceVar")
        val offset = myFixture.caretOffset + "target.".length
        val reference = myFixture.file.findReferenceAt(offset)!!
        assertEquals("name : String", reference.resolve()?.text)
    }
}
