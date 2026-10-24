package de.magynhard.crystal

import com.intellij.execution.RunManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.indexing.FileBasedIndex
import de.magynhard.crystal.inspections.CrystalUnresolvedNameInspection
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.run.CrystalRunConfiguration
import de.magynhard.crystal.run.CrystalRunConfigurationType
import de.magynhard.crystal.run.CrystalRunFactory
import de.magynhard.crystal.stubs.CrystalIndexService

class CrystalProgramContextTest : BasePlatformTestCase() {
    fun testBenchmarkMemoryResolvesThroughLibraryEntry() {
        val entry = myFixture.addFileToProject("src/benchmark.cr", """
            require "./benchmark/**"
            module Benchmark
              extend self
              def memory(&)
                yield
              end
            end
        """.trimIndent())
        val source = myFixture.addFileToProject("src/benchmark/ips.cr", """
            module Benchmark
              module IPS
                class Job
                  def report
                    Benchmark.memory do
                      1
                    end
                  end
                end
              end
            end
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(source.text.indexOf("memory"))
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
        myFixture.checkHighlighting()
        val target = dotTarget()
        assertEquals("memory", target.name)
        assertEquals(entry.virtualFile, target.containingFile.virtualFile)
    }

    fun testSelectedEntryControlsExactMethodWithoutMergingOtherProgram() {
        val shared = myFixture.addFileToProject("src/shared.cr", "Benchmark.memory { 1 }")
        val left = myFixture.addFileToProject("src/left.cr", """
            require "./shared"
            module Benchmark
              extend self
              def memory(&)
                yield
              end
            end
        """.trimIndent())
        val right = myFixture.addFileToProject("src/right.cr", left.text)
        val manager = RunManager.getInstance(project)
        val previous = manager.selectedConfiguration
        val factory = CrystalRunFactory(CrystalRunConfigurationType())
        val leftSettings = manager.createConfiguration("Left", factory)
        val rightSettings = manager.createConfiguration("Right", factory)
        (leftSettings.configuration as CrystalRunConfiguration).filePath = left.virtualFile.path
        (rightSettings.configuration as CrystalRunConfiguration).filePath = right.virtualFile.path
        manager.addConfiguration(leftSettings)
        manager.addConfiguration(rightSettings)
        try {
            myFixture.configureFromExistingVirtualFile(shared.virtualFile)
            myFixture.editor.caretModel.moveToOffset(shared.text.indexOf("memory"))
            myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            // Deliver pending file events before waiting: a "ready" index can
            // otherwise precede the background indexing request for right.cr.
            val index = FileBasedIndex.getInstance()
            index.requestReindex(left.virtualFile)
            index.requestReindex(right.virtualFile)
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            IndexingTestUtil.waitUntilIndexesAreReady(project)
            assertEquals(
                "Both program declarations must be indexed before testing context switches",
                setOf(left.virtualFile, right.virtualFile),
                CrystalIndexService.findTypes("Benchmark", project, GlobalSearchScope.projectScope(project))
                    .map { it.containingFile.virtualFile }.toSet()
            )
            manager.selectedConfiguration = leftSettings
            myFixture.checkHighlighting()
            assertEquals(left.virtualFile, dotTarget().containingFile.virtualFile)
            repeat(3) {
                for ((settings, expected, excluded) in listOf(
                    Triple(rightSettings, right, left), Triple(leftSettings, left, right)
                )) {
                    manager.selectedConfiguration = settings
                    // Highlight first: prewarming the graph could hide a stale-context failure.
                    myFixture.checkHighlighting()
                    val switched = CrystalRequireGraphService.getInstance(project).effectiveSources(shared)
                    assertTrue("Selected program must contain ${expected.name}: ${switched.files}", expected.virtualFile in switched.files)
                    assertFalse("Selected program must exclude ${excluded.name}: ${switched.files}", excluded.virtualFile in switched.files)
                    assertEquals(expected.virtualFile, dotTarget().containingFile.virtualFile)
                }
            }
        } finally {
            manager.selectedConfiguration = previous
            manager.removeConfiguration(leftSettings)
            manager.removeConfiguration(rightSettings)
        }
    }

    private fun dotTarget(): CrystalMethodDefinition {
        val access = PsiTreeUtil.getParentOfType(
            myFixture.file.findElementAt(myFixture.caretOffset), CrystalDotCallAccess::class.java
        )!!
        return access.reference?.resolve() as? CrystalMethodDefinition
            ?: error("Expected an exact method target")
    }
}
