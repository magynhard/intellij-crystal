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

    fun testLockMismatchIsReported() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        )
        writeProjectFile(
            "shard.lock",
            "version: 2.0\nshards:\n  kemal:\n    git: https://github.com/kemalcr/kemal.git\n    version: 1.9.0\n"
        )
        writeProjectFile("lib/kemal/shard.yml", "name: kemal\nversion: 1.5.0\n")
        assertEquals(
            CrystalShardStatus.DependencyState.VersionMismatch("1.9.0", "1.5.0"),
            CrystalShardStatus.dependencies(project).single().state
        )
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
}
