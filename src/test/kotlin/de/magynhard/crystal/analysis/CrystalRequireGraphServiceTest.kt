package de.magynhard.crystal.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.impl.ModuleRootEventImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.concurrency.AppExecutorUtil
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CrystalRequireGraphServiceTest : BasePlatformTestCase() {

    private val listenerDisposables = mutableListOf<Disposable>()
    private val fixtureFiles = mutableMapOf<String, VirtualFile>()

    override fun runInDispatchThread(): Boolean = false

    override fun tearDown() {
        try {
            listenerDisposables.forEach(Disposer::dispose)
            listenerDisposables.clear()
            fixtureFiles.clear()
            CrystalStdlibResolver.clearCachedStdlibPath(project)
        } finally {
            super.tearDown()
        }
    }

    fun testProductionServiceDoesNotDiscoverStdlibDuringCompletion() {
        files("task4_no_discovery/main.cr" to "")
        val discoveries = AtomicInteger()
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) {
            discoveries.incrementAndGet()
            null
        }

        val sources = productionService().effectiveSources(elementIn("task4_no_discovery/main.cr"))

        assertEquals(setOf(vf("task4_no_discovery/main.cr")), sources.files)
        assertEquals("Completion must not launch Crystal stdlib discovery", 0, discoveries.get())
    }

    fun testProductionServiceReusesPreviouslyDiscoveredStdlib() {
        files("task4_cached_stdlib/main.cr" to "")
        files("task4_cached_stdlib/stdlib/prelude.cr" to "")
        val discoveries = AtomicInteger()
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) {
            discoveries.incrementAndGet()
            null
        }
        val cachedRoot = CrystalStdlibResolver.publishStdlibPath(project, directory("task4_cached_stdlib/stdlib"))

        val sources = productionService(clearStdlibCache = false)
            .effectiveSources(elementIn("task4_cached_stdlib/main.cr"))

        assertTrue(sources.files.contains(requireNotNull(cachedRoot.findChild("prelude.cr"))))
        assertEquals("Completion must reuse the cached root", 0, discoveries.get())
    }

    fun testProductionServiceObservesStdlibCachedAfterColdFirstQuery() {
        files("task4_late_stdlib/main.cr" to "require \"late_feature\"")
        files(
            "task4_late_stdlib/stdlib/prelude.cr" to "",
            "task4_late_stdlib/stdlib/late_feature.cr" to "",
        )
        val discoveries = AtomicInteger()
        val stdlib = directory("task4_late_stdlib/stdlib")
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) {
            discoveries.incrementAndGet()
            stdlib
        }
        val service = productionService()

        assertEquals(
            setOf(vf("task4_late_stdlib/main.cr")),
            service.effectiveSources(elementIn("task4_late_stdlib/main.cr")).files,
        )
        assertEquals(0, discoveries.get())

        val cachedRoot = requireNotNull(CrystalStdlibResolver.resolveStdlibPath(project))
        val updated = service.effectiveSources(elementIn("task4_late_stdlib/main.cr"))

        assertTrue(updated.files.contains(requireNotNull(cachedRoot.findChild("prelude.cr"))))
        assertTrue(updated.files.contains(requireNotNull(cachedRoot.findChild("late_feature.cr"))))
        assertEquals("The graph query must not launch discovery", 1, discoveries.get())
        assertEquals(1, service.cacheStats().fullInvalidations)
    }

    fun testProductionServiceObservesBareWildcardAfterStdlibIsCached() {
        files("task4_late_stdlib_wildcard/main.cr" to "require \"extensions/*\"")
        files(
            "task4_late_stdlib_wildcard/stdlib/prelude.cr" to "",
            "task4_late_stdlib_wildcard/stdlib/extensions/late_extension.cr" to "",
        )
        val discoveries = AtomicInteger()
        val stdlib = directory("task4_late_stdlib_wildcard/stdlib")
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) {
            discoveries.incrementAndGet()
            stdlib
        }
        val service = productionService()

        assertEquals(
            setOf(vf("task4_late_stdlib_wildcard/main.cr")),
            service.effectiveSources(elementIn("task4_late_stdlib_wildcard/main.cr")).files,
        )

        val cachedRoot = requireNotNull(CrystalStdlibResolver.resolveStdlibPath(project))
        val updated = service.effectiveSources(elementIn("task4_late_stdlib_wildcard/main.cr"))

        assertTrue(
            updated.files.contains(
                requireNotNull(cachedRoot.findFileByRelativePath("extensions/late_extension.cr")),
            ),
        )
        assertEquals("The graph query must not launch discovery", 1, discoveries.get())
    }

    fun testStdlibRootIdentityTransitionsRebuildPreludeAndBareRequireNodes() {
        files(
            "stdlib_a/prelude.cr" to "",
            "stdlib_a/feature.cr" to "",
            "stdlib_b/prelude.cr" to "",
            "stdlib_b/feature.cr" to "",
            "src/main.cr" to "require \"feature\"",
        )
        var stdlibRoot: VirtualFile? = directory("stdlib_a")
        val service = CrystalRequireGraphService(
            project,
            CrystalRequirePathResolver(project) { stdlibRoot },
        )
        val context = elementIn("src/main.cr")

        val fromA = service.effectiveSources(context)
        assertTrue(fromA.files.contains(vf("stdlib_a/prelude.cr")))
        assertTrue(fromA.files.contains(vf("stdlib_a/feature.cr")))

        stdlibRoot = directory("stdlib_b")
        val fromB = service.effectiveSources(context)
        assertFalse(fromB.files.contains(vf("stdlib_a/prelude.cr")))
        assertFalse(fromB.files.contains(vf("stdlib_a/feature.cr")))
        assertTrue(fromB.files.contains(vf("stdlib_b/prelude.cr")))
        assertTrue(fromB.files.contains(vf("stdlib_b/feature.cr")))

        stdlibRoot = null
        val withoutStdlib = service.effectiveSources(context)
        assertEquals(setOf(vf("src/main.cr")), withoutStdlib.files)
    }

    fun testPreludeFoundationIgnoresProjectLibCollisions() {
        files(
            "stdlib/prelude.cr" to "require \"string\"\nrequire \"int\"\nrequire \"indexable\"",
            "stdlib/string.cr" to "",
            "stdlib/int.cr" to "",
            "stdlib/indexable.cr" to "",
            "src/main.cr" to "",
        )
        val projectString = projectFile("lib/string.cr")
        val projectInt = projectFile("lib/int.cr")
        val projectIndexable = projectFile("lib/indexable.cr")
        try {
            val sources = service().effectiveSources(elementIn("src/main.cr")).files

            assertTrue(sources.contains(vf("stdlib/string.cr")))
            assertTrue(sources.contains(vf("stdlib/int.cr")))
            assertTrue(sources.contains(vf("stdlib/indexable.cr")))
            assertFalse(sources.contains(projectString))
            assertFalse(sources.contains(projectInt))
            assertFalse(sources.contains(projectIndexable))
        } finally {
            removeProjectPath("lib/string.cr")
            removeProjectPath("lib/int.cr")
            removeProjectPath("lib/indexable.cr")
        }
    }

    fun testPreludeFoundationKeepsStdlibProvenanceAfterRelativeEdgeEscapesRoot() {
        files(
            "stdlib/prelude.cr" to "require \"../shared/core\"",
            "shared/core.cr" to "require \"string\"\nrequire \"extensions/*\"",
            "stdlib/string.cr" to "",
            "stdlib/extensions/core.cr" to "",
            "src/main.cr" to "",
            "src/project_main.cr" to "require \"../shared/core\"",
        )
        val projectString = projectFile("lib/string.cr")
        val projectWildcard = projectFile("lib/extensions/project.cr")
        try {
            val graph = service()
            val sources = graph.effectiveSources(elementIn("src/main.cr")).files

            assertTrue(sources.contains(vf("shared/core.cr")))
            assertTrue(sources.contains(vf("stdlib/string.cr")))
            assertTrue(sources.contains(vf("stdlib/extensions/core.cr")))
            assertFalse(sources.contains(projectString))
            assertFalse(sources.contains(projectWildcard))

            val projectSources = graph.effectiveSources(elementIn("src/project_main.cr")).files
            assertTrue(projectSources.contains(vf("shared/core.cr")))
            assertTrue(projectSources.contains(vf("stdlib/string.cr")))
            assertTrue(projectSources.contains(vf("stdlib/extensions/core.cr")))
            assertTrue(projectSources.contains(projectString))
            assertTrue(projectSources.contains(projectWildcard))
        } finally {
            removeProjectPath("lib/string.cr")
            removeProjectPath("lib/extensions")
        }
    }

    fun testFixtureOverrideNestedDisposalRestoresPreviousService() {
        val production = requireNotNull(project.getService(CrystalRequireGraphService::class.java))
        val outerDisposable = Disposer.newDisposable()
        val innerDisposable = Disposer.newDisposable()
        var outerDisposed = false
        var innerDisposed = false
        try {
            val outer = CrystalRequireGraphService.installForTests(
                project,
                CrystalRequirePathResolver(project) { null },
                outerDisposable,
            )
            val inner = CrystalRequireGraphService.installForTests(
                project,
                CrystalRequirePathResolver(project) { directory("stdlib") },
                innerDisposable,
            )

            assertSame(inner, CrystalRequireGraphService.getInstance(project))
            Disposer.dispose(innerDisposable)
            innerDisposed = true
            assertSame(outer, CrystalRequireGraphService.getInstance(project))
            Disposer.dispose(outerDisposable)
            outerDisposed = true
            assertSame(production, CrystalRequireGraphService.getInstance(project))
        } finally {
            if (!innerDisposed) Disposer.dispose(innerDisposable)
            if (!outerDisposed) Disposer.dispose(outerDisposable)
        }
    }

    fun testFixtureOverrideSkipsPreviouslyDisposedOuterFrame() {
        val production = requireNotNull(project.getService(CrystalRequireGraphService::class.java))
        val outerDisposable = Disposer.newDisposable()
        val innerDisposable = Disposer.newDisposable()
        try {
            CrystalRequireGraphService.installForTests(
                project,
                CrystalRequirePathResolver(project) { null },
                outerDisposable,
            )
            val inner = CrystalRequireGraphService.installForTests(
                project,
                CrystalRequirePathResolver(project) { directory("stdlib") },
                innerDisposable,
            )

            Disposer.dispose(outerDisposable)
            assertSame(inner, CrystalRequireGraphService.getInstance(project))
            Disposer.dispose(innerDisposable)
            assertSame(production, CrystalRequireGraphService.getInstance(project))
        } finally {
            // Disposer disposal is idempotent and guarantees cleanup if an assertion fails.
            Disposer.dispose(innerDisposable)
            Disposer.dispose(outerDisposable)
        }
    }

    fun testFixtureOverrideReplacementNeverExposesProductionService() {
        val production = requireNotNull(project.getService(CrystalRequireGraphService::class.java))
        var currentDisposable = Disposer.newDisposable()
        CrystalRequireGraphService.installForTests(
            project,
            CrystalRequirePathResolver(project) { null },
            currentDisposable,
        )
        val exposedProduction = AtomicInteger()
        val start = CountDownLatch(1)
        val readerReady = CountDownLatch(1)
        val replacementsDone = CountDownLatch(1)
        val reads = AtomicInteger()
        val reader = Thread {
            start.await()
            readerReady.countDown()
            do {
                reads.incrementAndGet()
                if (CrystalRequireGraphService.getInstance(project) === production) {
                    exposedProduction.incrementAndGet()
                }
            } while (replacementsDone.count > 0)
        }
        reader.start()
        try {
            start.countDown()
            assertTrue(readerReady.await(10, TimeUnit.SECONDS))
            repeat(25) {
                val nextDisposable = Disposer.newDisposable()
                CrystalRequireGraphService.installForTests(
                    project,
                    CrystalRequirePathResolver(project) { null },
                    nextDisposable,
                )
                val previous = currentDisposable
                currentDisposable = nextDisposable
                Disposer.dispose(previous)
            }
            replacementsDone.countDown()
            reader.join(10_000)
            assertFalse(reader.isAlive)
            assertTrue(reads.get() > 0)
            assertEquals(0, exposedProduction.get())
        } finally {
            replacementsDone.countDown()
            Disposer.dispose(currentDisposable)
            reader.join()
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

    fun testExplicitCrystalAndDecodedRequirePathsReachGraphTargets() {
        files(
            "src/main.cr" to "require \"./feature.cr\"\nrequire \"\\x2e\\x2fdecoded\\u002ecr\"",
            "src/feature.cr" to "",
            "src/decoded.cr" to "",
        )

        val sources = service(stdlib = null).effectiveSources(elementIn("src/main.cr")).files

        assertEquals(setOf(vf("src/main.cr"), vf("src/feature.cr"), vf("src/decoded.cr")), sources)
    }

    fun testDecodedCandidatePathCreationInvalidatesUnresolvedGraph() {
        files("src/main.cr" to "require \"\\x2e\\x2fcreated\\u002ecr\"")
        val service = service(stdlib = null)
        assertEquals(setOf(vf("src/main.cr")), service.effectiveSources(elementIn("src/main.cr")).files)
        val event = VFileCreateEvent(this, directory("src"), "created.cr", false, null, null, null)
        val created = myFixture.addFileToProject("src/created.cr", "").virtualFile

        service.handleVfsEvents(listOf(event))

        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(created))
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
        flushFixtureFiles()

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

    fun testCleanCachedNodeSkipsFingerprintReCollectionOnRepeatedQuery() {
        files(
            "stdlib/prelude.cr" to "require \"./string\"\nrequire \"./int\"",
            "stdlib/string.cr" to "",
            "stdlib/int.cr" to "",
            "src/main.cr" to "require \"./feature\"\nrequire \"./helper\"",
            "src/feature.cr" to "",
            "src/helper.cr" to "",
        )
        var collections = 0
        val service = service(
            validationMode = CrystalRequireGraphService.ValidationMode.EVENT_DRIVEN,
            collector = {
                collections++
                CrystalRequireCollector.collect(it)
            },
        )
        val context = elementIn("src/main.cr")
        service.effectiveSources(context)
        val initialCollections = collections
        val initialVisits = service.closureNodeVisits()
        assertEquals(6, initialCollections)

        service.effectiveSources(context)

        assertEquals("Clean cached nodes must not be collected again", initialCollections, collections)
        assertEquals("Clean effective snapshots must not traverse either closure", initialVisits, service.closureNodeVisits())
    }

    fun testDirtyNodeRevalidatesFingerprintInsideQueryReadAction() {
        files("src/main.cr" to "def value\n1\nend")
        var collections = 0
        val service = service(
            stdlib = null,
            validationMode = CrystalRequireGraphService.ValidationMode.EVENT_DRIVEN,
            collector = {
                collections++
                CrystalRequireCollector.collect(it)
            },
        )
        val context = elementIn("src/main.cr")
        val first = service.effectiveSources(context)
        val firstIdentity = service.cacheIdentity(vf("src/main.cr"))
        val baseline = service.cacheStats()
        val baselineVisits = service.closureNodeVisits()
        assertEquals(1, collections)

        replaceText("src/main.cr", "def value\n2\nend")
        service.handleVfsEvents(listOf(VFileContentChangeEvent(this, vf("src/main.cr"), 1, 2)))

        val second = service.effectiveSources(context)
        val secondIdentity = service.cacheIdentity(vf("src/main.cr"))
        assertSame("Unchanged fingerprint must reuse the cached effective snapshot", first, second)
        assertSame(firstIdentity.node, secondIdentity.node)
        assertSame(firstIdentity.closure, secondIdentity.closure)
        assertSame(firstIdentity.sources, secondIdentity.sources)
        val after = service.cacheStats()
        assertEquals("The dirty node must be collected exactly once", 2, collections)
        assertEquals(
            "An unchanged require fingerprint must not rebuild the node",
            baseline.nodeBuilds, after.nodeBuilds,
        )
        assertEquals(baseline.closureBuilds, after.closureBuilds)
        assertEquals(baselineVisits + 1, service.closureNodeVisits())

        service.effectiveSources(context)
        assertEquals("Validation must restore the clean fast path", baselineVisits + 1, service.closureNodeVisits())
    }

    fun testUnchangedSharedDependencyRestoresEveryOwningClosureFastPath() {
        files(
            "src/left.cr" to "require \"./shared\"",
            "src/right.cr" to "require \"./shared\"",
            "src/shared.cr" to "def value\n1\nend",
        )
        val service = service(stdlib = null, validationMode = CrystalRequireGraphService.ValidationMode.EVENT_DRIVEN)
        service.effectiveSources(elementIn("src/left.cr"))
        val right = service.effectiveSources(elementIn("src/right.cr"))
        replaceText("src/shared.cr", "def value\n2\nend")
        service.handleVfsEvents(listOf(VFileContentChangeEvent(this, vf("src/shared.cr"), 1, 2)))

        service.effectiveSources(elementIn("src/left.cr"))
        val visits = service.closureNodeVisits()

        assertSame(right, service.effectiveSources(elementIn("src/right.cr")))
        assertEquals(visits, service.closureNodeVisits())
    }

    fun testClearingOneSharedDirtyReasonPreservesAnotherReason() {
        files(
            "src/all.cr" to "require \"./first\"\nrequire \"./second\"",
            "src/first_owner.cr" to "require \"./first\"",
            "src/first.cr" to "def first\n1\nend",
            "src/second.cr" to "def second\n1\nend",
        )
        val service = service(stdlib = null, validationMode = CrystalRequireGraphService.ValidationMode.EVENT_DRIVEN)
        service.effectiveSources(elementIn("src/all.cr"))
        service.effectiveSources(elementIn("src/first_owner.cr"))
        replaceText("src/first.cr", "def first\n2\nend")
        replaceText("src/second.cr", "def second\n2\nend")
        service.handleVfsEvents(
            listOf(
                VFileContentChangeEvent(this, vf("src/first.cr"), 1, 2),
                VFileContentChangeEvent(this, vf("src/second.cr"), 1, 2),
            ),
        )

        service.effectiveSources(elementIn("src/first_owner.cr"))
        val visits = service.closureNodeVisits()
        service.effectiveSources(elementIn("src/all.cr"))

        assertTrue("The second dirty reason must still force validation", service.closureNodeVisits() > visits)
    }

    fun testDirtyRequireEditRebuildsNodeAndEffectiveSnapshot() {
        files(
            "src/main.cr" to "require \"./old_feature\"",
            "src/old_feature.cr" to "",
            "src/new_feature.cr" to "",
        )
        var collections = 0
        val service = service(
            stdlib = null,
            validationMode = CrystalRequireGraphService.ValidationMode.EVENT_DRIVEN,
            collector = {
                collections++
                CrystalRequireCollector.collect(it)
            },
        )
        val first = service.effectiveSources(elementIn("src/main.cr"))
        val baseline = service.cacheStats()
        val baselineVisits = service.closureNodeVisits()
        assertEquals(2, collections)

        replaceText("src/main.cr", "require \"./new_feature\"")
        service.handleVfsEvents(listOf(VFileContentChangeEvent(this, vf("src/main.cr"), 1, 2)))

        val second = service.effectiveSources(elementIn("src/main.cr"))
        assertNotSame(first, second)
        assertFalse(second.files.contains(vf("src/old_feature.cr")))
        assertTrue(second.files.contains(vf("src/new_feature.cr")))
        assertEquals("Only the dirty materialized node must be collected again", 4, collections)
        assertEquals(baseline.nodeBuilds + 2, service.cacheStats().nodeBuilds)
        assertEquals(baseline.closureBuilds + 1, service.cacheStats().closureBuilds)
        assertTrue(service.closureNodeVisits() > baselineVisits)
    }

    fun testUnsavedTopLevelRequireEditInvalidatesDependentClosure() {
        files(
            "task4_unsaved_require/main.cr" to "require \"./old_feature\"",
            "task4_unsaved_require/old_feature.cr" to "",
            "task4_unsaved_require/new_feature.cr" to "",
        )
        myFixture.configureFromTempProjectFile("task4_unsaved_require/main.cr")
        val service = productionService()
        val initial = service.effectiveSources(myFixture.file)
        val initialStats = service.cacheStats()
        assertTrue(initial.files.contains(vf("task4_unsaved_require/old_feature.cr")))

        replaceEditorText("require \"./new_feature\"")

        assertEquals(initialStats.targetedInvalidations + 1, service.cacheStats().targetedInvalidations)
        assertEquals(
            "The committed document edit must remain unsaved for this regression",
            "require \"./old_feature\"",
            String(vf("task4_unsaved_require/main.cr").contentsToByteArray()),
        )
        val updated = service.effectiveSources(myFixture.file)
        assertFalse(updated.files.contains(vf("task4_unsaved_require/old_feature.cr")))
        assertTrue(updated.files.contains(vf("task4_unsaved_require/new_feature.cr")))
    }

    fun testUnsavedFullMacroControlledRequireRemovalUpdatesClosureImmediately() {
        files(
            "task4_unsaved_macro_remove/main.cr" to "{% if flag?(:unix) %}\nrequire \"./feature\"\n{% end %}",
            "task4_unsaved_macro_remove/feature.cr" to "",
        )
        myFixture.configureFromTempProjectFile("task4_unsaved_macro_remove/main.cr")
        val service = productionService()
        assertTrue(service.effectiveSources(myFixture.file).files.contains(vf("task4_unsaved_macro_remove/feature.cr")))

        replaceEditorText("")

        assertFalse(service.effectiveSources(myFixture.file).files.contains(vf("task4_unsaved_macro_remove/feature.cr")))
    }

    fun testUnsavedFullMacroControlledRequireReplacementUpdatesClosureImmediately() {
        files(
            "task4_unsaved_macro_replace/main.cr" to "{% if flag?(:unix) %}\nrequire \"./old_feature\"\n{% end %}",
            "task4_unsaved_macro_replace/old_feature.cr" to "",
            "task4_unsaved_macro_replace/new_feature.cr" to "",
        )
        myFixture.configureFromTempProjectFile("task4_unsaved_macro_replace/main.cr")
        val service = productionService()
        service.effectiveSources(myFixture.file)

        replaceEditorText("{% if flag?(:unix) %}\nrequire \"./new_feature\"\n{% end %}")

        val updated = service.effectiveSources(myFixture.file)
        assertFalse(updated.files.contains(vf("task4_unsaved_macro_replace/old_feature.cr")))
        assertTrue(updated.files.contains(vf("task4_unsaved_macro_replace/new_feature.cr")))
    }

    fun testUnsavedMethodBodyEditRetainsRequireCaches() {
        files(
            "task4_unsaved_method/main.cr" to "require \"./feature\"\ndef value\n1\nend",
            "task4_unsaved_method/feature.cr" to "",
        )
        myFixture.configureFromTempProjectFile("task4_unsaved_method/main.cr")
        val service = productionService()
        val initial = service.effectiveSources(myFixture.file)
        val initialStats = service.cacheStats()

        replaceEditorText("require \"./feature\"\ndef value\n2\nend")

        assertSame(initial, service.effectiveSources(myFixture.file))
        assertEquals(initialStats, service.cacheStats())
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

    fun testRequiredFileRequireEditInvalidatesOnlyTransitiveDependents() {
        files(
            "task4_required_edit/main.cr" to "require \"./feature\"",
            "task4_required_edit/feature.cr" to "require \"./old_extension\"",
            "task4_required_edit/old_extension.cr" to "",
            "task4_required_edit/new_extension.cr" to "",
            "task4_required_edit/unrelated.cr" to "require \"./stable\"",
            "task4_required_edit/stable.cr" to "",
        )
        val service = productionService()
        val main = service.effectiveSources(elementIn("task4_required_edit/main.cr"))
        val feature = service.effectiveSources(elementIn("task4_required_edit/feature.cr"))
        val unrelated = service.effectiveSources(elementIn("task4_required_edit/unrelated.cr"))
        val before = service.cacheStats()

        replaceText("task4_required_edit/feature.cr", "require \"./new_extension\"")

        assertEquals(before.targetedInvalidations + 1, service.cacheStats().targetedInvalidations)
        val updatedMain = service.effectiveSources(elementIn("task4_required_edit/main.cr"))
        assertNotSame(main, updatedMain)
        assertFalse(updatedMain.files.contains(vf("task4_required_edit/old_extension.cr")))
        assertTrue(updatedMain.files.contains(vf("task4_required_edit/new_extension.cr")))
        assertNotSame(feature, service.effectiveSources(elementIn("task4_required_edit/feature.cr")))
        assertSame(unrelated, service.effectiveSources(elementIn("task4_required_edit/unrelated.cr")))
    }

    fun testSavedOrdinaryRequiredFileEditRetainsCaches() {
        files(
            "task4_saved_method/main.cr" to "require \"./feature\"",
            "task4_saved_method/feature.cr" to "def value\n1\nend",
        )
        val service = productionService()
        val main = service.effectiveSources(elementIn("task4_saved_method/main.cr"))
        val before = service.cacheStats()

        replaceText("task4_saved_method/feature.cr", "def value\n2\nend")
        service.handleVfsEvents(
            listOf(VFileContentChangeEvent(this, vf("task4_saved_method/feature.cr"), 1, 2)),
        )

        assertEquals(before, service.cacheStats())
        assertSame(main, service.effectiveSources(elementIn("task4_saved_method/main.cr")))
    }

    fun testRequiredFileRenameInvalidatesOnlyDependentClosure() {
        files(
            "task4_required_rename/main.cr" to "require \"./feature\"",
            "task4_required_rename/feature.cr" to "",
            "task4_required_rename/unrelated.cr" to "",
        )
        val service = productionService()
        val main = service.effectiveSources(elementIn("task4_required_rename/main.cr"))
        val unrelated = service.effectiveSources(elementIn("task4_required_rename/unrelated.cr"))
        val feature = vf("task4_required_rename/feature.cr")

        ApplicationManager.getApplication().runWriteAction { feature.rename(this, "renamed.cr") }

        val updated = service.effectiveSources(elementIn("task4_required_rename/main.cr"))
        assertNotSame(main, updated)
        assertFalse(updated.files.contains(feature))
        assertSame(unrelated, service.effectiveSources(elementIn("task4_required_rename/unrelated.cr")))
    }

    fun testRequiredFileDeleteInvalidatesDependentClosure() {
        files(
            "task4_required_delete/main.cr" to "require \"./feature\"",
            "task4_required_delete/feature.cr" to "",
        )
        val service = productionService()
        val feature = vf("task4_required_delete/feature.cr")
        assertTrue(service.effectiveSources(elementIn("task4_required_delete/main.cr")).files.contains(feature))

        ApplicationManager.getApplication().runWriteAction { feature.delete(this) }

        assertFalse(service.effectiveSources(elementIn("task4_required_delete/main.cr")).files.contains(feature))
    }

    fun testRequiredFileMoveInvalidatesDependentClosure() {
        files(
            "task4_required_move/main.cr" to "require \"./feature\"",
            "task4_required_move/feature.cr" to "",
            "task4_required_move/archive/keep.txt" to "",
        )
        val service = productionService()
        val feature = vf("task4_required_move/feature.cr")
        assertTrue(service.effectiveSources(elementIn("task4_required_move/main.cr")).files.contains(feature))

        ApplicationManager.getApplication().runWriteAction {
            feature.move(this, directory("task4_required_move/archive"))
        }

        assertFalse(service.effectiveSources(elementIn("task4_required_move/main.cr")).files.contains(feature))
    }

    fun testShardManifestAndLockChangesFullyInvalidateLazily() {
        removeProjectPath("shard.yml")
        removeProjectPath("shard.lock")
        files("task4_shard_metadata/main.cr" to "")
        val shardYaml = projectFile("shard.yml")
        val shardLock = projectFile("shard.lock")
        try {
            val service = productionService()
            service.effectiveSources(elementIn("task4_shard_metadata/main.cr"))
            var before = service.cacheStats()

            ApplicationManager.getApplication().runWriteAction { VfsUtil.saveText(shardYaml, "name: changed") }

            var invalidated = service.cacheStats()
            assertEquals(before.fullInvalidations + 1, invalidated.fullInvalidations)
            assertEquals(before.nodeBuilds, invalidated.nodeBuilds)
            assertEquals(before.closureBuilds, invalidated.closureBuilds)
            service.effectiveSources(elementIn("task4_shard_metadata/main.cr"))
            before = service.cacheStats()

            ApplicationManager.getApplication().runWriteAction { VfsUtil.saveText(shardLock, "version: 2") }

            invalidated = service.cacheStats()
            assertEquals(before.fullInvalidations + 1, invalidated.fullInvalidations)
            assertEquals(before.nodeBuilds, invalidated.nodeBuilds)
            assertEquals(before.closureBuilds, invalidated.closureBuilds)
        } finally {
            removeProjectPath("shard.yml")
            removeProjectPath("shard.lock")
        }
    }

    fun testProjectRootChangeFullyInvalidatesLazily() {
        files("task4_root_change/main.cr" to "")
        val service = productionService()
        service.effectiveSources(elementIn("task4_root_change/main.cr"))
        val before = service.cacheStats()

        val event = ModuleRootEventImpl(project, false)
        ApplicationManager.getApplication().runWriteAction {
            project.messageBus.syncPublisher(ModuleRootListener.TOPIC).beforeRootsChange(event)
            project.messageBus.syncPublisher(ModuleRootListener.TOPIC).rootsChanged(event)
        }

        val invalidated = service.cacheStats()
        assertEquals(before.fullInvalidations + 1, invalidated.fullInvalidations)
        assertEquals(before.nodeBuilds, invalidated.nodeBuilds)
        assertEquals(before.closureBuilds, invalidated.closureBuilds)
        service.effectiveSources(elementIn("task4_root_change/main.cr"))
        assertTrue(service.cacheStats().nodeBuilds > invalidated.nodeBuilds)
    }

    fun testLibRootCreationFullyInvalidatesUnresolvedBareRequiresLazily() {
        projectFile("lib/.keep")
        removeProjectPath("lib/task4_require_graph_test")
        files("src/main.cr" to "require \"task4_require_graph_test\"")
        try {
            val service = CrystalRequireGraphService(project, CrystalRequirePathResolver(project) { null })
            val initial = service.effectiveSources(elementIn("src/main.cr"))
            val before = service.cacheStats()
            assertFalse(initial.files.any { it.name == "task4_require_graph_test.cr" })

            val createEvent = VFileCreateEvent(
                this,
                wildcardDirectory("lib", projectBaseTarget = true),
                "task4_require_graph_test",
                true,
                null,
                null,
                null,
            )
            val shard = projectFile("lib/task4_require_graph_test/src/task4_require_graph_test.cr")
            service.handleVfsEvents(listOf(createEvent))

            val invalidated = service.cacheStats()
            assertEquals(before.fullInvalidations + 1, invalidated.fullInvalidations)
            assertEquals(before.nodeBuilds, invalidated.nodeBuilds)
            assertEquals(before.closureBuilds, invalidated.closureBuilds)
            assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(shard))
        } finally {
            removeProjectPath("lib/task4_require_graph_test")
            removeProjectPath("lib/.keep")
        }
    }

    fun testLocalWildcardCreateDeleteRenameAndMoveInvalidateOnlyOwningClosure() {
        files(
            "task4_local/main.cr" to "require \"./models/*\"",
            "task4_local/models/initial.cr" to "",
            "task4_local/archive/keep.txt" to "",
            "task4_local/unrelated.cr" to "require \"./other/*\"",
            "task4_local/other/stable.cr" to "",
        )
        val service = productionService()

        exerciseWildcardLifecycle(
            service,
            "task4_local/main.cr",
            "task4_local/models",
            "task4_local/archive",
            manualEvents = false,
            unrelatedPath = "task4_local/unrelated.cr",
        )
    }

    fun testShardWildcardCreateDeleteRenameAndMoveInvalidateOnlyOwningClosure() {
        files(
            "task4_shard_context/main.cr" to "require \"task4_shard/src/extensions/**\"",
            "task4_shard_context/unrelated.cr" to "require \"./other/*\"",
            "task4_shard_context/other/stable.cr" to "",
        )
        removeProjectPath("lib/task4_shard")
        try {
            projectFile("lib/task4_shard/src/extensions/initial.cr")
            projectFile("lib/task4_shard/src/archive/keep.txt")
            val service = productionService()

            exerciseWildcardLifecycle(
                service,
                "task4_shard_context/main.cr",
                "lib/task4_shard/src/extensions",
                "lib/task4_shard/src/archive",
                manualEvents = false,
                unrelatedPath = "task4_shard_context/unrelated.cr",
                projectBaseTarget = true,
            )
        } finally {
            removeProjectPath("lib/task4_shard")
        }
    }

    fun testStdlibWildcardCreateDeleteRenameAndMoveInvalidateOnlyOwningClosure() {
        files(
            "stdlib/prelude.cr" to "",
            "stdlib/extensions/initial.cr" to "",
            "stdlib/archive/keep.txt" to "",
            "task4_stdlib_context/main.cr" to "require \"extensions/**\"",
            "task4_stdlib_context/unrelated.cr" to "require \"./other/*\"",
            "task4_stdlib_context/other/stable.cr" to "",
        )
        val service = service()

        exerciseWildcardLifecycle(
            service,
            "task4_stdlib_context/main.cr",
            "stdlib/extensions",
            "stdlib/archive",
            manualEvents = true,
            unrelatedPath = "task4_stdlib_context/unrelated.cr",
        )
    }

    fun testMissingWildcardDirectoryWatchesNearestExistingParent() {
        files(
            "task4_missing_watch/main.cr" to "require \"./models/*\"",
            "task4_missing_watch/keep.txt" to "",
        )
        val service = productionService()
        assertFalse(
            service.effectiveSources(elementIn("task4_missing_watch/main.cr")).files.any { it.name == "created.cr" },
        )

        myFixture.addFileToProject("task4_missing_watch/models/created.cr", "")

        assertTrue(
            service.effectiveSources(elementIn("task4_missing_watch/main.cr")).files
                .contains(vf("task4_missing_watch/models/created.cr")),
        )
    }

    fun testCreateIntoUnresolvedRelativeExactCandidateInvalidatesOwner() {
        files("src/main.cr" to "require \"./feature\"", "src/unrelated.cr" to "")
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("src/main.cr"))
        val unrelated = service.effectiveSources(elementIn("src/unrelated.cr"))
        val event = VFileCreateEvent(this, directory("src"), "feature.cr", false, null, null, null)

        val feature = myFixture.addFileToProject("src/feature.cr", "").virtualFile
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertNotSame(initial, updated)
        assertTrue(updated.files.contains(feature))
        assertSame(unrelated, service.effectiveSources(elementIn("src/unrelated.cr")))
    }

    fun testCopyIntoUnresolvedRelativeExactCandidateInvalidatesOwner() {
        files("src/main.cr" to "require \"./feature\"", "templates/feature.cr" to "")
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("src/main.cr"))
        val source = vf("templates/feature.cr")
        val event = VFileCopyEvent(this, source, directory("src"), "feature.cr")

        val feature = ApplicationManager.getApplication().runWriteAction<VirtualFile> {
            VfsUtil.copyFile(this, source, directory("src"), "feature.cr")
        }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertNotSame(initial, updated)
        assertTrue(updated.files.contains(feature))
    }

    fun testRenameIntoUnresolvedRelativeExactCandidateInvalidatesOwner() {
        files("src/main.cr" to "require \"./feature\"", "src/pending.cr" to "")
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("src/main.cr"))
        val pending = vf("src/pending.cr")
        val event = VFilePropertyChangeEvent(
            this,
            pending,
            VirtualFile.PROP_NAME,
            "pending.cr",
            "feature.cr",
        )

        ApplicationManager.getApplication().runWriteAction { pending.rename(this, "feature.cr") }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertNotSame(initial, updated)
        assertTrue(updated.files.contains(pending))
    }

    fun testMoveIntoUnresolvedRelativeExactCandidateInvalidatesOwner() {
        files("src/main.cr" to "require \"./feature\"", "archive/feature.cr" to "")
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("src/main.cr"))
        val pending = vf("archive/feature.cr")
        val event = VFileMoveEvent(this, pending, directory("src"))

        ApplicationManager.getApplication().runWriteAction { pending.move(this, directory("src")) }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertNotSame(initial, updated)
        assertTrue(updated.files.contains(pending))
    }

    fun testCreateIntoUnresolvedStdlibExactCandidateInvalidatesOwner() {
        files(
            "stdlib/prelude.cr" to "",
            "src/main.cr" to "require \"task4_stdlib_exact\"",
        )
        val service = service()
        val initial = service.effectiveSources(elementIn("src/main.cr"))
        val event = VFileCreateEvent(
            this,
            directory("stdlib"),
            "task4_stdlib_exact.cr",
            false,
            null,
            null,
            null,
        )

        val feature = myFixture.addFileToProject("stdlib/task4_stdlib_exact.cr", "").virtualFile
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertNotSame(initial, updated)
        assertTrue(updated.files.contains(feature))
    }

    fun testCreateIntoUnresolvedShardExactCandidateInvalidatesOwner() {
        projectFile("lib/task4_exact/.keep")
        removeProjectPath("lib/task4_exact/src")
        files("src/main.cr" to "require \"task4_exact\"")
        try {
            val service = service(stdlib = null)
            val initial = service.effectiveSources(elementIn("src/main.cr"))
            val parent = wildcardDirectory("lib/task4_exact", projectBaseTarget = true)
            val event = VFileCreateEvent(this, parent, "src", true, null, null, null)

            val feature = projectFile("lib/task4_exact/src/task4_exact.cr")
            service.handleVfsEvents(listOf(event))

            val updated = service.effectiveSources(elementIn("src/main.cr"))
            assertNotSame(initial, updated)
            assertTrue(updated.files.contains(feature))
        } finally {
            removeProjectPath("lib/task4_exact")
        }
    }

    fun testHigherPriorityProjectCandidateCreationReplacesStdlibExactTarget() {
        projectFile("lib/.keep")
        removeProjectPath("lib/task4_priority.cr")
        files(
            "stdlib/prelude.cr" to "",
            "stdlib/task4_priority.cr" to "",
            "src/main.cr" to "require \"task4_priority\"",
        )
        try {
            val service = service()
            val initial = service.effectiveSources(elementIn("src/main.cr"))
            val stdlibTarget = vf("stdlib/task4_priority.cr")
            assertTrue(initial.files.contains(stdlibTarget))
            val event = VFileCreateEvent(
                this,
                wildcardDirectory("lib", projectBaseTarget = true),
                "task4_priority.cr",
                false,
                null,
                null,
                null,
            )

            val projectTarget = projectFile("lib/task4_priority.cr")
            service.handleVfsEvents(listOf(event))

            val updated = service.effectiveSources(elementIn("src/main.cr"))
            assertNotSame(initial, updated)
            assertTrue(updated.files.contains(projectTarget))
            assertFalse(updated.files.contains(stdlibTarget))
        } finally {
            removeProjectPath("lib/task4_priority.cr")
            removeProjectPath("lib/.keep")
        }
    }

    fun testExactCandidateMatchesDirectoryRenamedIntoPlaceAsAncestor() {
        files(
            "app/main.cr" to "require \"../shared/feature\"",
            "pending/feature.cr" to "",
        )
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("app/main.cr"))
        val pending = directory("pending")
        val event = VFilePropertyChangeEvent(
            this,
            pending,
            VirtualFile.PROP_NAME,
            "pending",
            "shared",
        )

        ApplicationManager.getApplication().runWriteAction { pending.rename(this, "shared") }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("app/main.cr"))
        assertNotSame(initial, updated)
        assertTrue(updated.files.any { it.path.endsWith("/shared/feature.cr") })
    }

    fun testExactCandidateMatchesContainingDirectoryRenamedAway() {
        files(
            "app/main.cr" to "require \"../shared/feature\"",
            "shared/feature.cr" to "",
        )
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("app/main.cr"))
        val shared = directory("shared")
        val event = VFilePropertyChangeEvent(
            this,
            shared,
            VirtualFile.PROP_NAME,
            "shared",
            "renamed",
        )

        ApplicationManager.getApplication().runWriteAction { shared.rename(this, "renamed") }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("app/main.cr"))
        assertNotSame(initial, updated)
        assertFalse(updated.files.any { it.path.endsWith("/renamed/feature.cr") })
    }

    fun testExactCandidateMatchesContainingDirectoryDelete() {
        files(
            "app/main.cr" to "require \"../shared/feature\"",
            "shared/feature.cr" to "",
        )
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("app/main.cr"))
        val shared = directory("shared")
        val event = VFileDeleteEvent(this, shared)

        ApplicationManager.getApplication().runWriteAction { shared.delete(this) }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("app/main.cr"))
        assertNotSame(initial, updated)
        assertFalse(updated.files.any { it.name == "feature.cr" })
    }

    fun testWildcardMatchesDirectoryRenamedIntoPlaceAsAncestor() {
        files(
            "app/main.cr" to "require \"../shared/models/*\"",
            "pending/models/feature.cr" to "",
        )
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("app/main.cr"))
        val pending = directory("pending")
        val event = VFilePropertyChangeEvent(
            this,
            pending,
            VirtualFile.PROP_NAME,
            "pending",
            "shared",
        )

        ApplicationManager.getApplication().runWriteAction { pending.rename(this, "shared") }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("app/main.cr"))
        assertNotSame(initial, updated)
        assertTrue(updated.files.any { it.path.endsWith("/shared/models/feature.cr") })
    }

    fun testWildcardMatchesContainingDirectoryMoveAway() {
        files(
            "app/main.cr" to "require \"../shared/models/*\"",
            "shared/models/feature.cr" to "",
            "archive/keep.txt" to "",
        )
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("app/main.cr"))
        val shared = directory("shared")
        val event = VFileMoveEvent(this, shared, directory("archive"))

        ApplicationManager.getApplication().runWriteAction { shared.move(this, directory("archive")) }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("app/main.cr"))
        assertNotSame(initial, updated)
        assertFalse(updated.files.any { it.path.endsWith("/archive/shared/models/feature.cr") })
    }

    fun testWildcardModesFilterIrrelevantStructuralEvents() {
        files(
            "src/direct.cr" to "require \"./models/*\"",
            "src/recursive.cr" to "require \"./models/**\"",
            "src/models/existing.cr" to "",
            "src/models/nested/deep.cr" to "",
            "src/models/nested/readme.md" to "",
        )
        val service = service(stdlib = null)
        val direct = service.effectiveSources(elementIn("src/direct.cr"))
        val recursive = service.effectiveSources(elementIn("src/recursive.cr"))
        val before = service.cacheStats()

        service.handleVfsEvents(
            listOf(
                VFileCreateEvent(this, directory("src/models"), "readme.md", false, null, null, null),
                VFileCreateEvent(this, directory("src/models/nested"), "other.md", false, null, null, null),
            ),
        )

        assertEquals(before, service.cacheStats())
        assertSame(direct, service.effectiveSources(elementIn("src/direct.cr")))
        assertSame(recursive, service.effectiveSources(elementIn("src/recursive.cr")))
    }

    fun testDirectWildcardIgnoresDeepCrystalEventWhileRecursiveWildcardInvalidates() {
        files(
            "src/direct.cr" to "require \"./models/*\"",
            "src/recursive.cr" to "require \"./models/**\"",
            "src/models/existing.cr" to "",
            "src/models/nested/deep.cr" to "",
        )
        val service = service(stdlib = null)
        val direct = service.effectiveSources(elementIn("src/direct.cr"))
        val recursive = service.effectiveSources(elementIn("src/recursive.cr"))

        service.handleVfsEvents(
            listOf(VFileCreateEvent(this, directory("src/models/nested"), "new.cr", false, null, null, null)),
        )

        assertSame(direct, service.effectiveSources(elementIn("src/direct.cr")))
        assertNotSame(recursive, service.effectiveSources(elementIn("src/recursive.cr")))
    }

    fun testNearestParentWatchIgnoresSiblingAndMatchesIntendedPath() {
        files(
            "src/main.cr" to "require \"./models/generated/*\"",
            "src/other/existing.cr" to "",
        )
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("src/main.cr"))
        val before = service.cacheStats()

        service.handleVfsEvents(
            listOf(VFileCreateEvent(this, directory("src/other"), "sibling.cr", false, null, null, null)),
        )

        assertEquals(before, service.cacheStats())
        assertSame(initial, service.effectiveSources(elementIn("src/main.cr")))

        val intended = VFileCreateEvent(this, directory("src"), "models", true, null, null, null)
        myFixture.addFileToProject("src/models/generated/feature.cr", "")
        service.handleVfsEvents(listOf(intended))
        assertTrue(
            service.effectiveSources(elementIn("src/main.cr")).files
                .contains(vf("src/models/generated/feature.cr")),
        )
    }

    fun testCopyIntoWildcardTargetInvalidatesOwner() {
        files(
            "src/main.cr" to "require \"./models/*\"",
            "src/models/existing.cr" to "",
            "templates/copied.cr" to "",
        )
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("src/main.cr"))
        val source = vf("templates/copied.cr")
        val event = VFileCopyEvent(this, source, directory("src/models"), "copied.cr")

        val copied = ApplicationManager.getApplication().runWriteAction<VirtualFile> {
            VfsUtil.copyFile(this, source, directory("src/models"), "copied.cr")
        }
        service.handleVfsEvents(listOf(event))

        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertNotSame(initial, updated)
        assertTrue(updated.files.contains(copied))
    }

    fun testMixedBatchDoesNotUseContentPathForWildcardMatching() {
        files(
            "src/main.cr" to "require \"./models/*\"",
            "src/models/existing.cr" to "",
            "src/unrelated/keep.txt" to "",
        )
        val service = service(stdlib = null)
        val initial = service.effectiveSources(elementIn("src/main.cr"))
        val before = service.cacheStats()

        service.handleVfsEvents(
            listOf(
                VFileContentChangeEvent(this, vf("src/models/existing.cr"), 1, 2),
                VFileCreateEvent(this, directory("src/unrelated"), "note.txt", false, null, null, null),
            ),
        )

        assertEquals(before, service.cacheStats())
        assertSame(initial, service.effectiveSources(elementIn("src/main.cr")))
    }

    fun testServiceHasNoGlobalContentRevalidationBarrierOrExecutor() {
        assertFalse(
            CrystalRequireGraphService::class.java.declaredFields.any {
                it.name == "pendingContentRevalidations" || it.name == "contentExecutor"
            },
        )
        assertFalse(
            CrystalRequireGraphService::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.contains(Executor::class.java)
            },
        )
    }

    fun testVfsContentCallbackValidatesFingerprintLazilyInQueriedClosure() {
        files(
            "src/main.cr" to "require \"./old_feature\"",
            "src/old_feature.cr" to "",
            "src/new_feature.cr" to "",
        )
        val service = service(stdlib = null)
        service.effectiveSources(elementIn("src/main.cr"))
        replaceText("src/main.cr", "require \"./new_feature\"")
        val beforeCallback = service.cacheStats()

        service.handleVfsEvents(listOf(VFileContentChangeEvent(this, vf("src/main.cr"), 1, 2)))

        assertEquals(beforeCallback, service.cacheStats())
        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertFalse(updated.files.contains(vf("src/old_feature.cr")))
        assertTrue(updated.files.contains(vf("src/new_feature.cr")))
    }

    fun testUnrelatedDirtyNodeDoesNotGateOrRebuildQueriedClosure() {
        files(
            "src/main.cr" to "require \"./feature\"",
            "src/feature.cr" to "",
            "src/unrelated.cr" to "require \"./old_dependency\"",
            "src/old_dependency.cr" to "",
            "src/new_dependency.cr" to "",
        )
        val service = service(
            stdlib = null,
            validationMode = CrystalRequireGraphService.ValidationMode.EVENT_DRIVEN,
        )
        val main = service.effectiveSources(elementIn("src/main.cr"))
        service.effectiveSources(elementIn("src/unrelated.cr"))
        replaceText("src/unrelated.cr", "require \"./new_dependency\"")
        val before = service.cacheStats()
        val visitsBefore = service.closureNodeVisits()

        service.handleVfsEvents(listOf(VFileContentChangeEvent(this, vf("src/unrelated.cr"), 1, 2)))

        assertSame(main, service.effectiveSources(elementIn("src/main.cr")))
        assertEquals(before, service.cacheStats())
        assertEquals("An unrelated dirty node must not break the root fast path", visitsBefore, service.closureNodeVisits())
        val updatedUnrelated = service.effectiveSources(elementIn("src/unrelated.cr"))
        assertFalse(updatedUnrelated.files.contains(vf("src/old_dependency.cr")))
        assertTrue(updatedUnrelated.files.contains(vf("src/new_dependency.cr")))
    }

    fun testContentChangeAndQueryUnderWriteAccessUseNoBackgroundReadWork() {
        files(
            "src/main.cr" to "require \"./old_feature\"",
            "src/old_feature.cr" to "",
            "src/new_feature.cr" to "",
        )
        val service = CrystalRequireGraphService(
            project,
            CrystalRequirePathResolver(project) { null },
        )
        service.effectiveSources(elementIn("src/main.cr"))
        replaceText("src/main.cr", "require \"./new_feature\"")

        var updated: CrystalEffectiveSourceSet? = null
        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                service.handleVfsEvents(listOf(VFileContentChangeEvent(this, vf("src/main.cr"), 1, 2)))
                updated = service.effectiveSources(elementIn("src/main.cr"))
            }
        }

        assertFalse(requireNotNull(updated).files.contains(vf("src/old_feature.cr")))
        assertTrue(requireNotNull(updated).files.contains(vf("src/new_feature.cr")))
    }

    fun testCancellationWhileValidatingDirtyClosurePropagatesAndLeavesItRetryable() {
        files(
            "src/main.cr" to "require \"./old_feature\"",
            "src/old_feature.cr" to "",
            "src/new_feature.cr" to "",
        )
        val service = service(stdlib = null)
        service.effectiveSources(elementIn("src/main.cr"))
        replaceText("src/main.cr", "require \"./new_feature\"")
        service.handleVfsEvents(listOf(VFileContentChangeEvent(this, vf("src/main.cr"), 1, 2)))
        val indicator = ProgressIndicatorBase().apply { cancel() }

        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().executeProcessUnderProgress(
                { service.effectiveSources(elementIn("src/main.cr")) },
                indicator,
            )
        }

        val updated = service.effectiveSources(elementIn("src/main.cr"))
        assertFalse(updated.files.contains(vf("src/old_feature.cr")))
        assertTrue(updated.files.contains(vf("src/new_feature.cr")))
    }

    fun testCanceledMixedBatchStillProcessesStructuralInvalidation() {
        files(
            "src/main.cr" to "require \"./created\"",
            "src/edited.cr" to "require \"./old_feature\"",
            "src/old_feature.cr" to "",
            "src/new_feature.cr" to "",
        )
        val service = service(stdlib = null)
        service.effectiveSources(elementIn("src/main.cr"))
        service.effectiveSources(elementIn("src/edited.cr"))
        replaceText("src/edited.cr", "require \"./new_feature\"")
        val createEvent = VFileCreateEvent(this, directory("src"), "created.cr", false, null, null, null)
        val created = myFixture.addFileToProject("src/created.cr", "").virtualFile
        val indicator = ProgressIndicatorBase().apply { cancel() }

        ProgressManager.getInstance().executeProcessUnderProgress(
            {
                service.handleVfsEvents(
                    listOf(
                        VFileContentChangeEvent(this, vf("src/edited.cr"), 1, 2),
                        createEvent,
                    ),
                )
            },
            indicator,
        )

        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(created))
    }

    fun testChildMovedPsiEventInvalidatesChangedRequireFingerprint() {
        files(
            "src/main.cr" to "require \"./old_feature\"",
            "src/old_feature.cr" to "",
            "src/new_feature.cr" to "",
        )
        val service = service(stdlib = null)
        service.effectiveSources(elementIn("src/main.cr"))
        replaceText("src/main.cr", "require \"./new_feature\"")
        val file = psiFile("src/main.cr")
        val statement = ReadAction.computeBlocking<CrystalRequireStatement, RuntimeException> {
            requireNotNull(PsiTreeUtil.findChildOfType(file, CrystalRequireStatement::class.java))
        }
        val event = PsiTreeChangeEventImpl(PsiManager.getInstance(project)).apply {
            setFile(file)
            setChild(statement)
            setOldParent(file)
            setNewParent(file)
        }
        val before = service.cacheStats()

        ApplicationManager.getApplication().runReadAction { service.handlePsiChange(event) }

        assertEquals(before.targetedInvalidations + 1, service.cacheStats().targetedInvalidations)
    }

    fun testPropertyChangedPsiEventInvalidatesChangedRequireFingerprint() {
        files(
            "src/main.cr" to "require \"./old_feature\"",
            "src/old_feature.cr" to "",
            "src/new_feature.cr" to "",
        )
        val service = service(stdlib = null)
        service.effectiveSources(elementIn("src/main.cr"))
        replaceText("src/main.cr", "require \"./new_feature\"")
        val file = psiFile("src/main.cr")
        val statement = ReadAction.computeBlocking<CrystalRequireStatement, RuntimeException> {
            requireNotNull(PsiTreeUtil.findChildOfType(file, CrystalRequireStatement::class.java))
        }
        val event = PsiTreeChangeEventImpl(PsiManager.getInstance(project)).apply {
            setElement(statement)
            setPropertyName("require")
        }
        val before = service.cacheStats()

        ApplicationManager.getApplication().runReadAction { service.handlePsiChange(event) }

        assertEquals(before.targetedInvalidations + 1, service.cacheStats().targetedInvalidations)
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
                if (stdlibLookups.incrementAndGet() == 1) {
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
                if (stdlibLookups.incrementAndGet() == 1) {
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

    fun testInvalidRootAndArbitraryNonphysicalContextAreExcluded() {
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

    fun testStalePhysicalPsiReplacementIsExcludedBeforeContainingFileAccess() {
        files("src/main.cr" to "require \"./old\"")
        val service = service(stdlib = null)
        val stale = ReadAction.computeBlocking<CrystalRequireStatement, RuntimeException> {
            requireNotNull(PsiTreeUtil.findChildOfType(psiFile("src/main.cr"), CrystalRequireStatement::class.java))
        }
        val sources = service.effectiveSources(stale)

        replaceText("src/main.cr", "new_value")

        ReadAction.computeBlocking<Unit, RuntimeException> {
            assertFalse(stale.isValid)
            assertFalse(sources.contains(stale))
        }
        assertTrue(service.effectiveSources(stale).files.isEmpty())
        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(vf("src/main.cr")))
    }

    fun testNonphysicalContextDoesNotInferSameNamedPhysicalRoot() {
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

        assertTrue(service().effectiveSources(sameNamedNonphysicalCandidate).files.isEmpty())
    }

    fun testObservesStdlibRootOncePerQueryAndReusesPreludeFoundation() {
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
        assertEquals(2, stdlibLookups)

        service.invalidateAll()
        service.effectiveSources(elementIn("src/main.cr"))
        assertEquals(3, stdlibLookups)
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

    fun testConcurrentColdPreludeReadersShareOneResolutionAndSnapshot() {
        files("stdlib/prelude.cr" to "", "src/main.cr" to "")
        val ownerEntered = CountDownLatch(1)
        val waiterEntered = CountDownLatch(1)
        val releaseOwner = CountDownLatch(1)
        val resolutions = AtomicInteger()
        val service = concurrentPreludeService(ownerEntered, waiterEntered, releaseOwner, resolutions)
        val executor = AppExecutorUtil.getAppExecutorService()

        val first = CompletableFuture.supplyAsync({ service.effectiveSources(elementIn("src/main.cr")) }, executor)
        assertTrue(ownerEntered.await(10, TimeUnit.SECONDS))
        val second = CompletableFuture.supplyAsync({ service.effectiveSources(elementIn("src/main.cr")) }, executor)
        assertTrue(waiterEntered.await(10, TimeUnit.SECONDS))
        releaseOwner.countDown()

        assertSame(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
        assertEquals(1, resolutions.get())
    }

    fun testFailedColdPreludeOwnerReleasesWaiterAndAllowsRetry() {
        assertColdPreludeFailureIsRetryable(IllegalStateException("resolution failed"))
    }

    fun testCanceledColdPreludeOwnerReleasesWaiterAndAllowsRetry() {
        assertColdPreludeFailureIsRetryable(ProcessCanceledException())
    }

    private fun assertColdPreludeFailureIsRetryable(failure: RuntimeException) {
        files("stdlib/prelude.cr" to "", "src/main.cr" to "")
        val ownerEntered = CountDownLatch(1)
        val waiterEntered = CountDownLatch(1)
        val releaseOwner = CountDownLatch(1)
        val resolutions = AtomicInteger()
        val root = directory("stdlib")
        val resolver = CrystalRequirePathResolver(project) { root }
        val service = CrystalRequireGraphService(
            project,
            resolver,
            CrystalRequireGraphService.ValidationMode.EVENT_DRIVEN,
            preludeResolver = {
                if (resolutions.incrementAndGet() == 1) {
                    ownerEntered.countDown()
                    assertTrue(releaseOwner.await(10, TimeUnit.SECONDS))
                    throw failure
                }
                vf("stdlib/prelude.cr")
            },
            onPreludeWait = waiterEntered::countDown,
        )
        val executor = AppExecutorUtil.getAppExecutorService()
        val first = CompletableFuture.supplyAsync({ service.effectiveSources(elementIn("src/main.cr")) }, executor)
        assertTrue(ownerEntered.await(10, TimeUnit.SECONDS))
        val second = CompletableFuture.supplyAsync({ service.effectiveSources(elementIn("src/main.cr")) }, executor)
        assertTrue(waiterEntered.await(10, TimeUnit.SECONDS))
        releaseOwner.countDown()

        val ownerFailure = futureFailure(first)
        val waiterFailure = futureFailure(second)
        assertSame(failure, ownerFailure.cause)
        assertSame(failure, waiterFailure.cause)
        if (failure is ProcessCanceledException) {
            assertTrue(ownerFailure.cause is ProcessCanceledException)
            assertTrue(waiterFailure.cause is ProcessCanceledException)
        } else {
            assertEquals(IllegalStateException::class.java, ownerFailure.cause?.javaClass)
            assertEquals(IllegalStateException::class.java, waiterFailure.cause?.javaClass)
        }
        assertTrue(service.effectiveSources(elementIn("src/main.cr")).files.contains(vf("stdlib/prelude.cr")))
        assertEquals(2, resolutions.get())
    }

    private fun futureFailure(future: CompletableFuture<*>): ExecutionException = try {
        future.get(10, TimeUnit.SECONDS)
        fail("Expected future to fail")
        error("unreachable")
    } catch (error: ExecutionException) {
        error
    }

    private fun concurrentPreludeService(
        ownerEntered: CountDownLatch,
        waiterEntered: CountDownLatch,
        releaseOwner: CountDownLatch,
        resolutions: AtomicInteger,
    ): CrystalRequireGraphService {
        val root = directory("stdlib")
        val resolver = CrystalRequirePathResolver(project) { root }
        return CrystalRequireGraphService(
            project,
            resolver,
            CrystalRequireGraphService.ValidationMode.EVENT_DRIVEN,
            preludeResolver = {
                resolutions.incrementAndGet()
                ownerEntered.countDown()
                assertTrue(releaseOwner.await(10, TimeUnit.SECONDS))
                vf("stdlib/prelude.cr")
            },
            onPreludeWait = waiterEntered::countDown,
        )
    }

    private fun service(
        stdlib: VirtualFile? = directory("stdlib"),
        validationMode: CrystalRequireGraphService.ValidationMode =
            CrystalRequireGraphService.ValidationMode.ALWAYS,
        collector: (PsiFile) -> CrystalDirectRequires = CrystalRequireCollector::collect,
    ): CrystalRequireGraphService = CrystalRequireGraphService(
        project,
        CrystalRequirePathResolver(project) { stdlib },
        validationMode,
        collector,
        true,
    )

    private fun productionService(clearStdlibCache: Boolean = true): CrystalRequireGraphService {
        if (clearStdlibCache) CrystalStdlibResolver.clearCachedStdlibPath(project)
        val listenerDisposable = Disposer.newDisposable().also(listenerDisposables::add)
        return CrystalRequireGraphService(
            project,
            CrystalRequirePathResolver(project) { CrystalStdlibResolver.cachedStdlibPath(project) },
            listenerDisposable,
        )
    }

    private fun files(vararg files: Pair<String, String>) {
        files.forEach { (path, text) ->
            fixtureFiles[path] = myFixture.addFileToProject(path, text).virtualFile
        }
        flushFixtureFiles()
    }

    private fun flushFixtureFiles() {
        ApplicationManager.getApplication().invokeAndWait {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
    }

    private fun elementIn(path: String): PsiElement {
        val file = psiFile(path)
        return ReadAction.computeBlocking<PsiElement, RuntimeException> { file.firstChild ?: file }
    }

    private fun psiFile(path: String): PsiFile {
        val file = vf(path)
        return ReadAction.computeBlocking<PsiFile, RuntimeException> {
            requireNotNull(PsiManager.getInstance(project).findFile(file))
        }
    }

    private fun vf(path: String): VirtualFile =
        fixtureFiles[path]?.takeIf(VirtualFile::isValid)
            ?: requireNotNull(myFixture.tempDirFixture.getFile(path))

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

    private fun replaceEditorText(text: String) {
        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction { myFixture.editor.document.setText(text) }
            PsiDocumentManager.getInstance(project).commitDocument(myFixture.editor.document)
        }
    }

    private fun exerciseWildcardLifecycle(
        service: CrystalRequireGraphService,
        contextPath: String,
        watchedDirectoryPath: String,
        archiveDirectoryPath: String,
        manualEvents: Boolean,
        unrelatedPath: String,
        projectBaseTarget: Boolean = false,
    ) {
        var owner = service.effectiveSources(elementIn(contextPath))
        val unrelated = service.effectiveSources(elementIn(unrelatedPath))
        val watchedDirectory = wildcardDirectory(watchedDirectoryPath, projectBaseTarget)

        val createEvent = VFileCreateEvent(this, watchedDirectory, "created.cr", false, null, null, null)
        val changed = if (projectBaseTarget) {
            projectFile("$watchedDirectoryPath/created.cr")
        } else {
            myFixture.addFileToProject("$watchedDirectoryPath/created.cr", "").virtualFile
        }
        if (manualEvents) service.handleVfsEvents(listOf(createEvent))
        var updated = service.effectiveSources(elementIn(contextPath))
        assertTrue(updated.files.contains(changed))
        assertNotSame(owner, updated)
        assertSame(unrelated, service.effectiveSources(elementIn(unrelatedPath)))
        owner = updated

        val renameEvent = VFilePropertyChangeEvent(
            this,
            changed,
            VirtualFile.PROP_NAME,
            "created.cr",
            "renamed.cr",
        )
        ApplicationManager.getApplication().runWriteAction { changed.rename(this, "renamed.cr") }
        if (manualEvents) service.handleVfsEvents(listOf(renameEvent))
        updated = service.effectiveSources(elementIn(contextPath))
        assertTrue(updated.files.contains(changed))
        assertEquals("renamed.cr", changed.name)
        assertNotSame(owner, updated)
        assertSame(unrelated, service.effectiveSources(elementIn(unrelatedPath)))
        owner = updated

        val archiveDirectory = wildcardDirectory(archiveDirectoryPath, projectBaseTarget)
        val moveEvent = VFileMoveEvent(this, changed, archiveDirectory)
        ApplicationManager.getApplication().runWriteAction { changed.move(this, archiveDirectory) }
        if (manualEvents) service.handleVfsEvents(listOf(moveEvent))
        updated = service.effectiveSources(elementIn(contextPath))
        assertFalse(updated.files.contains(changed))
        assertNotSame(owner, updated)
        assertSame(unrelated, service.effectiveSources(elementIn(unrelatedPath)))

        val initial = wildcardFile("$watchedDirectoryPath/initial.cr", projectBaseTarget)
        val deleteEvent = VFileDeleteEvent(this, initial)
        ApplicationManager.getApplication().runWriteAction { deleteEvent.file.delete(this) }
        if (manualEvents) service.handleVfsEvents(listOf(deleteEvent))
        val afterDelete = service.effectiveSources(elementIn(contextPath))
        assertFalse(afterDelete.files.any { it.name == "initial.cr" })
        assertNotSame(updated, afterDelete)
        assertSame(unrelated, service.effectiveSources(elementIn(unrelatedPath)))
    }

    private fun projectFile(path: String): VirtualFile {
        var result: VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
            val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
            val parent = VfsUtil.createDirectories(
                "${requireNotNull(project.basePath)}${if (parentPath.isEmpty()) "" else "/$parentPath"}",
            )
            result = parent.findChild(path.substringAfterLast('/'))
                ?: parent.createChildData(this, path.substringAfterLast('/'))
        }
        return requireNotNull(result)
    }

    private fun removeProjectPath(path: String) {
        val file = LocalFileSystem.getInstance()
            .findFileByPath("${requireNotNull(project.basePath)}/$path") ?: return
        ApplicationManager.getApplication().runWriteAction { file.delete(this) }
    }

    private fun wildcardDirectory(path: String, projectBaseTarget: Boolean): VirtualFile =
        if (projectBaseTarget) {
            requireNotNull(LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/$path"))
        } else {
            directory(path)
        }

    private fun wildcardFile(path: String, projectBaseTarget: Boolean): VirtualFile =
        if (projectBaseTarget) {
            requireNotNull(wildcardDirectory(path.substringBeforeLast('/'), true).findChild(path.substringAfterLast('/')))
        } else {
            vf(path)
        }
}
