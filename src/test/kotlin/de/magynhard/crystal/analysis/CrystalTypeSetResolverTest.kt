package de.magynhard.crystal.analysis

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalBareArgument
import de.magynhard.crystal.psi.CrystalReturnStatement

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

    fun testMultiValueReturnResolvesAsTuple() {
        val file = myFixture.configureByText("test.cr", "def values\n  return 1, \"two\"\nend")
        val statement = PsiTreeUtil.findChildOfType(file, CrystalReturnStatement::class.java)!!
        val result = CrystalTypeSetResolver.resolve(statement)
        assertEquals(
            CrystalTypeResolution.Known(listOf(CrystalResolvedType("Tuple(Int32, String)"))),
            result,
        )
    }

    fun testMethodCallInfersMultiValueReturnTuple() {
        assertTypes(
            "def values\n  return 1, \"two\"\nend\nresult = values\n<caret>result",
            "Tuple(Int32, String)",
        )
    }

    fun testMultiValueReturnWithFinalAssignmentPreservesPostfixFallthrough() {
        assertTypes(
            "def values(flag)\n  return 1, value = 2 if flag\n  \"fallback\"\nend\n" +
                "result = values(true)\n<caret>result",
            "Tuple(Int32, Int32)",
            "String",
        )
    }

    fun testPostfixMultiValueReturnAssignmentDoesNotLeakIntoFallthroughState() {
        assertTypes(
            "x = \"old\"\nreturn 1, x = 2 if flag\n<caret>x",
            "String",
        )
    }

    fun testPostfixIndexedAssignmentMergesRhsAssignmentWithSkippedPath() {
        assertTypes(
            "x = \"old\"\nvalues = [0]\nvalues[0] = x = 1 if flag\n<caret>x",
            "String",
            "Int32",
        )
    }

    fun testIndexedAssignmentProvidesStatementValue() {
        assertTypes(
            "def update(values)\n  values[0] = \"updated\"\nend\nresult = update([\"old\"])\n<caret>result",
            "String",
        )
    }

    fun testPostfixIndexedAssignmentProvidesRhsAndSkippedValues() {
        assertTypes(
            "def update(values, flag)\n  values[0] = \"updated\" if flag\nend\n" +
                "result = update([\"old\"], true)\n<caret>result",
            "String",
            "Nil",
        )
    }

    fun testRescuedIndexedAssignmentProvidesRhsAndHandlerValues() {
        assertTypes(
            "def update(values)\n  values[0] = \"updated\" rescue 1\nend\n" +
                "result = update([\"old\"])\n<caret>result",
            "String",
            "Int32",
        )
    }

    fun testCompoundIndexedAssignmentStatementValueRemainsUnknown() {
        assertEquals(
            CrystalTypeResolution.Unknown,
            resolve(
                "def update(values)\n  values[0] += 1\nend\n" +
                    "result = update([0])\n<caret>result"
            ),
        )
    }

    fun testPostfixIndexedAssignmentConditionAssignmentAlwaysRuns() {
        assertTypes(
            "condition = \"old\"\nvalues = [0]\nvalues[0] = 1 if condition = true\n<caret>condition",
            "Bool",
        )
    }

    fun testPostfixAssignmentConditionAssignmentAlwaysRuns() {
        assertTypes(
            "condition = \"old\"\nvalue = 1 if condition = true\n<caret>condition",
            "Bool",
        )
    }

    fun testShortCircuitIndexedAssignmentKeepsSkippedRhsState() {
        assertTypes(
            "x = \"old\"\nvalues = [1] of Int32?\nvalues[0] ||= x = 1\n<caret>x",
            "String",
            "Int32",
        )
    }

    fun testAndShortCircuitIndexedAssignmentKeepsSkippedRhsState() {
        assertTypes(
            "x = \"old\"\nvalues = [1] of Int32?\nvalues[0] &&= x = 1\n<caret>x",
            "String",
            "Int32",
        )
    }

    fun testPostfixIndexedAssignmentWithAbruptRhsKeepsOnlySkippedState() {
        assertTypes(
            "def update(values, flag)\n  x = \"old\"\n  values[0] = begin return x = 1 end if flag\n  <caret>x\nend",
            "String",
        )
    }

    fun testShortCircuitIndexedAssignmentWithAbruptRhsKeepsOnlySkippedState() {
        assertTypes(
            "def update(values)\n  x = \"old\"\n  values[0] ||= begin return x = 1 end\n  <caret>x\nend",
            "String",
        )
    }

    fun testExpressionPositionedReturnInIndexedAssignmentKeepsOnlySkippedState() {
        assertTypes(
            "def update(values)\n  x = \"old\"\n  values[0] ||= return x = 1\n  <caret>x\nend",
            "String",
        )
    }

    fun testExpressionPositionedReturnInLogicalOperandKeepsOnlySkippedState() {
        assertTypes(
            "def update(flag)\n  x = \"old\"\n  flag || return x = 1\n  <caret>x\nend",
            "String",
        )
    }

    fun testExpressionPositionedBreakInLogicalOperandKeepsOnlySkippedState() {
        assertTypes(
            "def update(flag)\n  x = \"old\"\n  loop do\n    flag && break x = 1\n    <caret>x\n  end\nend",
            "String",
        )
    }

    fun testExpressionPositionedNextInLogicalOperandKeepsOnlySkippedState() {
        assertTypes(
            "def update(flag)\n  x = \"old\"\n  loop do\n    flag && next x = 1\n    <caret>x\n  end\nend",
            "String",
        )
    }

    fun testChainedLogicalExpressionPreservesAccumulatedShortCircuitState() {
        assertTypes(
            "def update\n  x = \"old\"\n  true || false || return x = 1\n  <caret>x\nend",
            "String",
        )
        assertTypes(
            "def update\n  x = \"old\"\n  false && true && return x = 1\n  <caret>x\nend",
            "String",
        )
    }

    fun testMixedLogicalExpressionRespectsAndPrecedence() {
        assertTypes(
            "def update\n  x = \"old\"\n  true || false && return x = 1\n  <caret>x\nend",
            "String",
        )
    }

    fun testLogicalTernaryConditionPreservesShortCircuitState() {
        assertTypes(
            "def update\n  x = \"old\"\n  true || (x = 1) ? nil : nil\n  <caret>x\nend",
            "String",
        )
    }

    fun testNestedExpressionPositionedReturnStopsEnclosingCall() {
        assertTypes(
            "def update(flag)\n  x = \"old\"\n  consume(flag || return x = 1)\n  <caret>x\nend",
            "String",
        )
    }

    fun testRaisingArgumentBeforeExpressionPositionedReturnReachesRescue() {
        assertTypes(
            "def update(flag)\n  x = \"old\"\n  begin\n    consume((x = 1), danger, flag || return)\n  rescue\n    <caret>x\n  end\nend",
            "Int32",
        )
    }

    fun testExpressionPositionedBreakStateReachesLoopExit() {
        assertTypes(
            "def update(flag)\n  x = \"old\"\n  while flag\n    false || break x = 1\n  end\n  <caret>x\nend",
            "String",
            "Int32",
        )
    }

    fun testExpressionPositionedNextStateReachesNextLoopIteration() {
        assertTypes(
            "def update\n  x = \"old\"\n  i = 0\n  while i < 1\n    i += 1\n    false || next x = 1\n  end\n  <caret>x\nend",
            "String",
            "Int32",
        )
    }

    fun testEnsureTransformsExpressionPositionedBreakState() {
        assertTypes(
            "def update(flag)\n  x = \"old\"\n  while flag\n    begin\n      false || break x = 1\n    ensure\n      x = true\n    end\n  end\n  <caret>x\nend",
            "String",
            "Bool",
        )
    }

    fun testExpressionPositionedReturnInTernaryBranchDoesNotFallThrough() {
        assertTypes(
            "def update(flag)\n  x = \"old\"\n  flag ? (x = 1) : (return x = true)\n  <caret>x\nend",
            "Int32",
        )
    }

    fun testIndexedAssignmentRescueMergesFailuresBeforeAndAfterRhsAssignment() {
        assertTypes(
            "x = \"old\"\nvalues = [0]\nvalues[danger] = x = 1 rescue nil\n<caret>x",
            "Int32",
            "String",
        )
    }

    fun testCompoundIndexedAssignmentRescueIncludesGetterFailure() {
        assertTypes(
            "x = \"old\"\nvalues = [0]\nvalues[0] += x = 1 rescue nil\n<caret>x",
            "Int32",
            "String",
        )
    }

    fun testIndexedAssignmentRescueAssignmentMergesWithSuccessfulPath() {
        assertTypes(
            "fallback = \"old\"\nvalues = [0]\nvalues[0] = 1 rescue fallback = 2\n<caret>fallback",
            "String",
            "Int32",
        )
    }

    fun testPostfixAssignmentRescueAssignmentMergesWithSuccessfulPath() {
        assertTypes(
            "fallback = \"old\"\nvalue = danger rescue fallback = 2\n<caret>fallback",
            "String",
            "Int32",
        )
    }

    fun testPostfixAssignmentRescueIsSkippedForNonRaisingBody() {
        assertTypes(
            "fallback = \"old\"\nvalue = 1 rescue fallback = 2\n<caret>fallback",
            "String",
        )
    }

    fun testPostfixRescueReturnDoesNotFallThrough() {
        assertTypes(
            "def strict_parse : String\n  \"parsed\"\nend\ndef value\n  return strict_parse rescue 1\n  \"fallback\"\nend\nresult = value\n<caret>result",
            "String",
            "Int32",
        )
    }

    fun testPostfixRescueReturnSkipsUnreachableHandlerType() {
        assertTypes(
            "def value\n  return 1 rescue \"fallback\"\nend\nresult = value\n<caret>result",
            "Int32",
        )
    }

    fun testLaterMultiValueReturnFailureSeesEarlierAssignmentInRescue() {
        assertTypes(
            "x = \"old\"\nbegin\n  return x = 1, danger\nrescue\n  <caret>x\nend",
            "Int32",
        )
    }

    fun testEarlierMultiValueReturnFailureDoesNotApplyLaterAssignmentInRescue() {
        assertTypes(
            "x = \"old\"\nbegin\n  return danger, x = 1\nrescue\n  <caret>x\nend",
            "String",
        )
    }

    fun testChainedAssignmentInMultiValueReturnResolvesInOrder() {
        val file = myFixture.configureByText("test.cr", "def values\n  return first = second = 1, \"two\"\nend")
        val statement = PsiTreeUtil.findChildOfType(file, CrystalReturnStatement::class.java)!!
        assertEquals(
            CrystalTypeResolution.Known(listOf(CrystalResolvedType("Tuple(Int32, String)"))),
            CrystalTypeSetResolver.resolve(statement),
        )
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

    fun testExternalStorageParameterLocalNameKeepsItsType() {
        assertTypes(
            "class Foo\nend\nclass Bar\nend\n" +
                "def use(external @internal : Foo | Bar)\n  <caret>internal\nend",
            "Foo",
            "Bar",
        )
    }

    fun testStorageParameterAccessKeepsItsType() {
        assertTypes(
            "class Foo\nend\nclass Bar\nend\n" +
                "class Host\n  def use(@internal : Foo | Bar)\n    <caret>@internal\n  end\nend",
            "Foo",
            "Bar",
        )
        assertTypes(
            "class Foo\nend\nclass Host\n  def use(@@internal : Foo)\n    <caret>@@internal\n  end\nend",
            "Foo",
        )
    }

    fun testBlockStorageParameterLocalAndStorageNamesKeepTheirType() {
        val prefix = "class Handler\nend\nclass Host\n  def use(&@handler : Handler)\n"
        assertTypes("${prefix}    <caret>handler\n  end\nend", "Handler")
        assertTypes("${prefix}    <caret>@handler\n  end\nend", "Handler")
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

    fun testMacroGeneratedSymbolInferSymbol() {
        assertTypes(
            "value = :{{name.id}}\n<caret>value",
            "Symbol"
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

    fun testBangTildeComparisonWithUnknownOverloadReturnRemainsUnknown() {
        assertUnknown(
            "class Matcher\n  def !~(other)\n    123\n  end\nend\n" +
                "value = Matcher.new !~ 1\n<caret>value"
        )
    }

    fun testBangTildeComparisonResolvesAnnotatedOverloadReturn() {
        assertTypes(
            "class Matcher\n  def !~(other : Matcher) : Bool\n    false\n  end\nend\n" +
                "value = Matcher.new !~ Matcher.new\n<caret>value",
            "Bool",
        )
    }

    fun testSpaceshipResolvesAnnotatedOverloadReturn() {
        assertTypes(
            "class Thing\n  def <=>(other : Thing) : Int32\n    0\n  end\nend\n" +
                "value = Thing.new <=> Thing.new\n<caret>value",
            "Int32",
        )
    }

    fun testRegexMatchResolvesUnionOverloadReturn() {
        assertTypes(
            "class Thing\n  def =~(other : Thing) : Int32 | Nil\n    nil\n  end\nend\n" +
                "value = Thing.new =~ Thing.new\n<caret>value",
            "Int32",
            "Nil",
        )
    }

    fun testRegexMatchWithOverloadDependentReturnRemainsUnknown() {
        assertUnknown("value = \"crystal\" =~ /ystal/\n<caret>value")
    }

    fun testSpaceshipWithOverloadDependentReturnRemainsUnknown() {
        assertUnknown("value = \"a\" <=> \"b\"\n<caret>value")
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

    fun testBinaryOperatorDispatchesExactValueAndReturnsTheOnlyApplicableOverload() {
        // Time-like shape: Time - Time -> Time::Span while Time#-(Time::Span)
        // returns Time — the argument types, not the receiver alone, pick the
        // overload. WhenRangeEntries.cr:1 (`diff = Time.utc - date.to_utc`).
        assertTypes(
            "class Time\nend\n" +
                "class Time::Span\nend\n" +
                "class Time\ndef self.utc : Time\n  new\nend\ndef to_utc : Time\n  self\nend\n" +
                "def -(other : Time::Span) : Time\n  self\nend\n" +
                "def -(other : Time) : Time::Span\n  Time::Span.new\nend\nend\n" +
                "date = Time.utc\n" +
                "diff = Time.utc - date.to_utc\n<caret>diff",
            "Time::Span"
        )
    }

    fun testBinaryOperatorPostfixStaysOnItsOwnOperand() {
        // The postfix chain on the right operand must not be swallowed by the
        // operator: `left - source.to_moment` calls Moment#-(Moment), not a
        // `to_moment` on the operator result.
        assertTypes(
            "class Moment\nend\nclass Source\ndef to_moment : Moment\n  Moment.new\nend\nend\n" +
                "class Moment\ndef -(other : Moment) : Moment\n  self\nend\nend\n" +
                "left = Moment.new\nsource = Source.new\n" +
                "diff = left - source.to_moment\n<caret>diff",
            "Moment"
        )
    }

    fun testBinaryOperatorWithUnknownRightOperandStaysUnknown() {
        assertUnknown("class Moment\ndef -(other : Moment) : Moment\n  self\nend\nend\n" +
            "left = Moment.new\ndiff = left - missing\n<caret>diff")
    }

    fun testBinaryOperatorWithConflictingOverloadReturnsStaysUnknown() {
        // Moment - Offset has no applicable overload (Moment#-(Offset)
        // returns Offset, so the conflicting pair stays resolvable through
        // exact argument matching — the verdict is Moment).
        assertTypes(
            "class Moment\nend\nclass Offset\nend\n" +
                "class Moment\n" +
                "  def -(other : Moment) : Moment\n    self\n  end\n" +
                "  def -(other : Offset) : Offset\n    Offset.new\n  end\n" +
                "end\n" +
                "diff = Moment.new - Moment.new\n<caret>diff",
            "Moment"
        )
    }

    fun testBinaryOperatorWithoutApplicableOverloadStaysUnknown() {
        assertUnknown(
            "class Moment\nend\nclass Offset\nend\n" +
                "class Moment\n" +
                "  def -(other : Offset) : Offset\n    Offset.new\n  end\n" +
                "end\n" +
                "diff = Moment.new - Moment.new\n<caret>diff"
        )
    }

    fun testBinaryOperatorPrecedenceSelectsAppliedOverloads() {
        // `a - b * c` dispatches the * first: with Moment#*(Int32) returning
        // Offset, subtraction receives the Offset-typed intermediate result —
        // Moment#-(Offset) : Moment decides the chain, proving the
        // multiplication dispatched before the subtraction.
        assertTypes(
            "class Moment\nend\nclass Offset\nend\n" +
                "class Moment\n" +
                "  def -(other : Moment) : Moment\n    self\n  end\n" +
                "  def -(other : Offset) : Moment\n    self\n  end\n" +
                "  def *(other : Int32) : Offset\n    Offset.new\n  end\n" +
                "end\n" +
                "a = Moment.new\nb = Moment.new\nc = 2\n" +
                "diff = a - b * c\n<caret>diff",
            "Moment"
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

    // Crystal's compiler treats `s.consume .helper` as a call to consume with NO
    // arguments ("wrong number of arguments ... given 0"), never as consume(.helper):
    // the space-separated dot is a receiver continuation on the call result.
    fun testSpaceSeparatedDotIsReceiverContinuationMatchingCompiler() {
        assertUnknown(
            "class Service\n" +
                "  def consume(callback) : String\n    \"text\"\n  end\n" +
                "  def helper : Int32\n    1\n  end\nend\n" +
                "service = Service.new\nresult = service.consume .helper\n<caret>result"
        )
        // String#helper does not exist -> the chain resolves to nothing concrete.
    }

    fun testDirectAndMutualRecursionTerminateUnknown() {
        assertUnknown("def value\n  value()\nend\nresult = value()\n<caret>result")
        assertUnknown(
            "def first\n  second()\nend\ndef second\n  first()\nend\n" +
                "result = first()\n<caret>result"
        )
    }

    fun testKeywordNamedBareArgumentResolvesValueType() {
        val file = myFixture.configureByText("test.cr", "consume for: <caret>\"value\"")
        val leaf = file.findElementAt(myFixture.caretOffset) ?: error("No PSI at caret")
        val argument = PsiTreeUtil.getParentOfType(leaf, CrystalBareArgument::class.java)
            ?: error("No bare argument at caret")

        val result = CrystalTypeSetResolver.resolve(argument) as CrystalTypeResolution.Known

        assertEquals(listOf("String"), result.types.map { it.name })
    }

    fun testTypeResolutionUsesOnlyCurrentAndForwardRequiredSources() {
        myFixture.addFileToProject("loaded.cr", "class Loaded\nend")
        myFixture.addFileToProject("not_loaded.cr", "class Hidden\nend")
        val context = myFixture.configureByText(
            "main.cr",
            "require \"./loaded\"\nclass Current\nend\nLoaded.new"
        )
        val session = CrystalTypeSetResolver.session(context)

        assertEquals(CrystalTypeIdentity("Current", "Current"), session.resolveType("Current", context))
        assertEquals(CrystalTypeIdentity("Loaded", "Loaded"), session.resolveType("Loaded", context))
        assertNull(session.resolveType("Hidden", context))
    }

    fun testUnqualifiedCallUsesOnlyForwardRequiredMethods() {
        myFixture.addFileToProject("loaded.cr", "def value : String\n  \"loaded\"\nend")
        myFixture.addFileToProject("not_loaded.cr", "def value : Int32\n  1\nend")

        assertTypes("require \"./loaded\"\nresult = value\n<caret>result", "String")
    }

    fun testArrayIndexResolvesElementType() {
        assertTypes(
            "def fetch(lines : Array(String))\n  line = lines[0]\n  <caret>line\nend",
            "String",
        )
    }

    fun testArrayIndexWithVariableResolvesElementType() {
        assertTypes(
            "def fetch(lines : Array(String), index : Int32)\n  line = lines[index]\n  <caret>line\nend",
            "String",
        )
    }

    fun testArrayIndexPreservesElementUnion() {
        assertTypes(
            "def fetch(values : Array(Int32 | String))\n  value = values[0]\n  <caret>value\nend",
            "Int32",
            "String",
        )
    }

    fun testNilSafeArrayIndexAddsNil() {
        assertTypes(
            "def fetch(lines : Array(String))\n  line = lines[0]?\n  <caret>line\nend",
            "String",
            "Nil",
        )
    }

    fun testNestedArrayIndexChainResolvesElementType() {
        assertTypes(
            "def fetch(matrix : Array(Array(String)))\n  value = matrix[0][1]\n  <caret>value\nend",
            "String",
        )
    }

    fun testHashIndexResolvesValueType() {
        assertTypes(
            "def fetch(by_name : Hash(String, Int32))\n  value = by_name[\"a\"]\n  <caret>value\nend",
            "Int32",
        )
    }

    fun testArrayRangeIndexKeepsContainerType() {
        assertTypes(
            "def fetch(lines : Array(String))\n  part = lines[1..2]\n  <caret>part\nend",
            "Array(String)",
        )
    }

    fun testTupleLiteralIndexResolvesElementType() {
        assertTypes(
            "def fetch\n  pair = {1, \"two\"}\n  first = pair[0]\n  <caret>first\nend",
            "Int32",
        )
    }

    fun testTupleDynamicIndexResolvesElementUnion() {
        assertTypes(
            "def fetch(index : Int32)\n  pair = {1, \"two\"}\n  value = pair[index]\n  <caret>value\nend",
            "Int32",
            "String",
        )
    }

    fun testStringIndexResolvesChar() {
        assertTypes(
            "def fetch(text : String)\n  char = text[0]\n  <caret>char\nend",
            "Char",
        )
    }

    fun testArrayIndexedReceiverDotCallResolvesReturnType() {
        assertTypes(
            "class Wrapper\n  def value : String\n    \"x\"\n  end\nend\n" +
                "def fetch(wrappers : Array(Wrapper))\n  result = wrappers[0].value\n  <caret>result\nend",
            "String",
        )
    }

    fun testHashIndexedReceiverDotCallResolvesReturnType() {
        assertTypes(
            "class Wrapper\n  def value : Int32\n    1\n  end\nend\n" +
                "def fetch(by_name : Hash(String, Wrapper))\n  result = by_name[\"a\"].value\n  <caret>result\nend",
            "Int32",
        )
    }

    fun testCustomCollectionIndexResolvesReturnAnnotation() {
        assertTypes(
            "class Box\n  def [](index : Int32) : String\n    \"x\"\n  end\nend\n" +
                "def fetch(box : Box)\n  value = box[0]\n  <caret>value\nend",
            "String",
        )
    }

    fun testCustomCollectionIndexedReceiverDotCallResolves() {
        assertTypes(
            "class Box\n  def [](index : Int32) : Wrapper\n    Wrapper.new\n  end\nend\n" +
                "class Wrapper\n  def value : Int32\n    1\n  end\nend\n" +
                "def fetch(box : Box)\n  result = box[0].value\n  <caret>result\nend",
            "Int32",
        )
    }

    fun testStringRangeIndexKeepsString() {
        assertTypes(
            "def fetch(text : String)\n  part = text[1..2]\n  <caret>part\nend",
            "String",
        )
    }

    fun testNilSafeStringIndexAddsNil() {
        assertTypes(
            "def fetch(text : String)\n  char = text[0]?\n  <caret>char\nend",
            "Char",
            "Nil",
        )
    }

    fun testNilableAnnotationResolvesToUnion() {
        assertTypes(
            "def fetch(value : String?)\n  <caret>value\nend",
            "String",
            "Nil",
        )
    }

    fun testNilableGenericAnnotationResolvesToUnion() {
        assertTypes(
            "def fetch(parts : Array(String)?)\n  <caret>parts\nend",
            "Array(String)",
            "Nil",
        )
    }

    fun testNilableArrayIndexResolvesElementType() {
        assertTypes(
            "def fetch(parts : Array(String)?)\n  line = parts[0]\n  <caret>line\nend",
            "String",
        )
    }

    fun testNilableHashIndexResolvesValueType() {
        assertTypes(
            "def fetch(by_name : Hash(String, Int32)?)\n  value = by_name[\"a\"]\n  <caret>value\nend",
            "Int32",
        )
    }

    fun testNilableStringIndexResolvesChar() {
        assertTypes(
            "def fetch(text : String?)\n  char = text[0]\n  <caret>char\nend",
            "Char",
        )
    }

    fun testNilableArrayNilSafeIndexAddsNil() {
        assertTypes(
            "def fetch(parts : Array(String)?)\n  element = parts[0]?\n  <caret>element\nend",
            "String",
            "Nil",
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
