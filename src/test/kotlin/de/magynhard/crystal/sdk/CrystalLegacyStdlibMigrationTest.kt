package de.magynhard.crystal.sdk

import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class CrystalLegacyStdlibMigrationTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.moduleLibraryTable.libraries
                    .filter { it.name in TEST_LIBRARY_NAMES }
                    .forEach(model.moduleLibraryTable::removeLibrary)
            }
        } finally {
            super.tearDown()
        }
    }

    fun testExclusionPolicyReturnsKnownDistributionDirectoriesForLegacyLibrary() {
        val fixture = createStdlibFixture()
        try {
            addModuleLibrary(LEGACY_LIBRARY_NAME, fixture.stdlib)
            configureCrystalPath(fixture.executable)

            val excludedUrls = CrystalLegacyStdlibExcludePolicy(project)
                .excludeUrlsForProject
                .toSet()

            assertEquals(fixture.excludedDirectories.map { it.toUri().toString().removeSuffix("/") }.toSet(), excludedUrls)
        } finally {
            restoreCrystalPath()
            File(fixture.root.toString()).deleteRecursively()
        }
    }

    fun testExclusionPolicyUsesPersistedLegacyRootInsteadOfConfiguredRoot() {
        val legacyFixture = createStdlibFixture()
        val configuredFixture = createStdlibFixture()
        try {
            addModuleLibrary(LEGACY_LIBRARY_NAME, legacyFixture.stdlib)
            configureCrystalPath(configuredFixture.executable)

            val excludedUrls = CrystalLegacyStdlibExcludePolicy(project).excludeUrlsForProject.toSet()

            assertEquals(excludedUrls(legacyFixture), excludedUrls)
            assertTrue(excludedUrls.intersect(excludedUrls(configuredFixture)).isEmpty())
        } finally {
            restoreCrystalPath()
            File(legacyFixture.root.toString()).deleteRecursively()
            File(configuredFixture.root.toString()).deleteRecursively()
        }
    }

    fun testExclusionPolicyUsesEveryPersistedLegacySourceRoot() {
        val firstFixture = createStdlibFixture()
        val secondFixture = createStdlibFixture()
        try {
            addModuleLibrary(LEGACY_LIBRARY_NAME, firstFixture.stdlib, secondFixture.stdlib)
            configureCrystalPath(firstFixture.executable)

            val excludedUrls = CrystalLegacyStdlibExcludePolicy(project).excludeUrlsForProject.toSet()

            assertEquals(excludedUrls(firstFixture) + excludedUrls(secondFixture), excludedUrls)
        } finally {
            restoreCrystalPath()
            File(firstFixture.root.toString()).deleteRecursively()
            File(secondFixture.root.toString()).deleteRecursively()
        }
    }

    fun testExclusionPolicyExcludesDirectUnwantedSourceRoot() {
        val fixture = createStdlibFixture()
        try {
            val compilerRoot = fixture.stdlib.resolve("compiler")
            addModuleLibrary(LEGACY_LIBRARY_NAME, compilerRoot)
            configureCrystalPath(fixture.executable)

            assertEquals(
                setOf(compilerRoot.toUri().toString().removeSuffix("/")),
                CrystalLegacyStdlibExcludePolicy(project).excludeUrlsForProject.toSet()
            )
        } finally {
            restoreCrystalPath()
            File(fixture.root.toString()).deleteRecursively()
        }
    }

    fun testExclusionPolicyPreservesSrcAndFilteredSourceRoots() {
        val fixture = createStdlibFixture()
        try {
            val srcRoot = Files.createDirectory(fixture.root.resolve("src"))
            val filteredRoot = Files.createDirectory(fixture.root.resolve("json"))
            addModuleLibrary(LEGACY_LIBRARY_NAME, srcRoot, filteredRoot)
            configureCrystalPath(fixture.executable)

            assertEmpty(CrystalLegacyStdlibExcludePolicy(project).excludeUrlsForProject.asList())
        } finally {
            restoreCrystalPath()
            File(fixture.root.toString()).deleteRecursively()
        }
    }

    fun testExclusionPolicyReturnsNothingWithoutLegacyLibrary() {
        val fixture = createStdlibFixture()
        try {
            configureCrystalPath(fixture.executable)

            assertEmpty(CrystalLegacyStdlibExcludePolicy(project).excludeUrlsForProject.asList())
        } finally {
            restoreCrystalPath()
            File(fixture.root.toString()).deleteRecursively()
        }
    }

    fun testCompilerSourceProjectContentIsNotExcludedWithoutLegacyLibrary() {
        myFixture.addFileToProject("compiler/crystal/config.cr", "SOURCE_DATE_EPOCH = 0")
        val fixture = createStdlibFixture()
        try {
            configureCrystalPath(fixture.executable)

            assertEmpty(CrystalLegacyStdlibExcludePolicy(project).excludeUrlsForProject.asList())
        } finally {
            restoreCrystalPath()
            File(fixture.root.toString()).deleteRecursively()
        }
    }

    fun testCleanupRemovesOnlyLegacyLibrary() = runBlocking {
        addModuleLibrary("Before")
        addModuleLibrary(LEGACY_LIBRARY_NAME)
        addModuleLibrary("After")

        CrystalLegacyStdlibCleanupActivity().execute(project)

        assertEquals(listOf("Before", "After"), moduleLibraryNames())
    }

    fun testCleanupIsIdempotent() = runBlocking {
        addModuleLibrary(LEGACY_LIBRARY_NAME)

        val activity = CrystalLegacyStdlibCleanupActivity()
        activity.execute(project)
        activity.execute(project)

        assertEmpty(moduleLibraryNames())
    }

    fun testCleanupNeverCreatesReplacementModuleLibrary() = runBlocking {
        CrystalLegacyStdlibCleanupActivity().execute(project)

        assertEmpty(moduleLibraryNames())
    }

    private var originalCrystalPath: String? = null

    private fun addModuleLibrary(name: String, vararg sourceRoots: Path) =
        ModuleRootModificationUtil.addModuleLibrary(
            module,
            name,
            emptyList(),
            sourceRoots.map { it.toUri().toString().removeSuffix("/") }
        )

    private fun moduleLibraryNames(): List<String> {
        val model = ModuleRootManager.getInstance(module).modifiableModel
        return try {
            model.moduleLibraryTable.libraries.mapNotNull { it.name }
        } finally {
            model.dispose()
        }
    }

    private fun configureCrystalPath(executable: Path) {
        val settings = CrystalSettings.getInstance(project)
        originalCrystalPath = settings.state.crystalPath
        settings.state.crystalPath = executable.toString()
        CrystalStdlibResolver.clearCachedStdlibPath(project)
    }

    private fun restoreCrystalPath() {
        originalCrystalPath?.let { CrystalSettings.getInstance(project).state.crystalPath = it }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
    }

    private fun createStdlibFixture(): StdlibFixture {
        val root = Files.createTempDirectory("crystal-legacy-migration-test")
        val stdlib = Files.createDirectory(root.resolve("stdlib"))
        Files.writeString(stdlib.resolve("array.cr"), "class Array; end")
        val excludedDirectories = EXCLUDED_DIRECTORIES.map { name ->
            Files.createDirectory(stdlib.resolve(name))
        }
        val executable = Files.writeString(
            root.resolve("fake-crystal"),
            """
            #!/bin/sh
            if [ "${'$'}1" = "env" ]; then
              echo "$stdlib"
            else
              echo "Crystal 1.20.0"
            fi
            """.trimIndent()
        )
        assertTrue(executable.toFile().setExecutable(true))
        return StdlibFixture(root, stdlib, executable, excludedDirectories)
    }

    private fun excludedUrls(fixture: StdlibFixture): Set<String> =
        fixture.excludedDirectories.map { it.toUri().toString().removeSuffix("/") }.toSet()

    private data class StdlibFixture(
        val root: Path,
        val stdlib: Path,
        val executable: Path,
        val excludedDirectories: List<Path>
    )

    private companion object {
        const val LEGACY_LIBRARY_NAME = "Crystal StdLib"
        val EXCLUDED_DIRECTORIES = listOf("compiler", "crystal", "lib_c", "lib_z", "ll", "llvm", "gc", "samples")
        val TEST_LIBRARY_NAMES = setOf(LEGACY_LIBRARY_NAME, "Before", "After")
    }
}
