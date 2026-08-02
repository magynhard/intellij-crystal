package de.magynhard.crystal.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.sdk.CrystalSettings
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CrystalRequireGraphServiceTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean = false

    fun testProductionServiceDoesNotDiscoverStdlibDuringCompletion() {
        files("src/main.cr" to "")
        val tempDirectory = Files.createTempDirectory("crystal-require-graph")
        val marker = tempDirectory.resolve("compiler-invoked")
        val executable = Files.writeString(
            tempDirectory.resolve("fake-crystal"),
            "#!/bin/sh\ntouch \"$marker\"\nexit 1\n",
        )
        assertTrue(executable.toFile().setExecutable(true))
        val settings = CrystalSettings.getInstance(project)
        val originalCrystalPath = settings.state.crystalPath
        try {
            settings.state.crystalPath = executable.toString()
            CrystalStdlibResolver.clearCachedStdlibPath(project)

            val sources = CrystalRequireGraphService(project).effectiveSources(elementIn("src/main.cr"))

            assertEquals(setOf(vf("src/main.cr")), sources.files)
            assertFalse("Completion must not launch Crystal stdlib discovery", Files.exists(marker))
        } finally {
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            File(tempDirectory.toString()).deleteRecursively()
        }
    }

    fun testProductionServiceReusesPreviouslyDiscoveredStdlib() {
        files("src/main.cr" to "")
        val tempDirectory = Files.createTempDirectory("crystal-require-graph-cached")
        val marker = tempDirectory.resolve("compiler-invocations")
        val stdlib = Files.createDirectory(tempDirectory.resolve("stdlib"))
        Files.writeString(stdlib.resolve("prelude.cr"), "")
        val executable = Files.writeString(
            tempDirectory.resolve("fake-crystal"),
            "#!/bin/sh\nprintf x >> \"$marker\"\necho \"$stdlib\"\n",
        )
        assertTrue(executable.toFile().setExecutable(true))
        val settings = CrystalSettings.getInstance(project)
        val originalCrystalPath = settings.state.crystalPath
        try {
            settings.state.crystalPath = executable.toString()
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            val cachedRoot = requireNotNull(CrystalStdlibResolver.resolveStdlibPath(project))
            assertEquals(1L, Files.size(marker))

            val sources = CrystalRequireGraphService(project).effectiveSources(elementIn("src/main.cr"))

            assertTrue(sources.files.contains(requireNotNull(cachedRoot.findChild("prelude.cr"))))
            assertEquals("Completion must reuse the cached root", 1L, Files.size(marker))
        } finally {
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            File(tempDirectory.toString()).deleteRecursively()
        }
    }

    fun testProductionServiceObservesStdlibCachedAfterColdFirstQuery() {
        files("src/main.cr" to "")
        val tempDirectory = Files.createTempDirectory("crystal-require-graph-startup-order")
        val marker = tempDirectory.resolve("compiler-invocations")
        val stdlib = Files.createDirectory(tempDirectory.resolve("stdlib"))
        Files.writeString(stdlib.resolve("prelude.cr"), "")
        val executable = Files.writeString(
            tempDirectory.resolve("fake-crystal"),
            "#!/bin/sh\nprintf x >> \"$marker\"\necho \"$stdlib\"\n",
        )
        assertTrue(executable.toFile().setExecutable(true))
        val settings = CrystalSettings.getInstance(project)
        val originalCrystalPath = settings.state.crystalPath
        try {
            settings.state.crystalPath = executable.toString()
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            val service = CrystalRequireGraphService(project)

            assertEquals(
                setOf(vf("src/main.cr")),
                service.effectiveSources(elementIn("src/main.cr")).files,
            )
            assertFalse(Files.exists(marker))

            val cachedRoot = requireNotNull(CrystalStdlibResolver.resolveStdlibPath(project))
            assertEquals(1L, Files.size(marker))
            val updated = service.effectiveSources(elementIn("src/main.cr"))

            assertTrue(updated.files.contains(requireNotNull(cachedRoot.findChild("prelude.cr"))))
            assertEquals("The graph query must not launch discovery", 1L, Files.size(marker))
            assertEquals(0, service.cacheStats().fullInvalidations)
        } finally {
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            File(tempDirectory.toString()).deleteRecursively()
        }
    }

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

    fun testRebuildsPreludeFoundationWhenTransitiveRequireChanges() {
        files(
            "stdlib/prelude.cr" to "require \"./foundation\"",
            "stdlib/foundation.cr" to "require \"./old_dependency\"",
            "stdlib/old_dependency.cr" to "",
            "stdlib/new_dependency.cr" to "",
            "src/main.cr" to "",
        )
        val service = service()
        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(vf("stdlib/old_dependency.cr")))

        replaceText("stdlib/foundation.cr", "require \"./new_dependency\"")
        val updated = service.effectiveSources(elementIn("src/main.cr"))

        assertFalse(updated.files.contains(vf("stdlib/old_dependency.cr")))
        assertTrue(updated.files.contains(vf("stdlib/new_dependency.cr")))
    }

    fun testRebuildsPreludeFoundationWhenTransitiveDependencyIsDeleted() {
        files(
            "stdlib/prelude.cr" to "require \"./dependency\"",
            "stdlib/dependency.cr" to "",
            "src/main.cr" to "",
        )
        val service = service()
        val dependency = vf("stdlib/dependency.cr")
        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(dependency))

        ApplicationManager.getApplication().runWriteAction { dependency.delete(this) }

        assertFalse(service.effectiveSources(elementIn("src/main.cr")).files.contains(dependency))
    }

    fun testRebuildsPreludeFoundationWhenPreludeAddsDependency() {
        files(
            "stdlib/prelude.cr" to "",
            "stdlib/added.cr" to "",
            "src/main.cr" to "",
        )
        val service = service()
        assertFalse(service.effectiveSources(elementIn("src/main.cr")).files.contains(vf("stdlib/added.cr")))

        replaceText("stdlib/prelude.cr", "require \"./added\"")

        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(vf("stdlib/added.cr")))
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

    fun testLargeRequireCycleDoesNotOverflowTheStack() {
        val chainLength = 5_000
        myFixture.addFileToProject("stdlib/prelude.cr", "")
        repeat(chainLength) { index ->
            val target = if (index == chainLength - 1) 0 else index + 1
            myFixture.addFileToProject(
                "src/chain_$index.cr",
                "require \"./chain_$target\"",
            )
        }

        val sources = service().effectiveSources(elementIn("src/chain_0.cr"))

        assertEquals(chainLength + 1, sources.files.size)
        assertTrue(sources.files.contains(vf("src/chain_4999.cr")))
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

    fun testNodeBuildPublishesBeforeAConcurrentRequireEditCanInterleave() {
        files(
            "stdlib/prelude.cr" to "",
            "stdlib/old_dependency.cr" to "",
            "stdlib/new_dependency.cr" to "",
            "src/main.cr" to "require \"old_dependency\"",
        )
        val resolverEntered = CountDownLatch(1)
        val releaseResolver = CountDownLatch(1)
        val stdlibLookups = AtomicInteger()
        val service = CrystalRequireGraphService(
            project,
            CrystalRequirePathResolver(project) {
                if (stdlibLookups.incrementAndGet() == 2) {
                    resolverEntered.countDown()
                    assertTrue(releaseResolver.await(10, TimeUnit.SECONDS))
                }
                directory("stdlib")
            },
        )
        val context = elementIn("src/main.cr")
        val document = document("src/main.cr")
        val initialSources = CompletableFuture.supplyAsync(
            { service.effectiveSources(context) },
            AppExecutorUtil.getAppExecutorService(),
        )
        assertTrue(resolverEntered.await(10, TimeUnit.SECONDS))
        val writeStarted = CountDownLatch(1)
        val writeFinished = CountDownLatch(1)
        val writer = CompletableFuture<Void>()
        ApplicationManager.getApplication().invokeLater {
            try {
                writeStarted.countDown()
                ApplicationManager.getApplication().runWriteAction {
                    document.setText("require \"new_dependency\"")
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }
                writer.complete(null)
            } catch (error: Throwable) {
                writer.completeExceptionally(error)
            } finally {
                writeFinished.countDown()
            }
        }

        assertTrue(writeStarted.await(10, TimeUnit.SECONDS))
        assertFalse(
            "Require edits must not interleave with node publication",
            writeFinished.await(250, TimeUnit.MILLISECONDS),
        )
        releaseResolver.countDown()
        val initial = initialSources.get(10, TimeUnit.SECONDS)
        writer.get(10, TimeUnit.SECONDS)

        assertTrue(initial.files.contains(vf("stdlib/old_dependency.cr")))
        assertFalse(initial.files.contains(vf("stdlib/new_dependency.cr")))
        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertFalse(updated.files.contains(vf("stdlib/old_dependency.cr")))
        assertTrue(updated.files.contains(vf("stdlib/new_dependency.cr")))
    }

    fun testEffectiveSnapshotCannotMixNodesAcrossAtomicWrite() {
        files(
            "stdlib/prelude.cr" to "",
            "stdlib/b.cr" to "require \"./old_b\"",
            "stdlib/old_b.cr" to "",
            "stdlib/new_b.cr" to "",
            "src/main.cr" to "require \"./a\"",
            "src/a.cr" to "require \"./old_a\"\nrequire \"b\"",
            "src/old_a.cr" to "",
            "src/new_a.cr" to "",
        )
        val resolverEntered = CountDownLatch(1)
        val releaseResolver = CountDownLatch(1)
        val stdlibLookups = AtomicInteger()
        val service = CrystalRequireGraphService(
            project,
            CrystalRequirePathResolver(project) {
                if (stdlibLookups.incrementAndGet() == 2) {
                    resolverEntered.countDown()
                    assertTrue(releaseResolver.await(10, TimeUnit.SECONDS))
                }
                directory("stdlib")
            },
        )
        val context = elementIn("src/main.cr")
        val aDocument = document("src/a.cr")
        val bDocument = document("stdlib/b.cr")
        val initialSources = CompletableFuture.supplyAsync(
            { service.effectiveSources(context) },
            AppExecutorUtil.getAppExecutorService(),
        )
        assertTrue(resolverEntered.await(10, TimeUnit.SECONDS))
        val writeStarted = CountDownLatch(1)
        val writeFinished = CountDownLatch(1)
        val writer = CompletableFuture<Void>()
        ApplicationManager.getApplication().invokeLater {
            try {
                writeStarted.countDown()
                ApplicationManager.getApplication().runWriteAction {
                    aDocument.setText("require \"./new_a\"\nrequire \"b\"")
                    bDocument.setText("require \"./new_b\"")
                    PsiDocumentManager.getInstance(project).commitDocument(aDocument)
                    PsiDocumentManager.getInstance(project).commitDocument(bDocument)
                }
                writer.complete(null)
            } catch (error: Throwable) {
                writer.completeExceptionally(error)
            } finally {
                writeFinished.countDown()
            }
        }

        assertTrue(writeStarted.await(10, TimeUnit.SECONDS))
        assertFalse(writeFinished.await(250, TimeUnit.MILLISECONDS))
        releaseResolver.countDown()
        val initial = initialSources.get(10, TimeUnit.SECONDS)
        writer.get(10, TimeUnit.SECONDS)

        assertTrue(initial.files.contains(vf("src/old_a.cr")))
        assertTrue(initial.files.contains(vf("stdlib/old_b.cr")))
        assertFalse(initial.files.contains(vf("src/new_a.cr")))
        assertFalse(initial.files.contains(vf("stdlib/new_b.cr")))

        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertFalse(updated.files.contains(vf("src/old_a.cr")))
        assertFalse(updated.files.contains(vf("stdlib/old_b.cr")))
        assertTrue(updated.files.contains(vf("src/new_a.cr")))
        assertTrue(updated.files.contains(vf("stdlib/new_b.cr")))
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

    fun testAmbiguousNonphysicalRootCandidateProducesEmptySet() {
        files(
            "stdlib/prelude.cr" to "",
            "src/main.cr" to "",
        )
        val sameNamedNonphysicalCandidate = PsiFileFactory.getInstance(project).createFileFromText(
            "main.cr",
            CrystalLanguage,
            "require \"./unknown\"",
            false,
            false,
        )

        assertEquals(
            emptySet<VirtualFile>(),
            service().effectiveSources(sameNamedNonphysicalCandidate).files,
        )
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

    fun testRetriesPreludeResolutionAfterCancellation() {
        files(
            "stdlib/prelude.cr" to "",
            "src/main.cr" to "",
        )
        var attempts = 0
        val stdlibRoot = directory("stdlib")
        val service = CrystalRequireGraphService(
            project,
            CrystalRequirePathResolver(project) {
                attempts++
                if (attempts == 1) throw ProcessCanceledException()
                stdlibRoot
            },
        )

        assertThrows(ProcessCanceledException::class.java) {
            service.effectiveSources(elementIn("src/main.cr"))
        }
        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(vf("stdlib/prelude.cr")))
        assertEquals(2, attempts)
    }

    private fun service(stdlib: VirtualFile? = directory("stdlib")): CrystalRequireGraphService =
        CrystalRequireGraphService(project, CrystalRequirePathResolver(project) { stdlib })

    private fun files(vararg files: Pair<String, String>) {
        files.forEach { (path, text) -> myFixture.addFileToProject(path, text) }
    }

    private fun elementIn(path: String): PsiElement = ReadAction.computeBlocking<PsiElement, RuntimeException> {
        val file = psiFile(path)
        file.firstChild ?: file
    }

    private fun psiFile(path: String): PsiFile = ReadAction.computeBlocking<PsiFile, RuntimeException> {
        requireNotNull(PsiManager.getInstance(project).findFile(vf(path)))
    }

    private fun vf(path: String): VirtualFile =
        requireNotNull(myFixture.tempDirFixture.getFile(path))

    private fun directory(path: String): VirtualFile =
        requireNotNull(myFixture.tempDirFixture.getFile(path))

    private fun document(path: String) = ReadAction.computeBlocking<Document, RuntimeException> {
        requireNotNull(PsiDocumentManager.getInstance(project).getDocument(psiFile(path)))
    }

    private fun replaceText(path: String, text: String) {
        val document = document(path)
        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction { document.setText(text) }
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
}
