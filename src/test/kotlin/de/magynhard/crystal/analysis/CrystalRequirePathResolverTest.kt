package de.magynhard.crystal.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path

class CrystalRequirePathResolverTest : BasePlatformTestCase() {

    fun testCompilerOrderedFileExpansionsCoverEveryBranch() {
        val source = file("src/main.cr")
        val cases = listOf(
            "./models/user" to listOf("src/models/user.cr", "src/models/user/user.cr"),
            "./models/user.cr" to listOf("src/models/user.cr", "src/models/user.cr/user.cr"),
            "kemal" to listOf("lib/kemal.cr", "lib/kemal/kemal.cr", "lib/kemal/src/kemal.cr"),
            "kemal/helpers" to listOf(
                "lib/kemal/helpers.cr",
                "lib/kemal/src/helpers.cr",
                "lib/kemal/src/kemal/helpers.cr",
                "lib/kemal/helpers/helpers.cr",
                "lib/kemal/src/helpers/helpers.cr",
                "lib/kemal/src/kemal/helpers/helpers.cr",
            ),
            "kemal/helpers.cr" to listOf(
                "lib/kemal/helpers.cr",
                "lib/kemal/src/helpers.cr",
                "lib/kemal/src/kemal/helpers.cr",
                "lib/kemal/helpers.cr/helpers.cr",
                "lib/kemal/src/helpers.cr/helpers.cr",
                "lib/kemal/src/kemal/helpers.cr/helpers.cr",
            ),
        )

        for ((requirePath, paths) in cases) {
            for ((index, path) in paths.withIndex()) {
                val expected = if (path.startsWith("lib/")) projectFile(path) else file(path)
                assertEquals("$requirePath candidate $index", listOf(expected), resolver().resolve(source, requirePath).files)
                ApplicationManager.getApplication().runWriteAction { expected.delete(this) }
            }
        }

        val collisionPaths = listOf(
            "lib/collision/helpers.cr",
            "lib/collision/src/helpers.cr",
            "lib/collision/src/collision/helpers.cr",
            "lib/collision/helpers/helpers.cr",
            "lib/collision/src/helpers/helpers.cr",
            "lib/collision/src/collision/helpers/helpers.cr",
        )
        val collisions = collisionPaths.map(::projectFile)
        for ((index, expected) in collisions.withIndex()) {
            assertEquals("collision candidate $index", listOf(expected), resolver().resolve(source, "collision/helpers").files)
            ApplicationManager.getApplication().runWriteAction { expected.delete(this) }
        }
    }

    fun testProjectLibTakesPrecedenceOverStdlib() {
        val source = file("src/main.cr")
        val projectFile = projectFile("lib/json.cr")
        val stdlibFile = file("stdlib/json.cr")

        assertEquals(
            listOf(projectFile),
            resolver(directory("stdlib")).resolve(source, "json").files,
        )
        assertNotSame(projectFile, stdlibFile)
    }

    fun testStdlibTraversalIgnoresProjectLibForBareRequires() {
        val source = file("stdlib/prelude.cr")
        val projectFile = projectFile("lib/string.cr")
        val stdlibFile = file("stdlib/string.cr")
        val stdlibRoot = directory("stdlib")

        try {
            assertEquals(
                listOf(stdlibFile),
                resolver(stdlibRoot).resolveFromStdlib(source, "string", stdlibRoot).files,
            )
            assertFalse(
                resolver(stdlibRoot).resolveFromStdlib(source, "string", stdlibRoot)
                    .exactCandidatePaths.contains(projectFile.path),
            )
        } finally {
            ApplicationManager.getApplication().runWriteAction { projectFile.delete(this) }
        }
    }

    fun testStdlibTraversalPreservesRelativeResolution() {
        val source = file("stdlib/nested/source.cr")
        val helper = file("stdlib/nested/helper.cr")
        val stdlibRoot = directory("stdlib")

        assertEquals(
            listOf(helper),
            resolver(stdlibRoot).resolveFromStdlib(source, "./helper", stdlibRoot).files,
        )
    }

    fun testEveryDotPrefixedFilenameUsesRelativeExactResolution() {
        val source = file("src/main.cr")
        val hidden = file("src/.hidden.cr")

        assertEquals(listOf(hidden), resolver().resolve(source, ".hidden").files)
        assertEquals(listOf(hidden), resolver().resolve(source, ".hidden.cr").files)
    }

    fun testStdlibRootUsesNestedShardExpansionOrder() {
        val source = file("stdlib/prelude.cr")
        val nonNamespaced = file("stdlib/kemal/src/helpers.cr")
        val namespaced = file("stdlib/kemal/src/kemal/helpers.cr")
        val stdlibRoot = directory("stdlib")

        assertEquals(
            listOf(nonNamespaced),
            resolver(stdlibRoot).resolveFromStdlib(source, "kemal/helpers", stdlibRoot).files,
        )
        ApplicationManager.getApplication().runWriteAction { nonNamespaced.delete(this) }
        assertEquals(
            listOf(namespaced),
            resolver(stdlibRoot).resolveFromStdlib(source, "kemal/helpers", stdlibRoot).files,
        )
    }

    fun testStdlibTraversalIgnoresProjectLibForBareWildcards() {
        val source = file("stdlib/prelude.cr")
        val stdlibFile = file("stdlib/extensions/core.cr")
        val projectFile = projectFile("lib/extensions/project.cr")
        val stdlibRoot = directory("stdlib")

        try {
            val resolution = resolver(stdlibRoot)
                .resolveFromStdlib(source, "extensions/*", stdlibRoot)

            assertEquals(listOf(stdlibFile), resolution.files)
            assertFalse(resolution.files.contains(projectFile))
            assertEquals(setOf(directory("stdlib/extensions")), resolution.watchedDirectories)
        } finally {
            ApplicationManager.getApplication().runWriteAction { projectFile.parent.delete(this) }
        }
    }

    fun testBareExactUsesProjectBaseLibInsteadOfRequiringFileContentRoot() {
        val source = file("module/src/main.cr")
        val contentRootFile = file("lib/support.cr")
        val projectBaseFile = projectFile("lib/support.cr")

        assertFalse(VfsUtilCore.isAncestor(projectBaseRoot(), source, false))
        assertEquals(listOf(projectBaseFile), resolver().resolve(source, "support").files)
        assertNotSame(projectBaseFile, contentRootFile)
    }

    fun testResolvesPreludeFromConfiguredStdlibRoot() {
        val prelude = file("stdlib/prelude.cr")

        assertEquals(prelude, resolver(directory("stdlib")).resolvePrelude())
        assertNull(resolver().resolvePrelude())
    }

    fun testSingleStarExpandsOnlyDirectCrystalFilesInStableOrder() {
        val source = file("src/main.cr")
        val second = file("src/models/b.cr")
        val first = file("src/models/a.cr")
        file("src/models/README.md")
        file("src/models/nested/c.cr")

        val resolution = resolver().resolve(source, "./models/*")

        assertEquals(listOf(first, second), resolution.files)
        assertEquals(setOf(directory("src/models")), resolution.watchedDirectories)
    }

    fun testDoubleStarExpandsRecursively() {
        val source = file("src/main.cr")
        val direct = file("src/models/a.cr")
        val nested = file("src/models/nested/b.cr")
        val deeplyNested = file("src/models/nested/deep/c.cr")
        file("src/models/nested/README.md")

        val resolution = resolver().resolve(source, "./models/**")

        assertEquals(listOf(direct, nested, deeplyNested), resolution.files)
        assertEquals(setOf(directory("src/models")), resolution.watchedDirectories)
    }

    fun testEveryDotPrefixedFilenameUsesRelativeWildcardResolution() {
        val source = file("src/main.cr")
        val direct = file("src/.hidden/direct.cr")
        val nested = file("src/.hidden/nested/deep.cr")

        assertEquals(listOf(direct), resolver().resolve(source, ".hidden/*").files)
        assertEquals(listOf(direct, nested), resolver().resolve(source, ".hidden/**").files)
    }

    fun testRecursiveWildcardRejectsProjectRootButAllowsTargetedSubdirectory() {
        val source = projectFile("main.cr")
        val rootFeature = projectFile("root_feature.cr")
        val nested = projectFile("src/extensions/feature.cr")
        val resolver = CrystalRequirePathResolver(
            project,
            stdlibRoot = { null },
            projectRoot = ::projectBaseRoot,
        )

        try {
            assertEquals(emptyList<VirtualFile>(), resolver.resolve(source, "./**").files)
            assertEquals(emptyList<VirtualFile>(), resolver.resolve(source, "../**").files)
            assertEquals(emptyList<VirtualFile>(), resolver.resolve(source, "../../**").files)
            assertEquals(listOf(nested), resolver.resolve(source, "./src/extensions/**").files)
        } finally {
            ApplicationManager.getApplication().runWriteAction {
                source.delete(this)
                rootFeature.delete(this)
                nested.parent.parent.delete(this)
            }
        }
    }

    fun testRecursiveWildcardUsesCompilerSortedDepthFirstOrder() {
        val source = file("src/main.cr")
        val rootB = file("src/models/b.cr")
        val rootA = file("src/models/a.cr")
        val nestedB = file("src/models/b_dir/b.cr")
        val nestedA = file("src/models/a_dir/a.cr")
        val deep = file("src/models/a_dir/deep/z.cr")

        assertEquals(
            listOf(rootA, rootB, nestedA, deep, nestedB),
            resolver().resolve(source, "./models/**").files,
        )
    }

    fun testRecursiveWildcardChecksCancellation() {
        val source = file("src/main.cr")
        file("src/models/a.cr")
        val indicator = ProgressIndicatorBase().apply { cancel() }

        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(
                { resolver().resolve(source, "./models/**") },
                indicator,
            )
        }
    }

    fun testRecursiveWildcardDoesNotRevisitSymbolicLinkCycle() {
        val source = projectFile("cycle-fixture/src/main.cr")
        val feature = projectFile("cycle-fixture/src/models/feature.cr")
        val models = requireNotNull(feature.parent)
        try {
            createSymbolicLinkOrSkip(Path.of(models.path, "loop"), Path.of(models.path))
            VfsUtil.markDirtyAndRefresh(false, true, true, models)

            assertEquals(listOf(feature), resolver().resolve(source, "./models/**").files)
        } finally {
            ApplicationManager.getApplication().runWriteAction {
                projectBaseRoot().findChild("cycle-fixture")?.delete(this)
            }
        }
    }

    fun testRecursiveWildcardRejectsInitialSymlinkToCanonicalProjectRoot() {
        val source = projectFile("symlink-initial/source/main.cr")
        val projectFeature = projectFile("symlink-initial-project-feature.cr")
        val sourceDirectory = requireNotNull(source.parent)
        createSymbolicLinkOrSkip(Path.of(sourceDirectory.path, ".project"), Path.of(projectBaseRoot().path))
        VfsUtil.markDirtyAndRefresh(false, true, true, sourceDirectory)

        try {
            val resolution = resolver().resolve(source, ".project/**")
            assertTrue(resolution.files.isEmpty())
            assertFalse(resolution.files.contains(projectFeature))
            assertNull(resolution.wildcardWatches.single().canonicalTargetPath)
        } finally {
            ApplicationManager.getApplication().runWriteAction {
                projectBaseRoot().findChild("symlink-initial")?.delete(this)
                projectFeature.delete(this)
            }
        }
    }

    fun testRecursiveWildcardSkipsNestedSymlinkToCanonicalProjectRoot() {
        val source = projectFile("symlink-nested/main.cr")
        val allowed = projectFile("symlink-nested/target/allowed.cr")
        val projectFeature = projectFile("symlink-nested-project-feature.cr")
        val target = requireNotNull(allowed.parent)
        createSymbolicLinkOrSkip(Path.of(target.path, "project"), Path.of(projectBaseRoot().path))
        VfsUtil.markDirtyAndRefresh(false, true, true, target)

        try {
            val resolution = resolver().resolve(source, "./target/**")
            assertEquals(listOf(allowed), resolution.files)
            assertFalse(resolution.files.contains(projectFeature))
        } finally {
            ApplicationManager.getApplication().runWriteAction {
                projectBaseRoot().findChild("symlink-nested")?.delete(this)
                projectFeature.delete(this)
            }
        }
    }

    fun testMissingWildcardTargetWatchesNearestExistingDirectory() {
        val source = file("src/main.cr")
        file("src/models/existing.cr")

        val resolution = resolver().resolve(source, "./models/generated/**")

        assertEquals(emptyList<VirtualFile>(), resolution.files)
        assertEquals(setOf(directory("src/models")), resolution.watchedDirectories)
    }

    fun testBareWildcardFallsBackToStdlibAndWatchesHigherPrecedenceAncestor() {
        val source = file("src/main.cr")
        projectFile("lib/installed/existing.cr")
        val stdlibFile = file("stdlib/extras/feature.cr")

        val resolution = resolver(directory("stdlib")).resolve(source, "extras/*")

        assertEquals(listOf(stdlibFile), resolution.files)
        assertEquals(
            setOf(projectDirectory("lib"), directory("stdlib/extras")),
            resolution.watchedDirectories,
        )
    }

    fun testBareWildcardUsesProjectBaseLibInsteadOfRequiringFileContentRoot() {
        val source = file("module/src/main.cr")
        val contentRootFile = file("lib/features/content.cr")
        val projectBaseFile = projectFile("lib/features/project.cr")

        val resolution = resolver().resolve(source, "features/*")

        assertFalse(VfsUtilCore.isAncestor(projectBaseRoot(), source, false))
        assertEquals(listOf(projectBaseFile), resolution.files)
        assertEquals(setOf(projectDirectory("lib/features")), resolution.watchedDirectories)
        assertFalse(resolution.files.contains(contentRootFile))
    }

    fun testRecognizesOnlyTerminalWildcardSuffixes() {
        val source = file("src/main.cr")
        file("src/models/nested/user.cr")

        val resolution = resolver().resolve(source, "./models/*/user")

        assertEquals(emptyList<VirtualFile>(), resolution.files)
        assertEquals(emptySet<VirtualFile>(), resolution.watchedDirectories)
    }

    fun testNonWildcardLookupDoesNotWatchDirectories() {
        val source = file("src/main.cr")
        val target = file("src/models/user.cr")

        val resolution = resolver().resolve(source, "./models/user")

        assertEquals(listOf(target), resolution.files)
        assertEquals(emptySet<VirtualFile>(), resolution.watchedDirectories)
    }

    fun testUnresolvedRelativeExactRequireOwnsEveryCandidatePath() {
        val source = file("src/main.cr")

        val resolution = resolver().resolve(source, "./feature")

        assertEquals(emptyList<VirtualFile>(), resolution.files)
        assertEquals(emptySet<VirtualFile>(), resolution.watchedDirectories)
        assertEquals(
            setOf(
                "${directory("src").path}/feature.cr",
                "${directory("src").path}/feature/feature.cr",
            ),
            resolution.exactCandidatePaths,
        )
    }

    fun testStdlibExactRequireOwnsHigherPrecedenceProjectCandidates() {
        val source = file("src/main.cr")
        projectFile("lib/.keep")
        val stdlibFile = file("stdlib/json.cr")

        val resolution = resolver(directory("stdlib")).resolve(source, "json")

        val projectLib = projectDirectory("lib").path
        assertEquals(listOf(stdlibFile), resolution.files)
        assertEquals(
            setOf(
                "$projectLib/json.cr",
                "$projectLib/json/json.cr",
                "$projectLib/json/src/json.cr",
                "${directory("stdlib").path}/json.cr",
            ),
            resolution.exactCandidatePaths,
        )
    }

    fun testWildcardWatchRetainsIntendedTargetAndMode() {
        val source = file("src/main.cr")
        file("src/models/existing.cr")

        val direct = resolver().resolve(source, "./models/*")
        val recursiveMissing = resolver().resolve(source, "./models/generated/**")

        assertEquals(
            setOf(
                CrystalWildcardWatch(
                    directory("src/models"),
                    "${directory("src/models").path}",
                    directory("src/models").canonicalPath,
                    CrystalWildcardMode.DIRECT,
                ),
            ),
            direct.wildcardWatches,
        )
        assertEquals(
            setOf(
                CrystalWildcardWatch(
                    directory("src/models"),
                    "${directory("src/models").path}/generated",
                    null,
                    CrystalWildcardMode.RECURSIVE,
                ),
            ),
            recursiveMissing.wildcardWatches,
        )
        assertEquals(setOf(directory("src/models")), recursiveMissing.watchedDirectories)
    }

    private fun resolver(stdlibRoot: VirtualFile? = null): CrystalRequirePathResolver =
        CrystalRequirePathResolver(project) { stdlibRoot }

    private fun file(path: String): VirtualFile =
        myFixture.addFileToProject(path, "").virtualFile

    private fun projectFile(path: String): VirtualFile {
        var result: VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
            val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
            val parent = VfsUtil.createDirectories("${requireNotNull(project.basePath)}/$parentPath")
            val name = path.substringAfterLast('/')
            result = parent.findChild(name) ?: parent.createChildData(this, name)
        }
        return requireNotNull(result)
    }

    private fun projectDirectory(path: String): VirtualFile =
        requireNotNull(projectBaseRoot().findFileByRelativePath(path))

    private fun projectBaseRoot(): VirtualFile =
        requireNotNull(LocalFileSystem.getInstance().findFileByPath(requireNotNull(project.basePath)))

    private fun directory(path: String): VirtualFile =
        requireNotNull(myFixture.tempDirFixture.findOrCreateDir("").findFileByRelativePath(path))

    private fun createSymbolicLinkOrSkip(link: Path, target: Path) {
        try {
            Files.createDirectories(link.parent)
            Files.createSymbolicLink(link, target)
        } catch (error: UnsupportedOperationException) {
            Assume.assumeNoException(error)
        } catch (error: AccessDeniedException) {
            Assume.assumeNoException("Platform does not permit symbolic links", error)
        } catch (error: FileSystemException) {
            val reason = error.reason.orEmpty().lowercase()
            if (reason.contains("not permitted") || reason.contains("not supported") || reason.contains("privilege")) {
                Assume.assumeNoException("Platform cannot create symbolic links", error)
            }
            throw error
        } catch (error: SecurityException) {
            Assume.assumeNoException(error)
        }
    }
}
