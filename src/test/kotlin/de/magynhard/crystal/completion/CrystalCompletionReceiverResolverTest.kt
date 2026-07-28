package de.magynhard.crystal.completion

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalCompletionReceiverResolverTest : BasePlatformTestCase() {

    fun testResolvesSimpleQualifiedAbsoluteAndGroupedTypeObjects() {
        assertReceiver(
            CompletionReceiver.TypeObject("Foo", "Foo", explicitIdentity = false),
            "class Foo\nend\nFoo.<caret>"
        )
        assertReceiver(
            CompletionReceiver.TypeObject("Bar", "Outer::Bar", explicitIdentity = true),
            "module Outer\n  class Bar\n  end\nend\nOuter::Bar.<caret>"
        )
        assertReceiver(
            CompletionReceiver.TypeObject("Foo", "Foo", explicitIdentity = true),
            "class Foo\nend\n::Foo.<caret>"
        )
        assertReceiver(
            CompletionReceiver.TypeObject("Foo", "Foo", explicitIdentity = false),
            "class Foo\nend\n((Foo)).<caret>"
        )
        assertReceiver(
            CompletionReceiver.TypeObject("Box", "Box", explicitIdentity = false),
            "class Box\nend\nBox(Int32).<caret>"
        )
    }

    fun testRejectsAmbiguousSimpleTypeObjectIdentity() {
        assertReceiver(
            CompletionReceiver.Unknown,
            "module One\n  class Item\n  end\nend\n" +
                "module Two\n  class Item\n  end\nend\nItem.<caret>"
        )
    }

    fun testResolvesSimpleTypeObjectByLexicalIdentity() {
        assertReceiver(
            CompletionReceiver.TypeObject("Item", "One::Item", explicitIdentity = false),
            "module One\n  class Item\n  end\n  Item.<caret>\nend\n" +
                "module Two\n  class Item\n  end\nend"
        )
    }

    fun testResolvesRecordTypeObjectThroughSharedConstructorAnalysis() {
        val position = configurePosition("record Config, name : String\nConfig.<caret>")
        val receiver = CrystalCompletionReceiverResolver.resolve(position) as CompletionReceiver.TypeObject

        assertEquals("Config", receiver.simpleName)
        assertEquals("Config", receiver.qualifiedName)
        assertFalse(receiver.explicitIdentity)
        assertNotNull(receiver.recordDefinition)
        assertTrue(receiver.recordDefinition!!.text.startsWith("record Config"))
    }

    fun testRecordWinsSameExactIdentityTypeObjectCollision() {
        val position = configurePosition(
            "record Config, record_value : String\n" +
                "class Config\n" +
                "  def self.build\n" +
                "  end\n" +
                "end\n" +
                "Config.<caret>"
        )
        val receiver = CrystalCompletionReceiverResolver.resolve(position) as CompletionReceiver.TypeObject

        assertEquals("Config", receiver.simpleName)
        assertEquals("Config", receiver.qualifiedName)
        assertFalse(receiver.explicitIdentity)
        assertNotNull("Record must win at the shared exact identity", receiver.recordDefinition)
        assertTrue(receiver.recordDefinition!!.text.startsWith("record Config"))
    }

    fun testNearerNestedRecordTypeObjectWinsOverGlobalClass() {
        val position = configurePosition(
            "class Config\n" +
                "end\n" +
                "module Other\n" +
                "  record Config, record_value : Int32\n" +
                "  Config.<caret>\n" +
                "end"
        )
        val receiver = CrystalCompletionReceiverResolver.resolve(position) as CompletionReceiver.TypeObject

        assertEquals("Config", receiver.simpleName)
        assertEquals("Other::Config", receiver.qualifiedName)
        assertFalse(receiver.explicitIdentity)
        assertNotNull("Nearer nested record must win over the global class", receiver.recordDefinition)
        assertTrue(receiver.recordDefinition!!.text.startsWith("record Config"))
    }

    fun testNearerNestedClassTypeObjectWinsOverGlobalRecord() {
        assertReceiver(
            CompletionReceiver.TypeObject("Config", "Other::Config", explicitIdentity = false),
            "record Config, record_value : Int32\n" +
                "module Other\n" +
                "  class Config\n" +
                "  end\n" +
                "  Config.<caret>\n" +
                "end"
        )
    }

    fun testRejectsNestedOnlySimpleTypeObjectAtTopLevel() {
        assertReceiver(
            CompletionReceiver.Unknown,
            "module Outer\n  class Item\n  end\nend\nItem.<caret>"
        )
    }

    fun testResolvesLocalsTypedParametersAndInstanceVariables() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("String")),
            "value = \"text\"\nvalue.<caret>"
        )
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Foo")),
            "class Foo\nend\ndef use(value : Foo)\n  value.<caret>\nend"
        )
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("String")),
            "class Foo\n  @value = \"text\"\n  def use\n    @value.<caret>\n  end\nend"
        )
    }

    fun testResolvesDirectAndGroupedScalarLiterals() {
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Int32")), "3.<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Int32")), "(3).<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("String")), "\"text\".<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("String")), "(\"text\").<caret>")
    }

    fun testResolvesCollectionAndOperatorExpressions() {
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Array")), "[1, \"text\"].<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Hash")), "{\"key\" => 1}.<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Tuple")), "{1, \"text\"}.<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Int32")), "1 + 2.<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Int32")), "(1 + 2).<caret>")
    }

    fun testDirectOperatorBaseDoesNotApplyCompletedCallTwice() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Second")),
            "struct Int32\n" +
                "  def to_second : Second\n    Second.new\n  end\nend\n" +
                "class Second\nend\n" +
                "1 + 2.to_second.<caret>"
        )
    }

    fun testResolvesDirectAndNestedGroupedMethodResults() {
        val declaration = "def value : String\n  \"text\"\nend\n"
        assertReceiver(CompletionReceiver.ValueTypes(listOf("String")), declaration + "value().<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("String")), declaration + "((value())).<caret>")
    }

    fun testResolvesConstructorResultFromCompletePostfixPrefix() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Foo")),
            "class Foo\nend\nFoo.new.<caret>"
        )
    }

    fun testRejectsModuleConstructorResult() {
        assertReceiver(
            CompletionReceiver.Unknown,
            "module Service\nend\nService.new.<caret>"
        )
    }

    fun testResolvesInstanceMethodResultFromCompletePostfixPrefix() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("String")),
            "class Service\n  def value : String\n    \"text\"\n  end\nend\n" +
                "service = Service.new\nservice.value().<caret>"
        )
    }

    fun testInfersCompletedInstanceMethodResultFromBody() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("String")),
            "class Service\n  def value\n    \"text\"\n  end\nend\n" +
                "service = Service.new\nservice.value().<caret>"
        )
    }

    fun testResolvesNestedAndChainedMethodResults() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("String")),
            "class First\n" +
                "  def second : Second\n    Second.new\n  end\nend\n" +
                "class Second\n" +
                "  def value : String\n    \"text\"\n  end\nend\n" +
                "((First.new.second())).value().<caret>"
        )
    }

    fun testResolvesThreeStepArgumentlessPostfixChain() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Third")),
            chainDeclarations() + "First.new.second.third.<caret>"
        )
    }

    fun testResolvesFourStepMixedPostfixChain() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("String")),
            chainDeclarations() + "First.new().second.third().fourth.<caret>"
        )
    }

    fun testRejectsUnsupportedPostfixTail() {
        assertReceiver(
            CompletionReceiver.Unknown,
            chainDeclarations() + "First.new.second[0].<caret>"
        )
    }

    fun testRejectsAmbiguousCompletedCallOverloads() {
        assertReceiver(
            CompletionReceiver.Unknown,
            "class Service\n" +
                "  def value(input : Int32) : String\n    \"text\"\n  end\n" +
                "  def value(input : String) : Int32\n    1\n  end\nend\n" +
                "service = Service.new\nservice.value(1).<caret>"
        )
    }

    fun testResolvesSingleCompletedCallCandidate() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("String")),
            "class Service\n" +
                "  def value(input : Int32) : String\n    \"text\"\n  end\nend\n" +
                "service = Service.new\nservice.value(1).<caret>"
        )
    }

    fun testResolvesQualifiedGenericTypeObject() {
        assertReceiver(
            CompletionReceiver.TypeObject("Box", "Outer::Box", explicitIdentity = true),
            "module Outer\n  class Box\n  end\nend\nOuter::Box(Int32).<caret>"
        )
    }

    fun testPreservesTypedParameterUnionOrder() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Foo", "Bar")),
            "class Foo\nend\nclass Bar\nend\n" +
                "def use(value : Foo | Bar)\n  value.<caret>\nend"
        )
    }

    fun testPreservesExternalInternalTypedParameterUnion() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Foo", "Bar")),
            "class Foo\nend\nclass Bar\nend\n" +
                "def use(external internal : Foo | Bar)\n  internal.<caret>\nend"
        )
    }

    fun testPreservesExplicitMethodReturnUnionOrder() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Foo", "Bar")),
            "class Foo\nend\nclass Bar\nend\n" +
                "def value : Foo | Bar\n  Foo.new\nend\nvalue().<caret>"
        )
    }

    fun testResolvesIfCaseAndTernaryResultTypeSetsInSourceOrder() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Int32", "String")),
            "(if true\n  1\nelse\n  \"text\"\nend).<caret>"
        )
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Int32", "String")),
            "(case 1\nwhen 1\n  1\nelse\n  \"text\"\nend).<caret>"
        )
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Int32", "String")),
            "(true ? 1 : \"text\").<caret>"
        )
    }

    fun testNormalizesOnlyTopLevelUnionAndOuterGenericBase() {
        assertEquals(listOf("Foo", "Bar"), normalizeLookupTypes("Foo | Bar"))
        assertEquals(listOf("Foo", "Bar"), normalizeLookupTypes("(Foo | Bar)"))
        assertEquals(listOf("Foo", "Bar"), normalizeLookupTypes("((Foo | Bar))"))
        assertEquals(listOf("Foo", "Bar", "Baz"), normalizeLookupTypes("Foo | (Bar | Baz)"))
        assertEquals(listOf("Array"), normalizeLookupTypes("Array(Foo | Bar)"))
        assertEquals(listOf("Array"), normalizeLookupTypes("Array(Int32 | String)"))
        assertEquals(listOf("Hash"), normalizeLookupTypes("Hash(String, Array(Int32 | Nil))"))
        assertEquals(emptyList<String>(), normalizeLookupTypes("()"))
    }

    fun testLocatesReceiversAcrossGroupingWhitespaceAndNewlines() {
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Int32")), "3   .<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("Int32")), "(3)\n  .<caret>")

        val declaration = "def value : String\n  \"text\"\nend\n"
        assertReceiver(CompletionReceiver.ValueTypes(listOf("String")), declaration + "value() .<caret>")
        assertReceiver(CompletionReceiver.ValueTypes(listOf("String")), declaration + "((value()))\n.<caret>")
    }

    fun testRejectsUnknownMalformedMacroAndAssignmentReceivers() {
        assertReceiver(CompletionReceiver.Unknown, "missing.<caret>")
        assertReceiver(CompletionReceiver.Unknown, "((3).<caret>")
        assertReceiver(CompletionReceiver.Unknown, "{{ Foo }}.<caret>")
        assertReceiver(CompletionReceiver.Unknown, "(value = 1).<caret>")
    }

    fun testAllowsAssignmentsInsideIfCaseAndTernaryReceivers() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Int32", "String")),
            "(if condition = true\n  1\nelse\n  \"text\"\nend).<caret>"
        )
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Int32", "String")),
            "(case 1\nwhen 1\n  subject = 1\n  1\nelse\n  \"text\"\nend).<caret>"
        )
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Int32", "String")),
            "((condition = true) ? 1 : \"text\").<caret>"
        )
    }

    fun testResolvesAssignmentValuedIfBranch() {
        assertReceiver(
            CompletionReceiver.ValueTypes(listOf("Int32", "String")),
            "(if true\n  value = 1\nelse\n  \"text\"\nend).<caret>"
        )
    }

    fun testRejectsIfWithUnknownReachableBranch() {
        assertReceiver(
            CompletionReceiver.Unknown,
            "(if true\n  missing\nelse\n  \"text\"\nend).<caret>"
        )
    }

    fun testRejectsCaseWithUnknownReachableBranch() {
        assertReceiver(
            CompletionReceiver.Unknown,
            "(case 1\nwhen 1\n  missing\nelse\n  \"text\"\nend).<caret>"
        )
    }

    fun testRejectsTernaryWithUnknownReachableBranch() {
        assertReceiver(
            CompletionReceiver.Unknown,
            "(true ? missing : \"text\").<caret>"
        )
    }

    fun testRejectsCompletionInsideFloatTokenWithoutMemberAccessDot() {
        assertReceiver(CompletionReceiver.Unknown, "3.1<caret>")
    }

    private fun assertReceiver(expected: CompletionReceiver, source: String) {
        val position = configurePosition(source)
        assertEquals(expected, CrystalCompletionReceiverResolver.resolve(position))
    }

    private fun configurePosition(source: String): PsiElement {
        val completionSource = source.replace("<caret>", "<caret>completion_target")
        val file = myFixture.configureByText("test.cr", completionSource)
        return file.findElementAt(myFixture.caretOffset) ?: error("No PSI element at completion offset")
    }

    private fun chainDeclarations(): String =
        "class First\n  def second : Second\n    Second.new\n  end\nend\n" +
            "class Second\n  def third : Third\n    Third.new\n  end\nend\n" +
            "class Third\n  def fourth : String\n    \"text\"\n  end\nend\n"
}
