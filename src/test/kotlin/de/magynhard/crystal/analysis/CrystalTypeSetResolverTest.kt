package de.magynhard.crystal.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalTypeSetResolverTest : BasePlatformTestCase() {

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

    fun testQualifiedDotCallUsesExactTarget() {
        assertTypes(
            "module One\n  class Service\n    def self.value : String\n      \"text\"\n    end\n  end\nend\n" +
                "module Two\n  class Service\n    def self.value : Int32\n      1\n    end\n  end\nend\n" +
                "result = One::Service.value()\n<caret>result",
            "String"
        )
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
