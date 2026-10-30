package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.documentation.CrystalDocumentationProvider

class CrystalPredicateAccessorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.addFileToProject("ast.cr", """
            class Object
            end
            module Crystal
              class ASTNode
              end
              class ClassDef < ASTNode
                property? struct : Bool
                getter? label : String?
                class_getter? enabled : Bool
              end
            end
        """.trimIndent())
    }

    private fun visit(expression: String) {
        myFixture.configureByText("semantic_tokens.cr", """
            require "./ast"
            def visit(node : Crystal::ClassDef)
              node.struct? ? "struct" : "class"
              $expression
            end
        """.trimIndent())
    }

    fun testCompletionOffersPredicateReaderAndSetterWithoutPlainReader() {
        visit("node.<caret>")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Expected struct? among $names", "struct?" in names)
        assertTrue("Expected struct= among $names", "struct=" in names)
        assertTrue("Expected label? among $names", "label?" in names)
        assertFalse("struct" in names)
        assertFalse("label" in names)
        assertFalse("enabled?" in names)
    }

    fun testPredicateHoverShowsDeclaredReturnType() {
        visit("node.str<caret>uct?")
        assertHover("struct?", "Bool")
    }

    fun testNilablePredicateHoverRetainsItsType() {
        visit("node.la<caret>bel?")
        assertHover("label?", "String?")
    }

    fun testClassPredicateCompletionUsesStaticReceiver() {
        visit("Crystal::ClassDef.<caret>")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Expected enabled? among $names", "enabled?" in names)
        assertFalse("struct?" in names)
    }

    private fun assertHover(name: String, type: String) {
        val provider = CrystalDocumentationProvider()
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        val doc = provider.generateDoc(target, leaf).orEmpty()
        assertTrue("Expected $name : $type, got $doc", doc.contains(name) && doc.contains(type))
        assertFalse(doc, doc.contains("Any") || doc.contains("Cannot find"))
    }
}
