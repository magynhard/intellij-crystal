package de.magynhard.crystal.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalShardStatusTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            ApplicationManager.getApplication().runWriteAction {
                val basePath = project.basePath ?: return@runWriteAction
                val fileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                for (path in listOf("shard.yml", "shard.lock", "lib", "main.cr")) {
                    fileSystem.findFileByPath("$basePath/$path")?.delete(this@CrystalShardStatusTest)
                }
            }
        } finally {
            super.tearDown()
        }
    }

    fun testNoManifestYieldsNoEntries() {
        writeProjectFile("main.cr", "puts 1")
        assertTrue(CrystalShardStatus.dependencies(project).isEmpty())
    }

    fun testMissingDependency() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        )
        val entries = CrystalShardStatus.dependencies(project)
        assertEquals(1, entries.size)
        assertEquals(CrystalShardStatus.DependencyState.Missing, entries.single().state)
        assertEquals(entries, CrystalShardStatus.problems(project))
    }

    fun testInstalledLockMatchingDependencyIsOk() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n    version: \"~> 1.0\"\n"
        )
        writeProjectFile(
            "shard.lock",
            "version: 2.0\nshards:\n  kemal:\n    git: https://github.com/kemalcr/kemal.git\n    version: 1.9.0\n"
        )
        writeProjectFile("lib/kemal/shard.yml", "name: kemal\nversion: 1.9.0\n")
        val entries = CrystalShardStatus.dependencies(project)
        assertEquals(CrystalShardStatus.DependencyState.Ok, entries.single().state)
        assertTrue(CrystalShardStatus.problems(project).isEmpty())
    }

    fun testLockedDependencyTrustsShardsDespiteFieldMismatch() {
        // A lock entry means shards owns the truth: a successful install
        // guarantees the checked-out code matches the locked revision, while
        // the installed `version:` field is author-maintained and may be stale.
        // Flagging the mismatch would nag forever without any install fixing
        // it, so a pinned dependency is Ok without consulting the field.
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        )
        writeProjectFile(
            "shard.lock",
            "version: 2.0\nshards:\n  kemal:\n    git: https://github.com/kemalcr/kemal.git\n    version: 1.9.0\n"
        )
        writeProjectFile("lib/kemal/shard.yml", "name: kemal\nversion: 1.5.0\n")
        val entries = CrystalShardStatus.dependencies(project)
        assertEquals(CrystalShardStatus.DependencyState.Ok, entries.single().state)
        assertTrue(CrystalShardStatus.problems(project).isEmpty())
    }

    fun testLockedSentryWithStaleUpstreamFieldIsOk() {
        // Real-world shape (kemal's `sentry: {version: \"~> 0.5.0\"}`): the
        // lock pins 0.5.0 and shards installs that code, but upstream never
        // bumped the manifest field past 0.3.2. Must stay silent.
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  sentry:\n    github: samueleaton/sentry\n    version: \"~> 0.5.0\"\n"
        )
        writeProjectFile(
            "shard.lock",
            "version: 2.0\nshards:\n  sentry:\n    git: https://github.com/samueleaton/sentry.git\n    version: 0.5.0\n"
        )
        writeProjectFile("lib/sentry/shard.yml", "name: sentry\nversion: 0.3.2\n")
        val entries = CrystalShardStatus.dependencies(project)
        assertEquals(CrystalShardStatus.DependencyState.Ok, entries.single().state)
        assertTrue(CrystalShardStatus.problems(project).isEmpty())
    }

    fun testRequirementViolationWithoutLockIsReported() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n    version: \"~> 1.0\"\n"
        )
        writeProjectFile("lib/kemal/shard.yml", "name: kemal\nversion: 0.9.0\n")
        assertEquals(
            CrystalShardStatus.DependencyState.VersionMismatch("~> 1.0", "0.9.0"),
            CrystalShardStatus.dependencies(project).single().state
        )
    }

    fun testRequirementSatisfiedWithoutLockIsOk() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n    version: \"~> 1.0\"\n"
        )
        writeProjectFile("lib/kemal/shard.yml", "name: kemal\nversion: 1.2.0\n")
        assertEquals(
            CrystalShardStatus.DependencyState.Ok,
            CrystalShardStatus.dependencies(project).single().state
        )
    }

    fun testLockBuildMetadataSuffixIsOk() {
        // Branch-pinned dependencies (kemal's `ameba: {branch: master}`) lock
        // the installed commit as build metadata while the installed manifest
        // carries the base version — SemVer ignores build metadata, so this
        // is Ok and `shards install` must not be demanded in a loop.
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n    branch: master\n"
        )
        writeProjectFile(
            "shard.lock",
            "version: 2.0\nshards:\n  ameba:\n    git: https://github.com/crystal-ameba/ameba.git\n    version: 1.7.1-dev+git.commit.7f18f0d4595bf23f449d45a9cbbac51b29e4903b\n"
        )
        writeProjectFile("lib/ameba/shard.yml", "name: ameba\nversion: 1.7.1-dev\n")
        val entries = CrystalShardStatus.dependencies(project)
        assertEquals(CrystalShardStatus.DependencyState.Ok, entries.single().state)
        assertTrue(CrystalShardStatus.problems(project).isEmpty())
    }

    fun testLockBuildMetadataDifferentBaseIsOk() {
        // Same lock-trust rule as above: with a lock entry the installed
        // field is never consulted, whatever it says.
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        )
        writeProjectFile(
            "shard.lock",
            "version: 2.0\nshards:\n  kemal:\n    git: https://github.com/kemalcr/kemal.git\n    version: 1.7.0+git.commit.aaa111\n"
        )
        writeProjectFile("lib/kemal/shard.yml", "name: kemal\nversion: 1.7.1-dev\n")
        val entries = CrystalShardStatus.dependencies(project)
        assertEquals(CrystalShardStatus.DependencyState.Ok, entries.single().state)
        assertTrue(CrystalShardStatus.problems(project).isEmpty())
    }

    fun testMalformedManifestYieldsNoEntries() {
        writeProjectFile("shard.yml", "dependencies: [unclosed")
        assertTrue(CrystalShardStatus.dependencies(project).isEmpty())
    }

    private fun writeProjectFile(path: String, content: String) {
        ApplicationManager.getApplication().runWriteAction {
            val base = requireNotNull(project.basePath)
            val parentPath = path.substringBeforeLast('/', "")
            val parent = VfsUtil.createDirectories(if (parentPath.isEmpty()) base else "$base/$parentPath")
            val file = parent.findChild(path.substringAfterLast('/'))
                ?: parent.createChildData(this, path.substringAfterLast('/'))
            VfsUtil.saveText(file, content)
        }
    }

    // ==================== staleFieldNotes (post-install diagnosis) ====================

    private fun testEntry(
        name: String,
        state: CrystalShardStatus.DependencyState
    ) = CrystalShardStatus.Entry(
        CrystalShardManifest.Dependency(
            name,
            CrystalShardManifest.Source.Hosted("github", "example/$name"),
            null,
            null,
            false
        ),
        state
    )

    fun testUnchangedMismatchIsStale() {
        val entries = listOf(
            testEntry("sentry", CrystalShardStatus.DependencyState.VersionMismatch("0.5.0", "0.3.2"))
        )
        val noted = CrystalShardStatus.staleFieldNotes(
            mapOf("sentry" to "0.3.2"),
            mapOf("sentry" to "0.3.2"),
            entries
        )
        assertEquals(listOf("sentry"), noted.map { it.dependency.name })
    }

    fun testFixedMismatchIsNotStale() {
        val entries = listOf(
            testEntry("kemal", CrystalShardStatus.DependencyState.Ok)
        )
        val noted = CrystalShardStatus.staleFieldNotes(
            mapOf("kemal" to "1.5.0"),
            mapOf("kemal" to "1.9.0"),
            entries
        )
        assertTrue(noted.isEmpty())
    }

    fun testNewlyInstalledMismatchIsNotStale() {
        val entries = listOf(
            testEntry("radix", CrystalShardStatus.DependencyState.VersionMismatch("0.4.1", "0.4.0"))
        )
        val noted = CrystalShardStatus.staleFieldNotes(
            mapOf("radix" to null),
            mapOf("radix" to "0.4.0"),
            entries
        )
        assertTrue(noted.isEmpty())
    }

    fun testNoRemainingProblemsNotesNothing() {
        val noted = CrystalShardStatus.staleFieldNotes(
            mapOf("kemal" to "1.9.0"),
            mapOf("kemal" to "1.9.0"),
            listOf(testEntry("kemal", CrystalShardStatus.DependencyState.Ok))
        )
        assertTrue(noted.isEmpty())
    }
}
