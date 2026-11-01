package de.magynhard.crystal.documentation

import com.intellij.psi.PsiManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.psi.CrystalPsiUtils

class CrystalConstructorDocumentationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
    }

    private fun documentation(source: String): String {
        myFixture.configureByText("test.cr", source.trimIndent())
        val provider = CrystalDocumentationProvider()
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        return provider.generateDoc(target, leaf).orEmpty()
    }

    private fun assertConstructor(doc: String, owner: String, signature: String) {
        val plain = plainText(doc)
        assertTrue(doc, plain.contains(owner) && plain.contains(signature))
        assertTrue(doc, plain.contains("Constructor"))
        assertFalse(doc, plain.contains("Any") || plain.contains("(Variable)") || plain.contains("Cannot find"))
    }

    private fun plainText(doc: String): String = StringUtil.unescapeXmlEntities(
        doc.replace(Regex("<[^>]+>"), "").replace("&#32;", " "),
    )

    fun testImplicitStructConstructor() {
        val doc = documentation("""
            module Model
              struct Point
              end
            end
            Model::Point.ne<caret>w
        """)
        assertConstructor(doc, "Model::Point", "new() : Model::Point")
    }

    fun testStructInitializerBecomesNewSignature() {
        val doc = documentation("""
            struct Point
              def initialize(x : Int32, y : Int32 = 0)
              end
            end
            Point.ne<caret>w(1)
        """)
        assertConstructor(doc, "Point", "new(x : Int32, y : Int32 = 0) : Point")
    }

    fun testAllStructConstructorOverloadsArePresented() {
        val doc = documentation("""
            struct Point
              def initialize(x : Int32)
              end
              def initialize(label : String)
              end
            end
            Point.ne<caret>w(1)
        """)
        assertConstructor(doc, "Point", "new(x : Int32) : Point")
        assertConstructor(doc, "Point", "new(label : String) : Point")
    }

    fun testExplicitNewKeepsItsActualReturnAnnotation() {
        val doc = documentation("""
            struct Factory
              def self.new(value : String) : String
                value
              end
            end
            Factory.ne<caret>w("item")
        """)
        assertConstructor(doc, "Factory", "new(value : String) : String")
        assertFalse(doc, plainText(doc).contains("new(value : String) : Factory"))
    }

    fun testRecordConstructor() {
        val doc = documentation("""
            record Point, x : Int32, y : Int32 = 0
            Point.ne<caret>w(1)
        """)
        assertConstructor(doc, "Point", "new(x : Int32, y : Int32 = 0) : Point")
    }

    fun testClassConstructorUsesSamePresentation() {
        val doc = documentation("""
            class Box
            end
            Box.ne<caret>w
        """)
        assertConstructor(doc, "Box", "new() : Box")
    }

    fun testGenericStructPreservesConstructedTypeArguments() {
        val doc = documentation("""
            struct Box(T)
              def initialize(value : T)
              end
            end
            Box(Int32).ne<caret>w(1)
        """)
        assertConstructor(doc, "Box(Int32)", "new(value : T) : Box(Int32)")
    }

    fun testInstanceMethodNamedNewKeepsOrdinaryMethodDocumentation() {
        val doc = documentation("""
            class Factory
              def new : String
                "item"
              end
            end
            factory = Factory.new
            factory.ne<caret>w
        """)
        assertTrue(doc, plainText(doc).contains("new() : String"))
        assertFalse(doc, doc.contains("Constructor"))
    }

    fun testMacroGeneratedStructConstructorShowsOwnerWithoutGuessingSignature() {
        myFixture.addFileToProject("types.cr", """
            module LSP
              module Initializer
                macro included
                  {% verbatim do %}
                  def self.new(**args)
                    instance = self.allocate
                    instance.initialize(args)
                    instance
                  end
                  {% end %}
                end
              end
              struct CompletionItem
                include Initializer
                property label : String
              end
            end
        """.trimIndent())
        val doc = documentation("""
            require "./types"
            LSP::CompletionItem.ne<caret>w(label: "item")
        """)
        assertConstructor(doc, "LSP::CompletionItem", "new(…)")
        assertTrue(doc, doc.contains("not statically available"))
        assertFalse(doc, plainText(doc).contains("new()"))
        val provider = CrystalDocumentationProvider()
        val target = provider.getDocumentationElementForLink(
            PsiManager.getInstance(project), "class:::LSP::CompletionItem", myFixture.file,
        )
        assertNotNull(target)
        assertEquals("LSP::CompletionItem", CrystalPsiUtils.buildQualifiedName(target!!))
    }

    fun testUnknownQualifiedReceiverDoesNotBorrowStruct() {
        val doc = documentation("""
            module Other
              struct Point
              end
            end
            Missing::Point.ne<caret>w
        """)
        assertTrue(doc, doc.isEmpty())
    }

    fun testUnrequiredStructDoesNotSupplyConstructorHover() {
        myFixture.addFileToProject("hidden.cr", "struct Point\nend\n")
        assertTrue(documentation("Point.ne<caret>w").isEmpty())
        assertNull(CrystalDocumentationProvider().getDocumentationElementForLink(
            PsiManager.getInstance(project), "class:::Point", myFixture.file,
        ))
    }

    fun testConstructorArgumentsKeepTheirOwnHover() {
        val doc = documentation("""
            struct Point
              def initialize(value : Int32)
              end
            end
            def build(value : Int32)
              Point.new(va<caret>lue)
            end
        """)
        assertTrue(doc, doc.contains("Int32") && doc.contains("Parameter"))
        assertFalse(doc, doc.contains("Constructor"))
    }
}
