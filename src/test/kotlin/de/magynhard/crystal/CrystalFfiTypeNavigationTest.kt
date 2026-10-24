package de.magynhard.crystal

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.sdk.CrystalStdlibResolver

class CrystalFfiTypeNavigationTest : BasePlatformTestCase() {
    private fun addLibrary(path: String = "lib_gmp.cr") = myFixture.addFileToProject(path, """
        lib LibGMP
          struct MPF
          end
          alias Precision = UInt64
        end
    """.trimIndent())

    private fun targetAtCaret(): PsiElement? {
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset)
        assertNotNull("JetBrains must find a reference on this path segment", reference)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertEquals(leaf.text, reference!!.canonicalText)
        val target = reference.resolve()
        assertEquals("The normal JetBrains referenced-element path must use the same target", target,
            TargetElementUtil.findTargetElement(myFixture.editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED))
        return target
    }

    fun testCtrlBOnLibraryRootInBigFloatParameter() {
        addLibrary()
        myFixture.configureByText("big_float.cr", """
            require "./lib_gmp"
            struct BigFloat
              def initialize(@mpf : LibG<caret>MP::MPF)
              end
            end
        """.trimIndent())
        val target = targetAtCaret()
        assertTrue(target is CrystalLibDefinition)
        assertEquals("lib_gmp.cr", target!!.containingFile.name)
    }

    fun testCtrlBOnStructMemberInBigFloatParameter() {
        addLibrary()
        myFixture.addFileToProject("other.cr", "lib Other\nstruct MPF\nend\nend")
        myFixture.configureByText("big_float.cr", """
            require "./lib_gmp"
            require "./other"
            struct BigFloat
              def initialize(@mpf : LibGMP::M<caret>PF)
              end
            end
        """.trimIndent())
        val target = targetAtCaret()
        assertTrue(target is CrystalLibStructDefinition)
        assertEquals("LibGMP::MPF", CrystalPsiUtils.buildQualifiedName(target!!))
        assertEquals("lib_gmp.cr", target.containingFile.name)
    }

    fun testCtrlBOnLibraryAliasTargetsDeclarationNotAliasedType() {
        addLibrary()
        myFixture.configureByText("main.cr", "require \"./lib_gmp\"\nvalue : LibGMP::Pre<caret>cision")
        val target = targetAtCaret()
        assertTrue(target is CrystalTypeAliasLib)
        assertEquals("LibGMP::Precision", CrystalPsiUtils.buildQualifiedName(target!!))
    }

    fun testLexicalLibraryMemberResolvesInsideNamespace() {
        myFixture.addFileToProject("lib_gmp.cr", "module Big\nlib LibGMP\nstruct MPF\nend\nend\nend")
        myFixture.configureByText("main.cr", """
            require "./lib_gmp"
            struct Big::BigFloat
              def initialize(@mpf : LibGMP::M<caret>PF)
              end
            end
        """.trimIndent())
        assertEquals("Big::LibGMP::MPF", CrystalPsiUtils.buildQualifiedName(targetAtCaret()!!))
    }

    fun testAbsoluteLibraryPathBypassesLexicalShadow() {
        addLibrary()
        myFixture.addFileToProject("nested.cr", "module Big\nlib LibGMP\nstruct MPF\nend\nend\nend")
        myFixture.configureByText("main.cr", """
            require "./lib_gmp"
            require "./nested"
            struct Big::BigFloat
              def initialize(@mpf : ::LibGMP::M<caret>PF)
              end
            end
        """.trimIndent())
        assertEquals("LibGMP::MPF", CrystalPsiUtils.buildQualifiedName(targetAtCaret()!!))
    }

    fun testUnrequiredLibraryMemberDoesNotNavigate() {
        addLibrary()
        myFixture.configureByText("main.cr", "value : LibGMP::M<caret>PF")
        assertNull(targetAtCaret())
    }

    fun testMissingMemberDoesNotFallBackToOtherLibrary() {
        myFixture.addFileToProject("libs.cr", "lib LibGMP\nend\nlib Other\nstruct MPF\nend\nend")
        myFixture.configureByText("main.cr", "require \"./libs\"\nvalue : LibGMP::M<caret>PF")
        assertNull(targetAtCaret())
    }

    fun testAmbiguousLibraryMemberDoesNotPickArbitraryDeclaration() {
        addLibrary("a.cr")
        addLibrary("b.cr")
        myFixture.configureByText("main.cr", "require \"./a\"\nrequire \"./b\"\nvalue : LibGMP::M<caret>PF")
        assertNull(targetAtCaret())
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset) as PsiPolyVariantReference
        assertEquals("Both exact declarations must remain available to the native target chooser",
            2, reference.multiResolve(false).size)
    }

    fun testOrdinaryQualifiedTypePathKeepsMemberNavigation() {
        myFixture.configureByText("main.cr", "module Outer\nclass Inner\nend\nend\nvalue : Outer::In<caret>ner")
        val target = targetAtCaret()
        assertTrue(target is CrystalClassDefinition)
        assertEquals("Outer::Inner", CrystalPsiUtils.buildQualifiedName(target!!))
    }

    fun testQualifiedLibraryPathHasReferenceOnEverySegment() {
        myFixture.addFileToProject("lib_gmp.cr", "module Big\nlib LibGMP\nstruct MPF\nend\nend\nend")
        myFixture.configureByText("main.cr", "require \"./lib_gmp\"\nvalue : <caret>Big::LibGMP::MPF")
        val start = myFixture.caretOffset
        assertTrue(targetAtCaret() is CrystalModuleDefinition)
        myFixture.editor.caretModel.moveToOffset(start + "Big::".length)
        assertTrue(targetAtCaret() is CrystalLibDefinition)
        myFixture.editor.caretModel.moveToOffset(start + "Big::LibGMP::".length)
        assertTrue(targetAtCaret() is CrystalLibStructDefinition)
    }

    fun testUnionAndOpaqueTypeNavigateToTheirLibraryDeclarations() {
        myFixture.addFileToProject("lib.cr", """
            lib Native
              union Value
                integer : Int32
              end
              type Handle = Void*
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "require \"./lib\"\nvalue : Native::<caret>Value\nhandle : Native::Handle")
        assertTrue(targetAtCaret() is CrystalLibUnionDefinition)
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("Native::Handle") + "Native::".length)
        assertTrue(targetAtCaret() is CrystalLibTypeAlias)
    }

    fun testBareStructTypeInsideLibraryUsesItsOwnNamespace() {
        myFixture.configureByText("main.cr", "lib Native\nstruct Value\nend\nfun receive(value : <caret>Value)\nend")
        assertTrue(targetAtCaret() is CrystalLibStructDefinition)
    }

    fun testUnrequiredLibraryRootDoesNotNavigate() {
        addLibrary()
        myFixture.configureByText("main.cr", "value : LibG<caret>MP::MPF")
        assertNull(targetAtCaret())
    }

    fun testMissingLibraryRootDoesNotNavigateToUnrelatedMember() {
        myFixture.addFileToProject("other.cr", "lib Other\nstruct MPF\nend\nend")
        myFixture.configureByText("main.cr", "require \"./other\"\nvalue : LibGMP::M<caret>PF")
        assertNull(targetAtCaret())
    }

    fun testConstantTypeArgumentKeepsNavigation() {
        myFixture.configureByText("main.cr", "SIZE = 4\nvalue : StaticArray(Int32, SI<caret>ZE)")
        assertTrue(targetAtCaret() is CrystalConstantAssignment)
    }

    fun testLibraryEnumRetainsQualifiedIndexIdentity() {
        myFixture.addFileToProject("native.cr", "lib Native\nenum Kind\nValue\nend\nend")
        myFixture.configureByText("main.cr", "require \"./native\"\nvalue : Native::K<caret>ind")
        val target = targetAtCaret()
        assertTrue(target is CrystalEnumDefinition)
        assertEquals("Native::Kind", CrystalPsiUtils.buildQualifiedName(target!!))
        val nested = CrystalIndexService.findNestedTypes("Native", project, GlobalSearchScope.allScope(project))
        assertTrue("Library-owned enums must be indexed under the library, not the global namespace",
            nested.any { CrystalPsiUtils.buildQualifiedName(it) == "Native::Kind" })
    }

    fun testLibraryMemberNavigatesFromColdStub() {
        myFixture.tempDirFixture.createFile("cold.cr", "lib LibGMP\nstruct MPF\nvalue : Int32\nend\nend")
        myFixture.configureByText("main.cr", "require \"./cold\"\nvalue : LibGMP::M<caret>PF")
        val library = CrystalIndexService.findLibs("LibGMP", project, GlobalSearchScope.allScope(project)).single()
        assertFalse("The regression must begin without a loaded library AST", (library.containingFile as PsiFileImpl).isContentsLoaded)
        assertTrue(targetAtCaret() is CrystalLibStructDefinition)
    }

    fun testActualGotoDeclarationActionOpensMpfDeclaration() {
        val library = addLibrary()
        myFixture.configureByText("big_float.cr", """
            require "./lib_gmp"
            struct BigFloat
              def initialize(@mpf : LibGMP::M<caret>PF)
              end
            end
        """.trimIndent())
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        assertTrue("The actual Go to Declaration action must open the library source",
            library.virtualFile in FileEditorManager.getInstance(project).selectedFiles)
    }

    fun testCheckoutAndSdkLibraryAndMemberDeclarationsRemainAvailableToGotoChooser() {
        val prelude = myFixture.addFileToProject("sdk/prelude.cr", "")
        val installed = addLibrary("sdk/big/lib_gmp.cr")
        myFixture.addFileToProject("sdk/big.cr", "require \"./big/lib_gmp\"")
        val checkout = addLibrary("src/big/lib_gmp.cr")
        myFixture.addFileToProject("src/big.cr", "require \"./big/lib_gmp\"\nrequire \"./big/big_float\"")
        val source = myFixture.addFileToProject("src/big/big_float.cr", """
            require "big"
            struct BigFloat
              def initialize(@mpf : LibGMP::MPF)
              end
            end
        """.trimIndent())
        val root = requireNotNull(prelude.virtualFile.parent)
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { root }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        assertSame(root, CrystalStdlibResolver.resolveStdlibPath(project))
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(source.text.indexOf("::MPF") + 3)
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val sources = CrystalRequireGraphService.getInstance(project).effectiveSources(leaf)
        assertTrue("The checkout declaration must be in the selected ancestor entry", sources.contains(checkout))
        assertTrue("The bare big require must also load the installed declaration", sources.contains(installed))
        for (libraryRoot in listOf(true, false)) {
            val offset = if (libraryRoot) source.text.indexOf("LibGMP::") else source.text.indexOf("::MPF") + 3
            myFixture.editor.caretModel.moveToOffset(offset)
            val reference = myFixture.file.findReferenceAt(offset)
            assertNotNull(reference)
            assertNull("Do not arbitrarily select one of the two declarations", reference!!.resolve())
            assertTrue("Go to Declaration needs polyvariant candidates instead of no declaration",
                reference is PsiPolyVariantReference)
            val targets = (reference as PsiPolyVariantReference).multiResolve(false).mapNotNull { it.element }
            assertEquals(setOf(checkout.virtualFile, installed.virtualFile), targets.map { it.containingFile.virtualFile }.toSet())
            assertTrue(targets.all { if (libraryRoot) it is CrystalLibDefinition else it is CrystalLibStructDefinition })
            assertEquals(targets.toSet(), TargetElementUtil.getInstance().getTargetCandidates(reference).toSet())
            assertEquals("The active JetBrains Ctrl+B action must find a declaration, not report nowhere to go",
                GotoDeclarationOrUsageHandler2.GTDUOutcome.GTD,
                GotoDeclarationOrUsageHandler2.testGTDUOutcome(myFixture.editor, myFixture.file, offset))
        }
    }

    fun testValidLibraryReopeningsOfferBothRootsAndOnlyTheMatchingMember() {
        val first = addLibrary("first.cr")
        val second = myFixture.addFileToProject("second.cr", "lib LibGMP\nfun extra\nend")
        addLibrary("unrequired.cr")
        myFixture.configureByText("main.cr", "require \"./first\"\nrequire \"./second\"\nvalue : LibG<caret>MP::MPF")
        val reference = myFixture.file.findReferenceAt(myFixture.caretOffset) as PsiPolyVariantReference
        assertNull(reference.resolve())
        val targets = reference.multiResolve(false).mapNotNull { it.element }
        assertEquals(setOf(first.virtualFile, second.virtualFile), targets.map { it.containingFile.virtualFile }.toSet())
        assertTrue(targets.all { it is CrystalLibDefinition })
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("::MPF") + 3)
        val member = targetAtCaret()
        assertTrue(member is CrystalLibStructDefinition)
        assertEquals(first.virtualFile, member!!.containingFile.virtualFile)
    }
}
