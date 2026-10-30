package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.inspections.CrystalUnresolvedName
import de.magynhard.crystal.inspections.CrystalUnresolvedNameInspection
import de.magynhard.crystal.inspections.CrystalArgumentCountInspection
import de.magynhard.crystal.inspections.CrystalTypeCheckInspection
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.psi.CrystalTypePath

class CrystalRecordTypeResolutionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
        myFixture.addFileToProject("index.cr", """
            module Crystalline::Lightweight
              record ArgInfo, name : String
              record MethodInfo, name : String, args : Array(ArgInfo)
            end
        """.trimIndent())
    }

    private fun configure(source: String) {
        myFixture.configureByText("contracts.cr", source.trimIndent())
        assertNull(PsiTreeUtil.findChildOfType(myFixture.file, PsiErrorElement::class.java))
    }

    private fun target(): PsiElement? {
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        return PsiTreeUtil.getParentOfType(leaf, CrystalTypePath::class.java)!!.reference?.resolve()
    }

    fun testRequiredRecordsResolveInAnnotationsOfNestedModules() {
        configure("""
            require "./index"
            module Crystalline::Lightweight
              module Contracts
                def derive(method : Method<caret>Info) : Array(ArgInfo)
                  [] of ArgInfo
                end
                private def yield_contract_for(method : MethodInfo)
                end
              end
            end
        """)
        myFixture.checkHighlighting()
        val declaration = target()
        assertTrue(declaration is CrystalMethodCallExpression)
        assertEquals("Crystalline::Lightweight::MethodInfo", CrystalPsiUtils.buildQualifiedName(declaration!!))
        assertEquals("index.cr", declaration.containingFile.name)
    }

    fun testQualifiedAndAbsolutePathsKeepTheirExactRecordIdentity() {
        myFixture.addFileToProject("namespace.cr", "module Crystalline\nend")
        myFixture.addFileToProject("other.cr", "module Other\nrecord MethodInfo, name : String\nend")
        for (path in listOf("Crystalline::Lightweight::MethodInfo", "::Crystalline::Lightweight::MethodInfo")) {
            configure("""
                require "./index"
                require "./namespace"
                require "./other"
                module Other
                  def read(method : ${path.replace("MethodInfo", "Method<caret>Info")})
                  end
                end
            """)
            myFixture.checkHighlighting()
            assertEquals("Crystalline::Lightweight::MethodInfo", CrystalPsiUtils.buildQualifiedName(target()!!))
        }
    }

    fun testNearestRecordWinsOverAnOuterOrdinaryType() {
        myFixture.addFileToProject("global.cr", "class MethodInfo\nend")
        configure("""
            require "./index"
            require "./global"
            module Crystalline::Lightweight::Contracts
              def read(method : Method<caret>Info)
              end
            end
        """)
        assertEquals("Crystalline::Lightweight::MethodInfo", CrystalPsiUtils.buildQualifiedName(target()!!))
        val constructor = CrystalTypeSetResolver.session(myFixture.file).resolveConstructor("MethodInfo", target()!!)
        assertTrue(constructor is CrystalConstructorResolution.Record)
    }

    fun testUnrequiredAndUnrelatedRecordsNeverBecomeNavigationTargets() {
        for (source in listOf(
            "module Crystalline::Lightweight\ndef read(method : Method<caret>Info)\nend\nend",
            "require \"./index\"\nmodule Unrelated\ndef read(method : Method<caret>Info)\nend\nend",
            "require \"./index\"\nmodule Crystalline::Lightweight\ndef read(method : ::Method<caret>Info)\nend\nend",
        )) {
            configure(source)
            assertNull(target())
            val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
            val path = PsiTreeUtil.getParentOfType(leaf, CrystalTypePath::class.java)!!
            assertEquals(CrystalUnresolvedName.UnresolvedKind.UNREQUIRED,
                CrystalUnresolvedName.isUnresolvedConstant("MethodInfo", path))
        }
    }

    fun testRecordConstructorsUseTheRequiredDeclaration() {
        configure("""
            require "./index"
            module Crystalline::Lightweight::Contracts
              def read(method : Method<caret>Info)
              end
            end
        """)
        val position = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val session = CrystalTypeSetResolver.session(position)
        val result = session.resolveConstructor("MethodInfo", position)
        assertTrue(result is CrystalConstructorResolution.Record)
        assertEquals("index.cr", (result as CrystalConstructorResolution.Record).recordDefinition.containingFile.name)
    }

    fun testDuplicateRecordsKeepAllNavigationTargetsWithoutAnExactBinding() {
        myFixture.addFileToProject("duplicate.cr", "module Crystalline::Lightweight\nrecord MethodInfo, id : Int32\nend")
        configure("""
            require "./index"
            require "./duplicate"
            module Crystalline::Lightweight
              def read(method : Method<caret>Info)
              end
            end
        """)
        assertNull(target())
        val path = PsiTreeUtil.getParentOfType(myFixture.file.findElementAt(myFixture.caretOffset), CrystalTypePath::class.java)!!
        val reference = path.reference as com.intellij.psi.PsiPolyVariantReference
        assertEquals(2, reference.multiResolve(false).size)
        assertTrue(CrystalTypeSetResolver.session(path).resolveConstructor("MethodInfo", path) is CrystalConstructorResolution.Incomplete)
    }

    fun testRecoveredRecordHeaderDoesNotCertifyATruncatedConstructorSignature() {
        val repaired = """
            module Crystalline::Lightweight
              record BrokenInfo,
                name : String,
                doc : String? = nil
            end
        """.trimIndent()
        val broken = myFixture.addFileToProject("broken.cr", repaired.replace("doc : String? = nil", "doc :"))
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java, CrystalTypeCheckInspection::class.java)
        configure("""
            require "./broken"
            module Crystalline::Lightweight
              def read(method : Broken<caret>Info)
                BrokenInfo.new(name: "example", doc: "documentation")
              end
            end
        """)
        assertNotNull("Exercise a recovered source file", PsiTreeUtil.findChildOfType(broken, PsiErrorElement::class.java))
        myFixture.checkHighlighting()
        assertEquals("Crystalline::Lightweight::BrokenInfo", CrystalPsiUtils.buildQualifiedName(target()!!))
        val position = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertTrue(CrystalTypeSetResolver.session(position).resolveConstructor("BrokenInfo", position) is CrystalConstructorResolution.Incomplete)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(broken)!!
            document.setText(repaired)
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        assertTrue("Editing the declaration must invalidate cached parse completeness",
            CrystalTypeSetResolver.session(position).resolveConstructor("BrokenInfo", position) is CrystalConstructorResolution.Record)
    }
}
