package de.magynhard.crystal.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AmebaConfigDiscoveryTest : BasePlatformTestCase() {

    private var savedConfigPath = ""

    override fun setUp() {
        super.setUp()
        savedConfigPath = CrystalSettings.getInstance(project).state.amebaConfigPath
        CrystalSettings.getInstance(project).state.amebaConfigPath = ""
    }

    override fun tearDown() {
        try {
            CrystalSettings.getInstance(project).state.amebaConfigPath = savedConfigPath
            deleteProjectFile(".ameba.yml")
            deleteProjectFile("sub/.ameba.yml")
            deleteProjectFile("sub/main.cr")
        } finally {
            super.tearDown()
        }
    }

    fun testDiscoversNearestConfigUpwards() {
        writeProjectFile(".ameba.yml", "AllExcluded:\n  - lib\n")
        writeProjectFile("sub/.ameba.yml", "AllExcluded:\n  - spec\n")
        val file = writeProjectFile("sub/main.cr", "puts 1\n")

        val found = AmebaConfigDiscovery.discover(project, file)
        assertNotNull(found)
        assertEquals("sub/.ameba.yml", projectRelativePath(found!!))
    }

    fun testFallsBackToProjectRootConfig() {
        writeProjectFile(".ameba.yml", "AllExcluded:\n  - lib\n")
        val file = writeProjectFile("sub/main.cr", "puts 1\n")

        val found = AmebaConfigDiscovery.discover(project, file)
        assertNotNull(found)
        assertEquals(".ameba.yml", projectRelativePath(found!!))
    }

    fun testReturnsNullWithoutAnyConfig() {
        val file = writeProjectFile("sub/main.cr", "puts 1\n")
        assertNull(AmebaConfigDiscovery.discover(project, file))
    }

    fun testExplicitOverrideWins() {
        writeProjectFile(".ameba.yml", "AllExcluded:\n  - lib\n")
        val custom = writeProjectFile("custom.yml", "AllExcluded:\n  - spec\n")
        val file = writeProjectFile("sub/main.cr", "puts 1\n")
        CrystalSettings.getInstance(project).state.amebaConfigPath = custom.path

        val found = AmebaConfigDiscovery.discover(project, file)
        assertNotNull(found)
        assertEquals(custom.path, found!!.path)
    }

    fun testMissingOverrideFallsBackToNothing() {
        CrystalSettings.getInstance(project).state.amebaConfigPath = "/nonexistent/custom.yml"
        val file = writeProjectFile("sub/main.cr", "puts 1\n")
        assertNull(AmebaConfigDiscovery.discover(project, file))
    }

    private fun projectRelativePath(file: com.intellij.openapi.vfs.VirtualFile): String {
        val base = requireNotNull(project.basePath)
        return file.path.removePrefix("$base/")
    }

    private fun writeProjectFile(path: String, content: String): com.intellij.openapi.vfs.VirtualFile {
        var result: com.intellij.openapi.vfs.VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
            val base = requireNotNull(project.basePath)
            val parentPath = path.substringBeforeLast('/', "")
            val parent = VfsUtil.createDirectories(if (parentPath.isEmpty()) base else "$base/$parentPath")
            val file = parent.findChild(path.substringAfterLast('/'))
                ?: parent.createChildData(this, path.substringAfterLast('/'))
            VfsUtil.saveText(file, content)
            result = file
        }
        return requireNotNull(result)
    }

    private fun deleteProjectFile(path: String) {
        ApplicationManager.getApplication().runWriteAction {
            val base = project.basePath ?: return@runWriteAction
            com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath("$base/$path")?.delete(this@AmebaConfigDiscoveryTest)
        }
    }
}
