package de.magynhard.crystal

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
import de.magynhard.crystal.inspections.CrystalUnresolvedNameInspection
import de.magynhard.crystal.psi.CrystalStructDefinition
import de.magynhard.crystal.stubs.CrystalIndexService

class CrystalBlockDeclarationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
    }

    private fun addDependency() = myFixture.addFileToProject("completion_item.cr", """
        module LSP
          Enum.number InsertTextFormat do
            PlainText = 1
            Snippet = 2
            def to_json(json)
              json.number(value)
            end
          end
          struct CompletionItem
            property label : String
          end
        end
    """.trimIndent())

    fun testMethodInMacroCallBlockPreservesFollowingDeclaration() {
        val file = addDependency()
        assertEmpty(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java))
        val structs = CrystalIndexService.findTypes("CompletionItem", project, GlobalSearchScope.allScope(project))
        assertEquals(1, structs.size)
    }

    fun testFollowingStructResolvesAndHovers() {
        addDependency()
        myFixture.configureByText("completion.cr", """
            require "./completion_item"
            LSP::Comple<caret>tionItem.new(label: "item")
        """.trimIndent())
        val provider = CrystalDocumentationProvider()
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        assertInstanceOf(target, CrystalStructDefinition::class.java)
        assertEquals("CompletionItem", (target as CrystalStructDefinition).name)
        assertEquals("completion_item.cr", target.containingFile.name)
        val doc = provider.generateDoc(target, leaf).orEmpty()
        assertTrue(doc, doc.contains("CompletionItem") && doc.contains("struct"))
        assertFalse(doc, doc.contains("Cannot find") || doc.contains("Any"))
        myFixture.enableInspections(CrystalUnresolvedNameInspection())
        assertFalse(myFixture.doHighlighting().any { it.description == "Cannot find 'CompletionItem'" })
    }

    fun testMacroBlockMethodDoesNotBecomeAMethodOfLsp() {
        addDependency()
        assertEmpty(CrystalIndexService.findMethods("to_json", project, GlobalSearchScope.allScope(project)))
    }
}
