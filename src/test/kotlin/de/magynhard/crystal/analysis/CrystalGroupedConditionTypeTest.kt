package de.magynhard.crystal.analysis

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalVariableReference
import de.magynhard.crystal.documentation.CrystalDocumentationProvider

class CrystalGroupedConditionTypeTest : BasePlatformTestCase() {
    private fun assertTypes(source: String, vararg expected: String) {
        val file = myFixture.configureByText("query.cr", source.trimIndent())
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
        val reference = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.caretOffset), CrystalVariableReference::class.java, false)!!
        val result = CrystalTypeSetResolver.session(reference).resolve(reference)
        if (expected.isEmpty()) assertEquals(CrystalTypeResolution.Unknown, result)
        else assertEquals(expected.toList(), (result as? CrystalTypeResolution.Known)?.types?.map { it.name })
    }

    fun testGroupedNilableAssignmentIsTruthyInLaterAndOperand() = assertTypes("""
        def candidate : String?
          nil
        end
        def query(flag)
          if flag && (base_name = candidate) && <caret>base_name != "Proc"
            puts base_name
          end
        end
    """, "String")

    fun testGroupedAssignmentHoverUsesTheRefinedReadSite() {
        assertTypes("""
            def candidate : String?
              nil
            end
            def query(flag)
              if flag && (base_name = candidate) && <caret>base_name != "Proc"
                puts base_name
              end
            end
        """, "String")
        val position = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = CrystalDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, position, myFixture.caretOffset)!!
        val doc = provider.generateDoc(target, position).orEmpty()
        assertTrue(doc, doc.contains("String"))
        assertFalse(doc, doc.contains("Nil"))
        assertFalse(doc, doc.contains("Any"))
    }

    fun testUnrelatedDestructuringPreservesParameterType() = assertTypes("""
        def query(type_name : String, specialization)
          generic_type, mapping = specialization
          <caret>type_name
        end
    """, "String")

    fun testUnrelatedDestructuringStillEvaluatesAssignmentValuesInOrder() = assertTypes("""
        def query
          value = 1
          first, second = (value = "updated"), value
          <caret>value
        end
    """, "String")

    fun testGroupedTruthinessDoesNotLeakPastAndExpression() = assertTypes("""
        def candidate : String?
          nil
        end
        def query
          (base_name = candidate) && base_name
          <caret>base_name
        end
    """, "String", "Nil")

    fun testReassignmentInLaterAndOperandReplacesTheNarrowedType() = assertTypes("""
        def candidate : String?
          nil
        end
        def query
          (base_name = candidate) && (base_name = 1) && <caret>base_name
        end
    """, "Int32")

    fun testGroupedAssignmentDoesNotGainPositiveTruthinessAcrossOr() = assertTypes("""
        def candidate : String?
          nil
        end
        def query
          (base_name = candidate) || <caret>base_name
        end
    """, "String", "Nil")

    fun testRenamedGenericParametersFlowThroughIncludesAndSuperclass() = assertTypes("""
        module Entries(Element)
          def first? : Element?
          end
        end
        class Parent(Item)
          include Entries(Item)
        end
        class Child(Value) < Parent(Array(Value))
        end
        def query(values : Child(String))
          item = values.first?
          <caret>item
        end
    """, "Array(String)", "Nil")

    fun testMethodTypeParametersDoNotCaptureOwnerTypeParameters() = assertTypes("""
        class Box(T)
          def value : T forall T
          end
        end
        def query(box : Box(String))
          value = box.value
          <caret>value
        end
    """)

    fun testConflictingGenericPathsDoNotChooseOneElementType() = assertTypes("""
        module Entries(T)
          def first? : T?
          end
        end
        module Left(T)
          include Entries(T)
        end
        module Right(T)
          include Entries(Int32)
        end
        class Collection(T)
          include Left(T)
          include Right(T)
        end
        def query(values : Collection(String))
          value = values.first?
          <caret>value
        end
    """)

    fun testBlockPassKeepsBlockOverloadsInTheReturnPool() = assertTypes("""
        class Source
          def read : String
          end
          def read(&block : String -> _) : Int32
          end
        end
        def query(source : Source, block)
          value = source.read(&block)
          <caret>value
        end
    """)

    fun testLiteralBlockKeepsBlockOverloadsInTheReturnPool() = assertTypes("""
        class Source
          def read : String
          end
          def read(&block : String -> _) : Int32
          end
        end
        def query(source : Source)
          value = source.read { |text| text }
          <caret>value
        end
    """)

    fun testNilableBlockParameterIsNotRequired() = assertTypes("""
        class Source
          def read : String
          end
          def read(&block : (String -> _)?) : Int32
          end
        end
        def query(source : Source)
          value = source.read
          <caret>value
        end
    """)

    private fun splitSource(body: String) = """
        module Enumerable(T)
          def first? : T?
            nil
          end
        end
        class Array(T)
          include Enumerable(T)
        end
        class String
          def split(separator : Char) : Array(String)
          end
          def split(separator : Char, &block : String -> _)
          end
        end
        def query(type_name : String, flag)
          $body
        end
    """

    fun testSplitAndFirstRetainElementType() = assertTypes(splitSource("""
        value = type_name.split('(').first?
        <caret>value
    """), "String", "Nil")

    fun testSplitElementIsStringAfterGroupedAndAssignment() = assertTypes(splitSource("""
        first, second = flag
        if flag && (base_name = type_name.split('(').first?) && <caret>base_name != type_name
          puts base_name
        end
    """), "String")

    fun testSplitAssignmentTargetShowsItsNullableAssignedValue() {
        assertTypes(splitSource("""
            if flag && (<caret>base_name = type_name.split('(').first?) && base_name != type_name
              puts base_name
            end
        """), "String", "Nil")
        assertCaretHover("String", "Nil")
    }

    fun testSplitAssignmentIsStringInTheSuccessfulIfBody() {
        assertTypes(splitSource("""
            if flag && (base_name = type_name.split('(').first?) && base_name != type_name
              puts <caret>base_name
            end
        """), "String")
        assertCaretHover("String")
    }

    fun testSuccessfulAndBranchStillHonorsReassignment() = assertTypes(splitSource("""
        if flag && (base_name = type_name.split('(').first?) && base_name != type_name
          base_name = 1
          puts <caret>base_name
        end
    """), "Int32")

    fun testAndElseBranchDoesNotAssumeEveryOperandWasTrue() = assertTypes(splitSource("""
        base_name = type_name.split('(').first?
        if flag && base_name
          puts base_name
        else
          puts <caret>base_name
        end
    """), "String", "Nil")

    fun testMixedLogicalBranchDoesNotAssumeTheAssignmentWasTruthy() = assertTypes(splitSource("""
        base_name = type_name.split('(').first?
        if flag && base_name || true
          puts <caret>base_name
        end
    """), "String", "Nil")

    fun testSuccessfulAndBranchDoesNotOverrideBlockParameterShadowing() = assertTypes(splitSource("""
        if flag && (base_name = type_name.split('(').first?)
          values.each do |base_name|
            puts <caret>base_name
          end
        end
    """))

    fun testGroupedAssignmentRightHandSideDoesNotSeeItsOwnNewValue() = assertTypes(splitSource("""
        if flag && (base_name = <caret>base_name)
          puts base_name
        end
    """))

    private fun assertCaretHover(vararg expectedTypes: String) {
        val position = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = CrystalDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, position, myFixture.caretOffset)!!
        val doc = provider.generateDoc(target, position).orEmpty()
        for (type in expectedTypes) assertTrue(doc, doc.contains(type))
        if ("Nil" !in expectedTypes) assertFalse(doc, doc.contains("Nil"))
        assertFalse(doc, doc.contains("Any"))
        assertFalse(doc, doc.contains("Unknown"))
    }
}
