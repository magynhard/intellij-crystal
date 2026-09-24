package de.magynhard.crystal.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalShardBannerProviderTest : BasePlatformTestCase() {

    fun testBannerShowsForRootManifestWithProblems() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        )
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
        )
        val entries = CrystalShardBannerProvider().bannerProblems(project, file)
        assertNotNull(entries)
        assertEquals(listOf("kemal"), entries!!.map { it.dependency.name })
    }

    fun testBannerSilentWhenClean() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        )
        writeProjectFile("lib/kemal/shard.yml", "name: kemal\nversion: 1.9.0\n")
        writeProjectFile(
            "shard.lock",
            "version: 2.0\nshards:\n  kemal:\n    git: https://github.com/kemalcr/kemal.git\n    version: 1.9.0\n"
        )
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
        )
        assertNull(CrystalShardBannerProvider().bannerProblems(project, file))
    }

    fun testBannerSilentForOtherFiles() {
        writeProjectFile("shard.yml", "name: app\n")
        writeProjectFile("src/app.cr", "puts 1")
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/src/app.cr")
        )
        assertNull(CrystalShardBannerProvider().bannerProblems(project, file))
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

    override fun tearDown() {
        try {
            ApplicationManager.getApplication().runWriteAction {
                val basePath = project.basePath ?: return@runWriteAction
                val fileSystem = LocalFileSystem.getInstance()
                for (path in listOf("shard.yml", "shard.lock", "lib", "src")) {
                    fileSystem.findFileByPath("$basePath/$path")?.delete(this@CrystalShardBannerProviderTest)
                }
            }
        } finally {
            super.tearDown()
        }
    }
}
