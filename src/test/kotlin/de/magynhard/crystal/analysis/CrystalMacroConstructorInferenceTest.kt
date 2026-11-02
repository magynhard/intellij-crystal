package de.magynhard.crystal.analysis

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
import de.magynhard.crystal.inspections.CrystalDotCallTargetResolver
import de.magynhard.crystal.inspections.DotCallResolution
import de.magynhard.crystal.psi.CrystalDotCallAccess

class CrystalMacroConstructorInferenceTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.copyFileToProject("analysis/constructor_initializer.cr", "initializer.cr")
        myFixture.addFileToProject("types.cr", """
            require "./initializer"
            class String
            end
            module LSP
              module Serializable
                macro included
                  def self.new(pull : String)
                    new_from_json_pull_parser(pull)
                  end
                  private def self.new_from_json_pull_parser(pull : String)
                    instance = allocate
                    instance.initialize(pull: pull)
                    finalize(instance) if instance.responds_to?(:finalize)
                    instance
                  end
                end
              end
              struct CompletionItem
                include Initializer
                include Serializable
                property label : String
              end
            end
        """.trimIndent())
    }

    private fun configure(expression: String) {
        myFixture.configureByText("completion.cr", "require \"./types\"\n$expression")
    }

    fun testAssignedMacroConstructorResultHasExactType() {
        configure("item = LSP::CompletionItem.new(label: \"item\")\nit<caret>em")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals("LSP::CompletionItem", CrystalTypeSetResolver.session(leaf).resolveVariable("item", leaf).render())
    }

    fun testNewHoverShowsProvenReturnTypeAndGeneratedSignature() {
        configure("LSP::CompletionItem.ne<caret>w(label: \"item\")")
        val doc = documentation()
        assertTrue(doc, doc.contains("new(**args) : LSP::CompletionItem"))
        assertTrue(doc, doc.contains("new(pull : String) : LSP::CompletionItem"))
        assertFalse(doc, doc.contains("not statically available") || doc.contains("(Variable)"))
    }

    fun testVariableHoverShowsConstructedType() {
        configure("item = LSP::CompletionItem.new(label: \"item\")\nit<caret>em")
        val doc = documentation()
        assertTrue(doc, doc.contains("LSP::CompletionItem") && doc.contains("(Variable)"))
        assertFalse(doc, doc.contains("Unknown") || doc.contains("Any"))
    }

    fun testCompletionAfterConstructorOffersStructMembers() {
        configure("LSP::CompletionItem.new(label: \"item\").<caret>")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue(names.toString(), "label" in names)
    }

    fun testTypeObjectCompletionOffersGeneratedNew() {
        configure("LSP::CompletionItem.<caret>")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue(names.toString(), "new" in names)
    }

    fun testChainedReaderInfersItsReturnType() {
        configure("text = LSP::CompletionItem.new(label: \"item\").label\nte<caret>xt")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals("String", CrystalTypeSetResolver.session(leaf).resolveVariable("text", leaf).render())
    }

    fun testChainedReaderResolvesToItsAccessorDeclaration() {
        configure("LSP::CompletionItem.new(label: \"item\").la<caret>bel")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val access = PsiTreeUtil.getParentOfType(leaf, CrystalDotCallAccess::class.java)!!
        assertTrue(CrystalDotCallTargetResolver.resolve(access) is DotCallResolution.Accessor)
        val doc = documentation()
        assertTrue(doc, doc.contains("label() : String"))
    }

    fun testOverriddenAllocationDoesNotProveSelfResult() {
        configure("""
            struct LSP::CompletionItem
              def self.allocate : String
                "not an item"
              end
            end
            item = LSP::CompletionItem.new(label: "item")
            it<caret>em
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals(CrystalTypeResolution.Unknown, CrystalTypeSetResolver.session(leaf).resolveVariable("item", leaf))
    }

    fun testUnknownFactoryResultDoesNotFallBackToItsOwnersMembers() {
        configure("""
            struct Factory
              def self.new
                unknown_value
              end
              property label : String
            end
            item = Factory.new
            item.la<caret>bel
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val access = PsiTreeUtil.getParentOfType(leaf, CrystalDotCallAccess::class.java)!!
        assertTrue(CrystalDotCallTargetResolver.resolve(access) is DotCallResolution.Suppressed)
    }

    fun testUnrequiredHookDoesNotSupplyConstructorType() {
        myFixture.configureByText("test.cr", "item = LSP::CompletionItem.new(label: \"item\")\nit<caret>em")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals(CrystalTypeResolution.Unknown, CrystalTypeSetResolver.session(leaf).resolveVariable("item", leaf))
    }

    fun testOpaqueConditionalHookStaysUnknown() {
        myFixture.addFileToProject("conditional.cr", """
            module Conditional
              macro included
                {% if flag?(:special) %}
                  def self.new(**args)
                    instance = allocate
                    instance
                  end
                {% end %}
              end
            end
            struct Entry
              include Conditional
            end
        """.trimIndent())
        myFixture.configureByText("test.cr", "require \"./conditional\"\nentry = Entry.new\nen<caret>try")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals(CrystalTypeResolution.Unknown, CrystalTypeSetResolver.session(leaf).resolveVariable("entry", leaf))
    }

    fun testExplicitFactoryReturnsItsActualType() {
        configure("""
            struct Factory
              def self.new : String
                "item"
              end
            end
            text = Factory.new
            te<caret>xt
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals("String", CrystalTypeSetResolver.session(leaf).resolveVariable("text", leaf).render())
    }

    fun testGenericMacroConstructorKeepsItsTypeArguments() {
        assertResult("""
            struct Box(T)
              include LSP::Initializer
            end
            result = Box(Int32).new(value: 1)
            res<caret>ult
        """, "Box(Int32)")
    }

    fun testRelativeReturnAnnotationUsesTheExpandedOwnerNamespace() {
        assertResult("""
            module FactoryTrait
              macro included
                def self.new : Result
                  Result.new
                end
              end
            end
            module First
              struct Result
              end
              struct Entry
                include FactoryTrait
              end
            end
            module Other
              struct Result
              end
              def build
                result = First::Entry.new
                res<caret>ult
              end
            end
        """, "First::Result")
    }

    fun testAllocationParameterIsNotTreatedAsTheBuiltin() {
        assertResult("""
            module FactoryTrait
              macro included
                def self.new(allocate : String)
                  instance = allocate
                  instance
                end
              end
            end
            struct Entry
              include FactoryTrait
            end
            result = Entry.new("value")
            res<caret>ult
        """, null)
    }

    fun testGroupedReassignmentInvalidatesAllocationProof() {
        assertResult("""
            module FactoryTrait
              macro included
                def self.new
                  instance = allocate
                  consume((instance = "other"))
                  instance
                end
              end
            end
            struct Entry
              include FactoryTrait
            end
            result = Entry.new
            res<caret>ult
        """, null)
    }

    fun testAdditionalBareMacroInputStaysUnknown() {
        assertResult("""
            struct Entry
              include LSP::Initializer
              macro replace_constructor
                def self.new
                  nil
                end
              end
              replace_constructor
            end
            result = Entry.new
            res<caret>ult
        """, null)
    }

    fun testNestedInheritedHookIsNotReplayedAsItsOriginalIncludedHook() {
        assertResult("""
            module FactoryTrait
              macro included
                def self.new
                  instance = allocate
                  instance
                end
                macro inherited
                  def self.new : String
                    "other"
                  end
                end
              end
            end
            class Parent
              include FactoryTrait
            end
            class Child < Parent
            end
            result = Child.new
            res<caret>ult
        """, null)
    }

    fun testConstructorOfAnotherExplicitReceiverDoesNotBindToIncluder() {
        assertResult("""
            struct Other
            end
            module FactoryTrait
              macro included
                def Other.new
                  instance = self.allocate
                  instance
                end
              end
            end
            struct Entry
              include FactoryTrait
            end
            result = Entry.new
            res<caret>ult
        """, null)
    }

    fun testEditingHookInvalidatesItsCachedReturnEvidence() {
        val trait = myFixture.addFileToProject("mutable.cr", """
            module MutableFactory
              macro included
                def self.new
                  instance = allocate
                  instance
                end
              end
            end
            struct Entry
              include MutableFactory
            end
        """.trimIndent())
        configure("require \"./mutable\"\nresult = Entry.new\nres<caret>ult")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals("Entry", CrystalTypeSetResolver.session(leaf).resolveVariable("result", leaf).render())
        WriteCommandAction.runWriteCommandAction(project) {
            val manager = PsiDocumentManager.getInstance(project)
            val document = manager.getDocument(trait)!!
            document.setText(trait.text.replace("instance = allocate\n      instance", "\"item\""))
            manager.commitDocument(document)
        }
        assertEquals("String", CrystalTypeSetResolver.session(leaf).resolveVariable("result", leaf).render())
    }

    private fun assertResult(source: String, expected: String?) {
        configure(source.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals(expected, CrystalTypeSetResolver.session(leaf).resolveVariable("result", leaf).render())
    }

    private fun documentation(): String {
        val provider = CrystalDocumentationProvider()
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        return StringUtil.unescapeXmlEntities(provider.generateDoc(target, leaf).orEmpty()
            .replace(Regex("<[^>]+>"), "").replace("&#32;", " "))
    }
}
