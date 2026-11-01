package de.magynhard.crystal

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.render
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
import de.magynhard.crystal.inspections.CrystalUnresolvedName
import de.magynhard.crystal.psi.CrystalVariableReference

class CrystalReopenedPredicateAccessorTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.addFileToProject("compiler.cr", """
            class Object
            end
            struct Bool
            end
            module Crystal
              class Compiler
                property? no_cleanup = false
                class_getter? enabled = true
              end
            end
        """.trimIndent())
    }

    private fun configure(body: String = "consume(cleanup: !no_clea<caret>nup?)", owner: String = "Crystal::Compiler", required: Boolean = true) {
        myFixture.configureByText("ext.cr", """
            ${if (required) "require \"./compiler\"" else ""}
            class $owner
              def compile
                $body
              end
            end
        """.trimIndent())
    }

    private fun reference() = PsiTreeUtil.getParentOfType(
        myFixture.file.findElementAt(myFixture.caretOffset)!!, CrystalVariableReference::class.java, false,
    )!!

    fun testBarePredicateResolvesAcrossRequiredReopening() {
        configure()
        val reference = reference()
        val target = reference.reference?.resolve()
        assertNotNull("Expected the property argument in compiler.cr", target)
        assertEquals("compiler.cr", target!!.containingFile.name)
        assertEquals("no_cleanup = false", target.text)
        assertNull(CrystalUnresolvedName.isUnresolvedBareName("no_cleanup?", reference))
    }

    fun testBarePredicateHoverShowsBool() {
        configure()
        assertHover("no_cleanup?", "Bool")
    }

    fun testBarePredicateResultIsBool() {
        configure()
        val reference = reference()
        assertEquals("Bool", CrystalTypeSetResolver.resolve(reference).render())
    }

    fun testInheritedPredicateResolves() {
        configure(owner = "Derived < Crystal::Compiler")
        assertNotNull(reference().reference?.resolve())
        assertHover("no_cleanup?", "Bool")
    }

    fun testClassPredicateUsesStaticMode() {
        configure("ena<caret>bled?")
        assertNull(reference().reference?.resolve())
        myFixture.configureByText("ext.cr", """
            require "./compiler"
            class Crystal::Compiler
              def self.compile
                ena<caret>bled?
              end
            end
        """.trimIndent())
        assertNotNull(reference().reference?.resolve())
        assertHover("enabled?", "Bool")
    }

    fun testInstancePredicateDoesNotResolveInStaticMethod() {
        myFixture.configureByText("ext.cr", """
            require "./compiler"
            class Crystal::Compiler
              def self.compile
                no_clea<caret>nup?
              end
            end
        """.trimIndent())
        assertNull(reference().reference?.resolve())
    }

    fun testUnrelatedOwnerDoesNotSupplyPredicate() {
        configure(owner = "Other::Compiler")
        assertNull(reference().reference?.resolve())
    }

    fun testUnrequiredReopeningDoesNotSupplyPredicate() {
        configure(required = false)
        assertNull(reference().reference?.resolve())
    }

    fun testUnknownInitializerDoesNotImplyBool() {
        myFixture.addFileToProject("other.cr", """
            require "./compiler"
            class Crystal::Compiler
              getter? uncertain = fetch_value
            end
        """.trimIndent())
        myFixture.configureByText("ext.cr", """
            require "./other"
            class Crystal::Compiler
              def compile
                uncer<caret>tain?
              end
            end
        """.trimIndent())
        val reference = reference()
        assertNotNull(reference.reference?.resolve())
        assertEquals(CrystalTypeResolution.Unknown, CrystalTypeSetResolver.resolve(reference))
    }

    fun testDuplicateReopeningsDoNotChooseAnArbitraryProperty() {
        myFixture.addFileToProject("duplicate.cr", """
            require "./compiler"
            class Crystal::Compiler
              property? no_cleanup = true
            end
        """.trimIndent())
        myFixture.configureByText("ext.cr", """
            require "./duplicate"
            class Crystal::Compiler
              def compile
                no_clea<caret>nup?
              end
            end
        """.trimIndent())
        assertNull(reference().reference?.resolve())
    }

    private fun assertHover(name: String, type: String) {
        val provider = CrystalDocumentationProvider()
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        val doc = provider.generateDoc(target, leaf).orEmpty()
        assertTrue(doc, doc.contains(name) && doc.contains(type))
        assertFalse(doc, doc.contains("Any") || doc.contains("Cannot find") || doc.contains("(Variable)"))
    }
}
