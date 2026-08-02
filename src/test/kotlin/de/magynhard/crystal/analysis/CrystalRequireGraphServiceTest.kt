package de.magynhard.crystal.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.CrystalLanguage

class CrystalRequireGraphServiceTest : BasePlatformTestCase() {

    fun testCombinesPreludeCurrentFileAndForwardClosure() {
        files(
            "stdlib/prelude.cr" to
                "require \"./string\"\nrequire \"./int\"\nrequire \"./array\"",
            "stdlib/string.cr" to "",
            "stdlib/int.cr" to "",
            "stdlib/array.cr" to "",
            "src/main.cr" to "require \"./feature\"",
            "src/feature.cr" to "require \"./extension\"",
            "src/extension.cr" to "",
        )
        val sources = service().effectiveSources(elementIn("src/main.cr"))

        assertEquals(
            setOf(
                vf("stdlib/prelude.cr"),
                vf("stdlib/string.cr"),
                vf("stdlib/int.cr"),
                vf("stdlib/array.cr"),
                vf("src/main.cr"),
                vf("src/feature.cr"),
                vf("src/extension.cr"),
            ),
            sources.files,
        )
    }

    fun testUsesOnlyForwardClosureAndTerminatesCycles() {
        files(
            "stdlib/prelude.cr" to "",
            "src/main.cr" to "require \"./left\"\nrequire \"./right\"",
            "src/left.cr" to "require \"./shared\"",
            "src/right.cr" to "",
            "src/shared.cr" to "require \"./left\"",
        )

        assertEquals(
            setOf(vf("stdlib/prelude.cr"), vf("src/left.cr"), vf("src/shared.cr")),
            service().effectiveSources(elementIn("src/left.cr")).files,
        )
    }

    fun testReusesNodesClosuresAndImmutableEffectiveSnapshotAfterMethodBodyEdit() {
        files(
            "stdlib/prelude.cr" to "require \"./string\"",
            "stdlib/string.cr" to "",
            "src/main.cr" to "require \"./feature\"\ndef value\n1\nend",
            "src/feature.cr" to "",
        )
        val service = service()
        val context = elementIn("src/main.cr")
        val first = service.effectiveSources(context)
        val initialStats = service.cacheStats()

        replaceText("src/main.cr", "require \"./feature\"\ndef value\nputs \"changed\"\nend")
        val second = service.effectiveSources(elementIn("src/main.cr"))

        assertSame(first, second)
        assertEquals(initialStats, service.cacheStats())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (second.files as MutableSet<VirtualFile>).add(vf("stdlib/prelude.cr"))
        }
    }

    fun testChangedDependencyRebuildsItsDependentsWithoutInvalidatingUnrelatedRoot() {
        files(
            "stdlib/prelude.cr" to "",
            "src/main.cr" to "require \"./feature\"",
            "src/feature.cr" to "require \"./old_extension\"",
            "src/old_extension.cr" to "",
            "src/new_extension.cr" to "",
            "src/unrelated.cr" to "require \"./unrelated_dependency\"",
            "src/unrelated_dependency.cr" to "",
        )
        val service = service()
        service.effectiveSources(elementIn("src/main.cr"))
        val feature = service.effectiveSources(elementIn("src/feature.cr"))
        val unrelated = service.effectiveSources(elementIn("src/unrelated.cr"))
        val before = service.cacheStats()

        replaceText("src/feature.cr", "require \"./new_extension\"")
        val updated = service.effectiveSources(elementIn("src/main.cr"))

        assertFalse(updated.files.contains(vf("src/old_extension.cr")))
        assertTrue(updated.files.contains(vf("src/new_extension.cr")))
        assertEquals(before.nodeBuilds + 2, service.cacheStats().nodeBuilds)
        assertNotSame(feature, service.effectiveSources(elementIn("src/feature.cr")))
        assertEquals(before.closureBuilds + 2, service.cacheStats().closureBuilds)
        assertSame(unrelated, service.effectiveSources(elementIn("src/unrelated.cr")))
    }

    fun testMissingPreludeAndUnresolvedEdgesProduceConservativeSet() {
        files("src/main.cr" to "require \"./missing\"")

        assertEquals(setOf(vf("src/main.cr")), service(stdlib = null).effectiveSources(elementIn("src/main.cr")).files)
    }

    fun testChangedEdgeDoesNotRetainLastResolvedFile() {
        files(
            "stdlib/prelude.cr" to "",
            "src/main.cr" to "require \"./feature\"",
            "src/feature.cr" to "",
        )
        val service = service()
        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(vf("src/feature.cr")))

        replaceText("src/main.cr", "require \"./missing\"")

        assertEquals(
            setOf(vf("stdlib/prelude.cr"), vf("src/main.cr")),
            service.effectiveSources(elementIn("src/main.cr")).files,
        )
    }

    fun testInvalidRootAndUnrelatedNonphysicalElementsAreExcluded() {
        files(
            "stdlib/prelude.cr" to "",
            "src/main.cr" to "",
        )
        val service = service()
        val context = elementIn("src/main.cr")
        val sources = service.effectiveSources(context)
        val nonphysical = PsiFileFactory.getInstance(project).createFileFromText(
            "scratch.cr",
            CrystalLanguage,
            "1",
            false,
            false,
        )

        assertTrue(sources.contains(elementIn("src/main.cr")))
        assertFalse(sources.contains(nonphysical))
        assertTrue(service.effectiveSources(nonphysical).files.isEmpty())
        ApplicationManager.getApplication().runWriteAction { vf("src/main.cr").delete(this) }
        assertTrue(service.effectiveSources(context).files.isEmpty())
    }

    fun testResolvesOnePreludeFoundationPerGlobalGeneration() {
        files(
            "stdlib/prelude.cr" to "",
            "src/main.cr" to "",
            "src/other.cr" to "",
        )
        var stdlibLookups = 0
        val stdlibRoot = directory("stdlib")
        val service = CrystalRequireGraphService(
            project,
            CrystalRequirePathResolver(project) {
                stdlibLookups++
                stdlibRoot
            },
        )

        service.effectiveSources(elementIn("src/main.cr"))
        service.effectiveSources(elementIn("src/other.cr"))
        assertEquals(1, stdlibLookups)

        service.invalidateAll()
        service.effectiveSources(elementIn("src/main.cr"))
        assertEquals(2, stdlibLookups)
        assertEquals(1, service.cacheStats().fullInvalidations)
    }

    private fun service(stdlib: VirtualFile? = directory("stdlib")): CrystalRequireGraphService =
        CrystalRequireGraphService(project, CrystalRequirePathResolver(project) { stdlib })

    private fun files(vararg files: Pair<String, String>) {
        files.forEach { (path, text) -> myFixture.addFileToProject(path, text) }
    }

    private fun elementIn(path: String): PsiElement = psiFile(path).firstChild ?: psiFile(path)

    private fun psiFile(path: String): PsiFile =
        requireNotNull(PsiManager.getInstance(project).findFile(vf(path)))

    private fun vf(path: String): VirtualFile =
        requireNotNull(myFixture.tempDirFixture.findOrCreateDir("").findFileByRelativePath(path))

    private fun directory(path: String): VirtualFile =
        requireNotNull(myFixture.tempDirFixture.findOrCreateDir("").findFileByRelativePath(path))

    private fun replaceText(path: String, text: String) {
        val file = psiFile(path)
        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
        ApplicationManager.getApplication().runWriteAction { document.setText(text) }
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
