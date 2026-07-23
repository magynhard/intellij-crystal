package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalTypes

class CrystalExactReceiverTypeResolverTest : BasePlatformTestCase() {

    fun testResolvesNearestPrecedingLocalConstructorAssignment() {
        assertResolved(
            """
                module Outer
                  class Service
                  end
                end
                value = Outer::Service.new
                value.run
            """.trimIndent(),
            ExactReceiverType("Service", "Outer::Service")
        )
    }

    fun testResolvesSimpleConstructorInExactLexicalNamespace() {
        assertResolved(
            """
                module Outer
                  class Service
                  end

                  def execute
                    value = Service.new
                    value.run
                  end
                end
                module Other
                  class Service
                  end
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Outer::Service")
        )
    }

    fun testLaterLocalAssignmentDoesNotAffectCall() {
        assertResolved(
            """
                class First
                end
                class Second
                end
                value = First.new
                value.run
                value = Second.new
            """.trimIndent(),
            ExactReceiverType("First", "First")
        )
    }

    fun testNearestUnsupportedReassignmentSuppressesOlderType() {
        assertUnresolved(
            """
                class Service
                end
                value = Service.new
                value = unknown_source
                value.run
            """.trimIndent()
        )
    }

    fun testConditionalConstructorExpressionWithNilBranchIsUnresolved() {
        assertUnresolved(
            """
                class Service
                end
                value = if condition
                  Service.new
                else
                  nil
                end
                value.run
            """.trimIndent()
        )
    }

    fun testConditionalConstructorExpressionWithUnknownBranchIsUnresolved() {
        assertUnresolved(
            """
                class Service
                end
                value = if condition
                  Service.new
                else
                  unknown_source
                end
                value.run
            """.trimIndent()
        )
    }

    fun testCompositeExpressionContainingConstructorIsUnresolved() {
        assertUnresolved(
            """
                class Service
                end
                value = [Service.new]
                value.run
            """.trimIndent()
        )
    }

    fun testGroupedConstructorExpressionIsExact() {
        assertResolved(
            """
                class Service
                end
                value = (Service.new)
                value.run
            """.trimIndent(),
            ExactReceiverType("Service", "Service")
        )
    }

    fun testSequentialAssignmentsInBeginUseNearestAssignment() {
        assertResolved(
            """
                class First
                end
                class Second
                end
                begin
                  value = First.new
                  value = Second.new
                end
                value.run
            """.trimIndent(),
            ExactReceiverType("Second", "Second")
        )
    }

    fun testConditionalAssignmentsInBeginAreAmbiguous() {
        assertUnresolved(
            """
                class First
                end
                class Second
                end
                begin
                  if condition
                    value = First.new
                  else
                    value = Second.new
                  end
                end
                value.run
            """.trimIndent()
        )
    }

    fun testUnconditionalAssignmentAfterConditionalInBeginIsNearest() {
        assertResolved(
            """
                class First
                end
                class Second
                end
                class Final
                end
                begin
                  if condition
                    value = First.new
                  else
                    value = Second.new
                  end
                  value = Final.new
                end
                value.run
            """.trimIndent(),
            ExactReceiverType("Final", "Final")
        )
    }

    fun testConditionalLocalAssignmentsAreAmbiguous() {
        assertUnresolved(
            """
                class Service
                end
                value = Service.new if condition
                value.run
            """.trimIndent()
        )
    }

    fun testLocalAssignmentDoesNotCrossSiblingMethodBoundary() {
        assertUnresolved(
            """
                class Service
                end
                class Runner
                  def prepare
                    value = Service.new
                  end

                  def execute
                    value.run
                  end
                end
            """.trimIndent()
        )
    }

    fun testLocalAssignmentDoesNotCrossNestedTypeBoundary() {
        assertUnresolved(
            """
                class Service
                end
                class Nested
                  value = Service.new
                end
                value.run
            """.trimIndent()
        )
    }

    fun testResolvesExactMethodParameterByInternalName() {
        assertResolved(
            """
                module Outer
                  class Service
                  end
                end
                def execute(external value : Outer::Service)
                  value.run
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Outer::Service")
        )
    }

    fun testResolvesExactBlockParameter() {
        assertResolved(
            """
                class Service
                end
                def execute(&value : Service)
                  value.run
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Service")
        )
    }

    fun testNilableAndUnionParametersAreUnresolved() {
        assertUnresolved(
            """
                class Service
                end
                def execute(value : Service?)
                  value.run
                end
            """.trimIndent()
        )
        assertUnresolved(
            """
                class Service
                end
                class Other
                end
                def execute(value : Service | Other)
                  value.run
                end
            """.trimIndent()
        )
    }

    fun testResolvesParenthesizedExactParameterRestriction() {
        assertResolved(
            """
                class Service
                end
                def execute(value : (Service))
                  value.run
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Service")
        )
    }

    fun testUntypedBlockParameterSuppressesOuterLocalType() {
        assertUnresolved(
            """
                class Service
                end
                value = Service.new
                [1].each do |value|
                  value.run
                end
            """.trimIndent()
        )
    }

    fun testResolvesExplicitlyTypedInstanceVariable() {
        assertResolved(
            """
                module Outer
                  class Service
                  end
                end
                class Runner
                  @value : Outer::Service

                  def execute
                    @value.run
                  end
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Outer::Service")
        )
    }

    fun testResolvesTypedInstanceVariableParameter() {
        assertResolved(
            """
                class Service
                end
                class Runner
                  def initialize(@value : Service)
                  end

                  def execute
                    @value.run
                  end
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Service")
        )
    }

    fun testExactInstanceVariableDeclarationTakesPrecedenceOverAssignments() {
        assertResolved(
            """
                class Service
                end
                class Other
                end
                class Runner
                  @value : Service
                  @value = Other.new
                  @value = unknown_source

                  def execute
                    @value.run
                  end
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Service")
        )
    }

    fun testResolvesConsistentInstanceVariableAssignmentsAcrossBranchesAndOrder() {
        assertResolved(
            """
                class Service
                end
                class Runner
                  def initialize(flag)
                    if flag
                      @value = Service.new
                    else
                      @value = Service.new
                    end
                  end

                  def execute
                    @value.run
                  end

                  def replace
                    @value = Service.new
                  end
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Service")
        )
    }

    fun testNestedTypeAssignmentsDoNotAffectInstanceVariable() {
        assertResolved(
            """
                class Service
                end
                class Other
                end
                class Runner
                  @value = Service.new

                  class Nested
                    @value = Other.new
                  end

                  def execute
                    @value.run
                  end
                end
            """.trimIndent(),
            ExactReceiverType("Service", "Service")
        )
    }

    fun testConflictingOrUnknownInstanceVariableAssignmentSuppressesType() {
        assertUnresolved(
            """
                class Service
                end
                class Other
                end
                class Runner
                  @value = Service.new
                  @value = Other.new

                  def execute
                    @value.run
                  end
                end
            """.trimIndent()
        )
        assertUnresolved(
            """
                class Service
                end
                class Runner
                  @value = Service.new
                  @value = unknown_source

                  def execute
                    @value.run
                  end
                end
            """.trimIndent()
        )
        assertUnresolved(
            """
                class Service
                end
                class Runner
                  @value : Service?

                  def execute
                    @value.run
                  end
                end
            """.trimIndent()
        )
    }

    fun testUnknownLocalClassVariableAndAmbiguousTypeIdentityAreUnresolved() {
        assertUnresolved("unknown.run")
        assertUnresolved(
            """
                class Runner
                  @@value = unknown_source
                  @@value.run
                end
            """.trimIndent()
        )
        assertUnresolved(
            """
                module One
                  class Service
                  end
                end
                module Two
                  class Service
                  end
                end
                def execute(value : Service)
                  value.run
                end
            """.trimIndent()
        )
    }

    fun testAssignmentsInUnrelatedFileDoNotDetermineLocalReceiver() {
        myFixture.addFileToProject(
            "other.cr",
            """
                class Service
                end
                value = Service.new
            """.trimIndent()
        )
        assertUnresolved("value.run")
    }

    private fun assertResolved(source: String, expected: ExactReceiverType) {
        assertEquals(expected, resolve(source))
    }

    private fun assertUnresolved(source: String) {
        assertNull(resolve(source))
    }

    private fun resolve(source: String): ExactReceiverType? {
        val file = myFixture.configureByText("test.cr", source)
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
        val call = PsiTreeUtil.findChildrenOfType(file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "run" }
        val receiver = previousSignificantSibling(call)
        return CrystalExactReceiverTypeResolver.resolve(receiver, call)
    }

    private fun previousSignificantSibling(element: PsiElement): PsiElement {
        var sibling = element.prevSibling
        while (sibling is PsiWhiteSpace || sibling?.node?.elementType == CrystalTypes.NEWLINE) {
            sibling = sibling.prevSibling
        }
        return requireNotNull(sibling)
    }

}
