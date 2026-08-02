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
import com.intellij.util.concurrency.AppExecutorUtil
import de.magynhard.crystal.CrystalLanguage
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.sdk.CrystalSettings
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
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
        } finally {
            super.tearDown()
        }
    }

    fun testProductionServiceDoesNotDiscoverStdlibDuringCompletion() {
        files("task4_no_discovery/main.cr" to "")
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

            val sources = productionService().effectiveSources(elementIn("task4_no_discovery/main.cr"))

            assertEquals(setOf(vf("task4_no_discovery/main.cr")), sources.files)
            assertFalse("Completion must not launch Crystal stdlib discovery", Files.exists(marker))
        } finally {
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            File(tempDirectory.toString()).deleteRecursively()
        }
    }

    fun testProductionServiceReusesPreviouslyDiscoveredStdlib() {
        files("task4_cached_stdlib/main.cr" to "")
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

            val sources = productionService(clearStdlibCache = false)
                .effectiveSources(elementIn("task4_cached_stdlib/main.cr"))

            assertTrue(sources.files.contains(requireNotNull(cachedRoot.findChild("prelude.cr"))))
            assertEquals("Completion must reuse the cached root", 1L, Files.size(marker))
        } finally {
            settings.state.crystalPath = originalCrystalPath
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            File(tempDirectory.toString()).deleteRecursively()
        }
    }

    fun testProductionServiceObservesStdlibCachedAfterColdFirstQuery() {
        files("task4_late_stdlib/main.cr" to "")
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
            val service = productionService()

            assertEquals(
                setOf(vf("task4_late_stdlib/main.cr")),
                service.effectiveSources(elementIn("task4_late_stdlib/main.cr")).files,
            )
            assertFalse(Files.exists(marker))

            val cachedRoot = requireNotNull(CrystalStdlibResolver.resolveStdlibPath(project))
            assertEquals(1L, Files.size(marker))
            val updated = service.effectiveSources(elementIn("task4_late_stdlib/main.cr"))

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

    fun testVfsContentCallbackDefersFingerprintValidationUntilNextQuery() {
        files(
            "src/main.cr" to "require \"./old_feature\"",
            "src/old_feature.cr" to "",
            "src/new_feature.cr" to "",
        )
        val queued = mutableListOf<Runnable>()
        val service = CrystalRequireGraphService(
            project,
            CrystalRequirePathResolver(project) { null },
            Executor(queued::add),
        )
        service.effectiveSources(elementIn("src/main.cr"))
        replaceText("src/main.cr", "require \"./new_feature\"")
        val beforeCallback = service.cacheStats()

        service.handleVfsEvents(listOf(VFileContentChangeEvent(this, vf("src/main.cr"), 1, 2)))

        assertEquals(1, queued.size)
        assertEquals(beforeCallback, service.cacheStats())
        queued.single().run()
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
