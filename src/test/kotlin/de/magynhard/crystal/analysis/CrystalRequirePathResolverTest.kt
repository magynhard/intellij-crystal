package de.magynhard.crystal.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalRequirePathResolverTest : BasePlatformTestCase() {

    fun testResolvesRelativeFileAndDirectoryMain() {
        val source = file("src/main.cr")
        val direct = file("src/models/user.cr")
        assertEquals(listOf(direct), resolver().resolve(source, "./models/user").files)

        ApplicationManager.getApplication().runWriteAction { direct.delete(this) }
        val directoryMain = file("src/models/user/user.cr")
        assertEquals(listOf(directoryMain), resolver().resolve(source, "./models/user").files)
    }

    fun testResolvesRelativeParentPath() {
        val source = file("src/features/main.cr")
        val helper = file("src/helpers.cr")

        assertEquals(listOf(helper), resolver().resolve(source, "../helpers").files)
    }

    fun testResolvesBareFileAndDirectoryMainFromProjectLib() {
        val source = file("src/main.cr")
        val direct = projectFile("lib/support/helpers.cr")
        val directoryMain = projectFile("lib/support/helpers/helpers.cr")

        assertEquals(listOf(direct), resolver().resolve(source, "support/helpers").files)

        ApplicationManager.getApplication().runWriteAction { direct.delete(this) }
        assertEquals(listOf(directoryMain), resolver().resolve(source, "support/helpers").files)
    }

    fun testResolvesBareShardMainUnderSrc() {
        val source = file("src/main.cr")
        val kemal = projectFile("lib/kemal/src/kemal.cr")

        assertEquals(listOf(kemal), resolver().resolve(source, "kemal").files)
    }

    fun testResolvesNestedShardPathInCandidateOrder() {
        val source = file("src/main.cr")
        val direct = projectFile("lib/kemal/helpers.cr")
        val directoryMain = projectFile("lib/kemal/helpers/helpers.cr")
        val shardDirect = projectFile("lib/kemal/src/helpers.cr")
        val shardDirectoryMain = projectFile("lib/kemal/src/helpers/helpers.cr")

        assertEquals(listOf(direct), resolver().resolve(source, "kemal/helpers").files)

        ApplicationManager.getApplication().runWriteAction { direct.delete(this) }
        assertEquals(listOf(directoryMain), resolver().resolve(source, "kemal/helpers").files)

        ApplicationManager.getApplication().runWriteAction { directoryMain.delete(this) }
        assertEquals(listOf(shardDirect), resolver().resolve(source, "kemal/helpers").files)

        ApplicationManager.getApplication().runWriteAction { shardDirect.delete(this) }
        assertEquals(listOf(shardDirectoryMain), resolver().resolve(source, "kemal/helpers").files)
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
                "$projectLib/json/src/json/json.cr",
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
}
