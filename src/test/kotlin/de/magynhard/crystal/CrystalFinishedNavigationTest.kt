package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import de.magynhard.crystal.analysis.CrystalStaticDeclarations
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.CrystalMacroBody
import de.magynhard.crystal.psi.CrystalPsiUtils

class CrystalFinishedNavigationTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
    }

    private fun targets(code: String): List<PsiElement> {
        myFixture.configureByText("range_formatting.cr", code.trimIndent())
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        assertNotNull("Exercise a click inside the original opaque macro body",
            PsiTreeUtil.getParentOfType(leaf, CrystalMacroBody::class.java, false))
        return CrystalGotoDeclarationHandler().getGotoDeclarationTargets(
            leaf, myFixture.caretOffset, myFixture.editor,
        ).orEmpty().toList()
    }

    fun testPropertyTypeNavigatesToFollowingStructInTheSameFile() {
        val result = targets("""
            module LSP
              macro finished
                class DocumentRangeFormattingRequest
                  property params : DocumentRangeFormattingPar<caret>ams
                end
              end
              struct DocumentRangeFormattingParams
              end
            end
        """)
        assertEquals(1, result.size)
        assertEquals("LSP::DocumentRangeFormattingParams", CrystalPsiUtils.buildQualifiedName(result.single()))
        assertEquals("range_formatting.cr", result.single().containingFile.name)
        assertEquals(myFixture.file.text.indexOf("struct DocumentRangeFormattingParams") + "struct ".length, result.single().textOffset)
    }

    fun testSuperclassAndNestedGenericArgumentsNavigateIndividually() {
        val source = """
            class Array(T)
            end
            module LSP
              class TextEdit
              end
              class RequestMessage(T)
              end
              macro finished
                class RangeRequest < BASE
                end
              end
            end
        """.trimIndent()
        for ((receiver, identity) in listOf(
            "RequestMess<caret>age(Array(TextEdit)?)" to "LSP::RequestMessage",
            "RequestMessage(Ar<caret>ray(TextEdit)?)" to "Array",
            "RequestMessage(Array(Text<caret>Edit)?)" to "LSP::TextEdit",
        )) {
            val result = targets(source.replace("BASE", receiver))
            assertEquals(1, result.size)
            assertEquals(identity, CrystalPsiUtils.buildQualifiedName(result.single()))
        }
    }

    fun testQualifiedTypePathSegmentsNavigateToTheirOwnDeclarations() {
        for ((path, identity) in listOf(
            "Pa<caret>cket::Payload" to "Packet",
            "Packet::Pay<caret>load" to "Packet::Payload",
        )) {
            val result = targets("""
                module Packet
                  class Payload
                  end
                end
                module LSP
                  macro finished
                    class Request
                      property params : $path
                    end
                  end
                end
            """)
            assertEquals(1, result.size)
            assertEquals(identity, CrystalPsiUtils.buildQualifiedName(result.single()))
        }
    }

    fun testReferenceToAnotherStaticClassNavigatesBackIntoTheMacro() {
        val result = targets("""
            module LSP
              macro finished
                class Payload
                end
                class Request
                  property params : Pay<caret>load
                end
              end
            end
        """)
        assertEquals(1, result.size)
        assertEquals(myFixture.file.text.indexOf("class Payload") + "class ".length, result.single().textOffset)
        assertEquals("range_formatting.cr", result.single().containingFile.name)
        assertFalse(result.single().isWritable)
    }

    fun testPhysicalSourceFileWinsOverARequiredReopening() {
        myFixture.addFileToProject("a_reopening.cr", "module LSP\nstruct Params\nend\nend")
        val result = targets("""
            require "./a_reopening"
            module LSP
              macro finished
                class Request
                  property params : Par<caret>ams
                end
              end
              struct Params
              end
            end
        """)
        assertEquals(1, result.size)
        assertEquals("range_formatting.cr", result.single().containingFile.name)
    }

    fun testClassReferenceInAnEmittedMethodUsesTheSameResolver() {
        val result = targets("""
            module LSP
              class Params
              end
              macro finished
                class Request
                  def build
                    Par<caret>ams.new
                  end
                end
              end
            end
        """)
        assertEquals(1, result.size)
        assertEquals("LSP::Params", CrystalPsiUtils.buildQualifiedName(result.single()))
    }

    fun testEditingATypeReferenceDoesNotReuseTheOldSyntaxPosition() {
        for (name in listOf("First", "Second")) {
            val result = targets("""
                module LSP
                  macro finished
                    class Request
                      property params : ${name.take(2)}<caret>${name.drop(2)}
                    end
                  end
                  class First
                  end
                  class Second
                  end
                end
            """)
            assertEquals(1, result.size)
            assertEquals("LSP::$name", CrystalPsiUtils.buildQualifiedName(result.single()))
        }
    }

    fun testLexicalAndAbsoluteNamesKeepTheirIdentity() {
        for ((path, identity) in listOf("Par<caret>ams" to "LSP::Params", "::Par<caret>ams" to "Params")) {
            val result = targets("""
                class Params
                end
                module LSP
                  class Params
                  end
                  macro finished
                    class Request
                      property params : $path
                    end
                  end
                end
            """)
            assertEquals(1, result.size)
            assertEquals(identity, CrystalPsiUtils.buildQualifiedName(result.single()))
        }
    }

    fun testUnrequiredAndUnrelatedTypesCannotHealMacroReferences() {
        myFixture.addFileToProject("hidden.cr", "module LSP\nclass Params\nend\nend")
        myFixture.addFileToProject("unrelated.cr", "module Other\nclass Params\nend\nend")
        assertEmpty(targets("""
            require "./unrelated"
            module LSP
              macro finished
                class Request
                  property params : Par<caret>ams
                end
              end
            end
        """))
    }

    fun testCommentsAndStringsDoNotBecomeTypeReferences() {
        for (content in listOf("# Par<caret>ams", "@method = \"Par<caret>ams\"")) {
            assertEmpty(targets("""
                module LSP
                  class Params
                  end
                  macro finished
                    class Request
                      $content
                    end
                  end
                end
            """))
        }
    }

    fun testUnsupportedMacroDoesNotAcquireStaticTypeReferences() {
        for ((name, dynamic) in listOf("build_request" to "", "finished" to "{{ 1 }}")) {
            assertEmpty(targets("""
                module LSP
                  class Params
                  end
                  macro $name
                    $dynamic
                    class Request
                      property params : Par<caret>ams
                    end
                  end
                end
            """))
        }
    }

    fun testOnlySourceBackedViewsInheritTheOriginalRequireContext() {
        targets("module LSP\nmacro finished\nclass Request\nproperty params : Par<caret>ams\nend\nend\nend")
        val graph = CrystalRequireGraphService.getInstance(project)
        val macro = PsiTreeUtil.findChildOfType(myFixture.file, de.magynhard.crystal.psi.CrystalMacroDefinition::class.java)!!
        val syntax = CrystalStaticDeclarations.finishedSyntax(macro)!!
        assertEquals(graph.effectiveSources(myFixture.file).files, graph.effectiveSources(syntax).files)
        val unrelated = PsiFileFactory.getInstance(project).createFileFromText("fragment.cr", CrystalFileType, "class Request\nend")
        assertEmpty(graph.effectiveSources(unrelated).files)
    }
}
