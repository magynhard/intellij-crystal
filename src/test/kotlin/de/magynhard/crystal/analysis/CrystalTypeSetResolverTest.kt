package de.magynhard.crystal.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTypeSetResolverTest : BasePlatformTestCase() {

    fun testRadixIntegerLiteralsRetainUnsuffixedMetadata() {
        listOf("0xff", "0o77", "0b1010").forEach { literal ->
            val result = resolve("<caret>$literal") as CrystalTypeResolution.Known
            assertEquals("Int32", result.types.single().name)
            assertTrue("$literal should be unsuffixed", result.types.single().isUnsuffixedNumericLiteral)
        }
        val suffixed = resolve("<caret>0xff_i64") as CrystalTypeResolution.Known
        assertEquals("Int64", suffixed.types.single().name)
        assertFalse(suffixed.types.single().isUnsuffixedNumericLiteral)
    }

    fun testPreservesOrderedStableTypeSetAndNumericMetadata() {
        val result = resolve("value = true ? 1 : 2_i64\n<caret>value")
        assertEquals(
            CrystalTypeResolution.Known(
                listOf(
                    CrystalResolvedType("Int32", isUnsuffixedNumericLiteral = true),
                    CrystalResolvedType("Int64")
                )
            ),
            result
        )
    }

    fun testNestedAssignmentUsesInnermostRightHandSide() {
        assertTypes("a = b = 1\n<caret>a", "Int32")
        assertTypes("a = b = 1\n<caret>b", "Int32")
    }

    fun testLaterAssignmentIsIgnored() {
        assertTypes("value = \"first\"\n<caret>value\nvalue = 1", "String")
    }

    fun testAssignmentsDoNotLeakAcrossSiblingMethods() {
        assertUnknown("def first\n  value = 1\nend\ndef second\n  <caret>value\nend")
    }

    fun testAssignmentsDoNotLeakAcrossNestedTypes() {
        assertUnknown("class One\n  value = 1\nend\nclass Two\n  <caret>value\nend")
    }

    fun testAssignmentsDoNotLeakAcrossFiles() {
        myFixture.addFileToProject("other.cr", "value = 1")
        assertUnknown("<caret>value")
    }

    fun testInternalMethodParameterIsVisibleInsideBlock() {
        assertTypes(
            "class Foo\nend\nclass Bar\nend\n" +
                "def use(external internal : Foo | Bar)\n  [1].each do |item|\n    <caret>internal\n  end\nend",
            "Foo",
            "Bar"
        )
    }

    fun testBlockParameterDoesNotLeakOutsideBlock() {
        assertUnknown("[1].each do |item|\n  item\nend\n<caret>item")
    }

    fun testBranchAssignmentsMergeConservatively() {
        assertTypes(
            "if true\n  value = 1\nelse\n  value = \"text\"\nend\n<caret>value",
            "Int32",
            "String"
        )
    }

    fun testConditionalBranchRetainsIncomingBinding() {
        assertTypes(
            "value = 1\nif true\n  value = \"text\"\nend\n<caret>value",
            "Int32",
            "String"
        )
    }

    fun testConditionalBranchRetainsIncomingParameterBinding() {
        assertTypes(
            "def use(value : Int32)\n  if true\n    value = \"text\"\n  end\n  <caret>value\nend",
            "Int32",
            "String"
        )
    }

    fun testPostfixAndLoopAssignmentsMergeIncomingBinding() {
        assertTypes("value = 1\nvalue = \"text\" if true\n<caret>value", "Int32", "String")
        assertTypes("value = 1\nwhile true\n  value = \"text\"\nend\n<caret>value", "Int32", "String")
    }

    fun testSequentialFlowPreservesConditionalAndIntermediateLoopStates() {
        assertTypes(
            "value = 1\ntrue ? (value = \"text\") : nil\n<caret>value",
            "Int32",
            "String"
        )
        assertTypes(
            "value = 1\nflag = true\nflag && (value = \"text\")\n<caret>value",
            "Int32",
            "String"
        )
        assertTypes(
            "value = 1\nwhile true\n  value = \"text\"\n  value = true\nend\n<caret>value",
            "Int32",
            "String",
            "Bool"
        )
    }

    fun testBeginFlowComposesNormalRescueElseAndEnsure() {
        assertTypes(
            "value = 1\nbegin\n  value = \"normal\"\nrescue\n  value = true\nelse\n  value = 'e'\nensure\n  1\nend\n<caret>value",
            "Bool",
            "Char"
        )
        assertTypes(
            "value = 1\nbegin\n  value = \"normal\"\nrescue\n  value = true\nensure\n  value = :ensured\nend\n<caret>value",
            "Symbol"
        )
    }

    fun testTerminatingAssignmentBranchesDoNotReachLaterVariableUses() {
        assertTypes(
            "def use(flag)\n  value = 1\n  if flag\n    value = \"terminated\"\n    return true\n" +
                "  else\n    value = 'c'\n  end\n  <caret>value\nend",
            "Char"
        )
        assertTypes(
            "def use(flag)\n  value = 1\n  case flag\n  when true\n    value = \"terminated\"\n" +
                "    return true\n  else\n    value = false\n  end\n  <caret>value\nend",
            "Bool"
        )
        assertTypes(
            "def use(flag)\n  value = 1\n  begin\n    value = \"normal\"\n  rescue\n" +
                "    value = true\n    return false\n  end\n  <caret>value\nend",
            "String"
        )
    }

    fun testAllTerminatingVariableBranchesHaveNoJoinState() {
        assertUnknown(
            "def use(flag)\n  value = 1\n  if flag\n    value = \"first\"\n    return true\n" +
                "  else\n    value = false\n    return false\n  end\n  <caret>value\nend"
        )
    }

    fun testRescueSeesLastProvenStateBeforePotentiallyRaisingCall() {
        assertTypes(
            "def use\n  value = 1\n  begin\n    value = \"ready\"\n    risky\n  rescue\n" +
                "    <caret>value\n  end\nend",
            "String"
        )
        assertUnknown(
            "def use\n  value = 1\n  begin\n    value = risky\n  rescue\n    <caret>value\n  end\nend"
        )
    }

    fun testRescueSeesPostArgumentAssignmentStateWhenEnclosingCallRaises() {
        assertTypes(
            "def use\n  value = 1\n  begin\n    consume(value = \"ready\")\n  rescue\n" +
                "    <caret>value\n  end\nend",
            "String"
        )
        assertUnknown(
            "def use\n  value = 1\n  begin\n    consume(value = risky)\n  rescue\n" +
                "    <caret>value\n  end\nend"
        )
    }

    fun testConditionalNestedAssignmentWithUnknownPathIsUnknown() {
        assertUnknown("if true\n  value = missing\nend\n<caret>value")
    }

    fun testIfIncludesEveryElsifAndElseInSourceOrder() {
        assertTypes(
            "value = if false\n  1\nelsif false\n  \"text\"\nelsif true\n  true\nelse\n  nil\nend\n<caret>value",
            "Int32",
            "String",
            "Bool",
            "Nil"
        )
    }

    fun testIfAndUnlessWithoutElseIncludeNil() {
        assertTypes("value = if true\n  1\nend\n<caret>value", "Int32", "Nil")
        assertTypes("value = unless false\n  \"text\"\nend\n<caret>value", "String", "Nil")
    }

    fun testUnlessAssignmentValueExcludesTerminatingArm() {
        assertTypes(
            "def use(flag)\n  value = unless flag\n    return 1\n  else\n    \"ready\"\n  end\n" +
                "  <caret>value\nend",
            "String"
        )
    }

    fun testEmptyBranchIsNil() {
        assertTypes("value = if true\nelse\n  \"text\"\nend\n<caret>value", "Nil", "String")
    }

    fun testCaseIncludesEveryWhenInSourceOrder() {
        assertTypes(
            "value = case 1\nwhen 1\n  1\nwhen 2\n  \"text\"\nelse\n  true\nend\n<caret>value",
            "Int32",
            "String",
            "Bool"
        )
    }

    fun testCaseIncludesEveryInClauseInSourceOrder() {
        assertTypes(
            "value = case {1, 2}\nin {1, x}\n  1\nin {x, 2}\n  \"text\"\nelse\n  nil\nend\n<caret>value",
            "Int32",
            "String",
            "Nil"
        )
    }

    fun testCaseWithoutElseIncludesNil() {
        assertTypes("value = case 1\nwhen 1\n  1\nend\n<caret>value", "Int32", "Nil")
    }

    fun testAssignmentValuedBranchesResolveRightHandSide() {
        assertTypes("value = if true\n  nested = 1\nelse\n  \"text\"\nend\n<caret>value", "Int32", "String")
    }

    fun testUnknownReachableBranchMakesResultUnknown() {
        assertUnknown("value = if true\n  missing\nelse\n  \"text\"\nend\n<caret>value")
        assertUnknown("value = true ? missing : \"text\"\n<caret>value")
    }

    fun testImplicitSelfMethodBeatsUnrelatedAndTopLevelMethods() {
        assertTypes(
            "def value : Bool\n  true\nend\n" +
                "class Other\n  def value : Int32\n    1\n  end\nend\n" +
                "class Current\n  def value : String\n    \"text\"\n  end\n" +
                "  def use\n    result = value()\n    <caret>result\n  end\nend",
            "String"
        )
    }

    fun testTopLevelMethodUsedWhenNoImplicitSelfCandidateExists() {
        assertTypes("def value : String\n  \"text\"\nend\nresult = value()\n<caret>result", "String")
    }

    fun testUniqueTopLevelMethodIsCallableInsideClass() {
        assertTypes(
            "def value : String\n  \"text\"\nend\n" +
                "class Current\n  def use\n    result = value()\n    <caret>result\n  end\nend",
            "String"
        )
    }

    fun testAmbiguousTopLevelFallbackInsideClassIsUnknown() {
        assertUnknown(
            "def value(x : Int32) : String\n  \"text\"\nend\n" +
                "def value(x : String) : Int32\n  1\nend\n" +
                "class Current\n  def use\n    result = value(1)\n    <caret>result\n  end\nend"
        )
    }

    fun testQualifiedDotCallUsesExactTarget() {
        assertTypes(
            "module One\n  class Service\n    def self.value : String\n      \"text\"\n    end\n  end\nend\n" +
                "module Two\n  class Service\n    def self.value : Int32\n      1\n    end\n  end\nend\n" +
                "result = One::Service.value()\n<caret>result",
            "String"
        )
    }

    fun testSimpleConstantUsesLexicalQualifiedIdentity() {
        assertTypes(
            "module One\n  class Service\n    def self.value : String\n      \"text\"\n    end\n  end\n" +
                "  result = Service.value()\n  <caret>result\nend\n" +
                "module Two\n  class Service\n    def self.value : Int32\n      1\n    end\n  end\nend",
            "String"
        )
    }

    fun testNearestLexicalConstantShadowsGlobalAndOuterCandidates() {
        assertTypes(
            "class Service\n  def self.value : Bool\n    true\n  end\nend\n" +
                "module Outer\n  class Service\n    def self.value : Int32\n      1\n    end\n  end\n" +
                "  module Inner\n    class Service\n      def self.value : String\n        \"text\"\n      end\n    end\n" +
                "    result = Service.value\n    <caret>result\n  end\nend",
            "String"
        )
    }

    fun testStaticDotCallUsesNearestLexicalTypeIdentity() {
        assertTypes(
            "class Service\n  def self.value : Bool\n    true\n  end\nend\n" +
                "module Outer\n  class Service\n    def self.value : String\n      \"text\"\n    end\n  end\n" +
                "  result = Service.value\n  <caret>result\nend",
            "String"
        )
    }

    fun testTopLevelCannotResolveNestedOnlySimpleConstant() {
        assertUnknown(
            "module One\n  class Service\n    def self.value : String\n      \"text\"\n    end\n  end\nend\n" +
                "result = Service.value()\n<caret>result"
        )
    }

    fun testModuleConstructorIsUnknown() {
        assertUnknown("module Service\nend\nresult = Service.new\n<caret>result")
    }

    fun testAbstractClassConstructorIsUnknown() {
        assertUnknown("abstract class Service\nend\nresult = Service.new\n<caret>result")
    }

    fun testIncludedAndExtendedMethodsResolveExactReturns() {
        assertTypes(
            "module Feature\n  def feature_value : String\n    \"text\"\n  end\nend\n" +
                "class Service\n  include Feature\nend\n" +
                "service = Service.new\nresult = service.feature_value()\n<caret>result",
            "String"
        )
        assertTypes(
            "module Factory\n  def build_value : Int32\n    1\n  end\nend\n" +
                "class Service\n  extend Factory\nend\n" +
                "result = Service.build_value()\n<caret>result",
            "Int32"
        )
    }

    fun testGenericSuperclassAndIncludeEdgesResolveBaseIdentity() {
        assertTypes(
            "class Parent(T)\n  def value : String\n    \"text\"\n  end\nend\n" +
                "class Child < Parent(Int32)\nend\nchild = Child.new\nresult = child.value\n<caret>result",
            "String"
        )
        assertTypes(
            "module Feature(T)\n  def value : Int32\n    1\n  end\nend\n" +
                "class Service\n  include Feature(String)\nend\nservice = Service.new\n" +
                "result = service.value\n<caret>result",
            "Int32"
        )
    }

    fun testLogicalOperatorsReturnOnlyReachableValuesByTruthiness() {
        assertTypes("value = 1 && \"text\"\n<caret>value", "String")
        assertTypes("value = 1 || \"text\"\n<caret>value", "Int32")
        assertTypes("value = nil && \"text\"\n<caret>value", "Nil")
        assertTypes("value = nil || \"text\"\n<caret>value", "String")
        assertTypes("value = false && \"text\"\n<caret>value", "Bool")
        assertTypes("value = false || \"text\"\n<caret>value", "String")
        assertTypes("flag = true\nvalue = flag && \"text\"\n<caret>value", "Bool", "String")
        assertTypes("flag = true\nvalue = flag || \"text\"\n<caret>value", "Bool", "String")
        assertTypes("left = true ? nil : 1\nvalue = left && \"text\"\n<caret>value", "Nil", "String")
        assertTypes("left = true ? nil : 1\nvalue = left || \"text\"\n<caret>value", "Int32", "String")
        assertUnknown("value = missing || \"text\"\n<caret>value")
    }

    fun testMultipleExactOverloadsAreUnknown() {
        assertUnknown(
            "class Service\n" +
                "  def value(x : Int32) : String\n    \"text\"\n  end\n" +
                "  def value(x : String) : Int32\n    1\n  end\nend\n" +
                "service = Service.new\nresult = service.value(1)\n<caret>result"
        )
    }

    fun testSingleExactMethodTargetIsKnown() {
        assertTypes(
            "class Service\n  def value(x : Int32) : String\n    \"text\"\n  end\nend\n" +
                "service = Service.new\nresult = service.value(1)\n<caret>result",
            "String"
        )
    }

    fun testUnannotatedMethodMergesExplicitAndImplicitReturns() {
        assertTypes(
            "def value(flag)\n  return 1 if flag\n  return true unless flag\n  \"text\"\nend\n" +
                "result = value(true)\n<caret>result",
            "Int32",
            "Bool",
            "String"
        )
    }

    fun testUnconditionalReturnExcludesUnreachableTail() {
        assertTypes(
            "def value\n  return 1\n  \"unreachable\"\nend\nresult = value()\n<caret>result",
            "Int32"
        )
    }

    fun testAllBranchTerminationExcludesUnreachableReturnsAndImplicitTail() {
        assertTypes(
            "def value(flag)\n  if flag\n    return 1\n  else\n    return \"text\"\n  end\n" +
                "  return true\n  :unreachable\nend\nresult = value(true)\n<caret>result",
            "Int32",
            "String"
        )
        assertTypes(
            "def value(flag)\n  begin\n    return 1\n  rescue\n    return \"text\"\n  end\n" +
                "  :unreachable\nend\nresult = value(true)\n<caret>result",
            "Int32",
            "String"
        )
    }

    fun testBeginAndMethodRescueResultsIgnoreEnsureValue() {
        assertTypes(
            "value = begin\n  1\nrescue FirstError\n  \"first\"\nrescue SecondError\n  true\nelse\n  'e'\nensure\n  :ignored\nend\n<caret>value",
            "String",
            "Bool",
            "Char"
        )
        assertTypes(
            "def value\n  1\nrescue FirstError\n  \"first\"\nrescue SecondError\n  true\nelse\n  'e'\nensure\n  :ignored\nend\n" +
                "result = value\n<caret>result",
            "String",
            "Bool",
            "Char"
        )
    }

    fun testInstanceVariablesStopAtNearestType() {
        assertUnknown(
            "class Outer\n  @value : String\n  class Inner\n    def use\n      <caret>@value\n    end\n  end\nend"
        )
        assertUnknown(
            "class First\n  @value : String\nend\nclass Second\n  def use\n    <caret>@value\n  end\nend"
        )
    }

    fun testNestedTypeUsesItsOwnInstanceVariableWithoutOuterLeakage() {
        assertTypes(
            "class Outer\n  @value : String\n  class Inner\n    @value : Int32\n" +
                "    def use\n      <caret>@value\n    end\n  end\nend",
            "Int32"
        )
    }

    fun testGenuineBareArgumentIsNotReceiverContinuation() {
        assertTypes(
            "class Service\n" +
                "  def consume(callback) : String\n    \"text\"\n  end\n" +
                "  def helper : Int32\n    1\n  end\nend\n" +
                "service = Service.new\nresult = service.consume .helper\n<caret>result",
            "String"
        )
    }

    fun testDirectAndMutualRecursionTerminateUnknown() {
        assertUnknown("def value\n  value()\nend\nresult = value()\n<caret>result")
        assertUnknown(
            "def first\n  second()\nend\ndef second\n  first()\nend\n" +
                "result = first()\n<caret>result"
        )
    }

    private fun assertTypes(source: String, vararg expected: String) {
        val result = resolve(source)
        assertTrue("Expected known types ${expected.toList()}, got $result", result is CrystalTypeResolution.Known)
        assertEquals(expected.toList(), (result as CrystalTypeResolution.Known).types.map { it.name })
    }

    private fun assertUnknown(source: String) {
        assertEquals(CrystalTypeResolution.Unknown, resolve(source))
    }

    private fun resolve(source: String): CrystalTypeResolution {
        val file = myFixture.configureByText("test.cr", source)
        val element = file.findElementAt(myFixture.caretOffset) ?: error("No PSI at caret")
        return CrystalTypeSetResolver.resolve(element)
    }
}
