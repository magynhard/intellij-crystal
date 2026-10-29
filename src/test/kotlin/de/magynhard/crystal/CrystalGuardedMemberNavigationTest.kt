package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.analysis.CrystalTypeResolution
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.documentation.CrystalDocumentationProvider

class CrystalGuardedMemberNavigationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.addFileToProject("errors.cr", """
            class Object
            end
            module Crystal
              module ErrorFormat
              end
              class TypeException < Object
                include ErrorFormat
                property inner : TypeException?
              end
            end
        """.trimIndent())
    }

    private fun targets(body: String, requires: String = ""): List<PsiElement> {
        myFixture.configureByText("diagnostics.cr", """
            require "./errors"
            $requires
            def read(error : Crystal::ErrorFormat, replacement)
              $body
            end
        """.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val call = PsiTreeUtil.getParentOfType(leaf, CrystalDotCallAccess::class.java)!!
        assertNull("Navigation candidates must not become exact reference resolution", call.reference!!.resolve())
        return CrystalGotoDeclarationHandler().getGotoDeclarationTargets(leaf, myFixture.caretOffset, myFixture.editor)
            .orEmpty().toList()
    }

    private fun owners(elements: List<PsiElement>): Set<String?> = elements.map {
        CrystalPsiUtils.getEnclosingType(it)?.let(CrystalPsiUtils::buildQualifiedName)
    }.toSet()

    fun testGuardedInnerOffersTheCompatiblePropertyDeclaration() {
        val result = targets("if error.responds_to?(:inner)\nerror.in<caret>ner\nend")
        assertEquals(1, result.size)
        assertEquals(setOf("Crystal::TypeException"), owners(result))
        assertEquals("errors.cr", result.single().containingFile.name)
        assertEquals("inner : TypeException?", result.single().text)
        val call = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), CrystalDotCallAccess::class.java)!!
        val reference = call.reference as PsiPolyVariantReference
        assertTrue(reference.multiResolve(false).all { !it.isValidResult })
        assertFalse("A possible target is not a certified usage/rename binding", reference.isReferenceTo(result.single()))
        assertTrue(CrystalTypeSetResolver.session(call).resolveCall(
            listOf("Crystal::ErrorFormat"), "inner", false, call,
        ) is CrystalTypeResolution.Unknown)
    }

    fun testMultipleCompatibleImplementationsAreOfferedTogether() {
        myFixture.addFileToProject("second.cr", """
            class SecondError < Object
              include Crystal::ErrorFormat
              def inner(value = nil)
              end
            end
        """.trimIndent())
        val result = targets("if error.responds_to? :inner\nerror.in<caret>ner\nend", "require \"./second\"")
        assertEquals(setOf("Crystal::TypeException", "SecondError"), owners(result))
        assertEquals(2, result.size)
    }

    fun testWrongNamespacesAndUnrequiredImplementationsAreExcluded() {
        myFixture.addFileToProject("hidden.cr", "class Hidden < Object\ninclude Crystal::ErrorFormat\ngetter inner : Int32\nend")
        myFixture.addFileToProject("other.cr", """
            module ErrorFormat
            end
            module Other
              module ErrorFormat
              end
            end
            class ForeignError < Object
              include Other::ErrorFormat
              getter inner : Int32
            end
            class Unrelated < Object
              def inner
              end
            end
            module Crystal
              class AbsoluteForeign < Object
                include ::ErrorFormat
                getter inner : Int32
              end
            end
        """.trimIndent())
        val result = targets("if error.responds_to?(:inner)\nerror.in<caret>ner\nend", "require \"./other\"")
        assertEquals(setOf("Crystal::TypeException"), owners(result))
    }

    fun testTransitiveModuleAndSuperclassEdgesAreFollowedAndTargetsDeduplicated() {
        myFixture.addFileToProject("derived.cr", """
            module WrappedFormat
              include Crystal::ErrorFormat
            end
            class WrappedError < Object
              include WrappedFormat
              getter inner : Int32
            end
            class ChildError < Crystal::TypeException
            end
        """.trimIndent())
        val result = targets("if error.responds_to?(:inner)\nerror.in<caret>ner\nend", "require \"./derived\"")
        assertEquals(setOf("Crystal::TypeException", "WrappedError"), owners(result))
        assertEquals("ChildError inherits the same property declaration", 2, result.size)
    }

    fun testIncompleteAndConditionalHierarchiesAreNotNavigationEvidence() {
        myFixture.addFileToProject("uncertain.cr", """
            class Broken < Object
              include Crystal::ErrorFormat
              include MissingMixin
              getter inner : Int32
            end
            class Conditional < Object
              {% if flag?(:feature) %}
                include Crystal::ErrorFormat
              {% end %}
              getter inner : Int32
            end
            module Crystal
              class Generic(ErrorFormat) < Object
                include ErrorFormat
                getter inner : Int32
              end
            end
        """.trimIndent())
        val result = targets("if error.responds_to?(:inner)\nerror.in<caret>ner\nend", "require \"./uncertain\"")
        assertEquals(setOf("Crystal::TypeException"), owners(result))
    }

    fun testMissingGuardOrUnknownCurrentTypeDoesNotFallBackToTheParameterType() {
        for (body in listOf(
            "error.in<caret>ner",
            "error = replacement\nif error.responds_to?(:inner)\nerror.in<caret>ner\nend",
            "if error.responds_to?(:inner)\nerror = replacement\nerror.in<caret>ner\nend",
            "if error.responds_to?(:inner)\n{{ error.in<caret>ner }}\nend",
        )) {
            assertEmpty(targets(body))
        }
    }

    fun testStaticFinishedSubtypeUsesItsSourceBackedDeclarationView() {
        myFixture.addFileToProject("generated.cr", """
            module Crystal
              class ErrorBase < Object
                include ErrorFormat
              end
              macro finished
                class GeneratedError < ErrorBase
                  getter inner : TypeException?
                end
              end
            end
        """.trimIndent())
        val result = targets("if error.responds_to?(:inner)\nerror.in<caret>ner\nend", "require \"./generated\"")
        assertEquals(setOf("Crystal::TypeException", "Crystal::GeneratedError"), owners(result))
        val projected = result.single { CrystalPsiUtils.getEnclosingType(it)?.let(CrystalPsiUtils::buildQualifiedName) == "Crystal::GeneratedError" }
        assertEquals("generated.cr", projected.navigationElement.containingFile.name)
    }

    fun testHoverRecognizesTheGuardedMemberWithoutPretendingExactDispatch() {
        targets("if error.responds_to?(:inner)\nerror.in<caret>ner\nend")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = CrystalDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        val doc = provider.generateDoc(target, leaf).orEmpty()
        assertTrue(doc.contains("guarded member"))
        assertTrue(doc.contains("responds_to?"))
        assertTrue(doc.contains("Known compatible declarations"))
        assertTrue(doc.contains("Crystal::TypeException"))
        assertFalse(doc.contains("Any"))
    }

    fun testBroadSearchDiscardsPartialTargets() {
        myFixture.addFileToProject("many.cr", (1..300).joinToString("\n") {
            "class Candidate$it < Object\ninclude Crystal::ErrorFormat\ngetter inner : Int32\nend"
        })
        assertEmpty(targets("if error.responds_to?(:inner)\nerror.in<caret>ner\nend", "require \"./many\""))
    }
}
