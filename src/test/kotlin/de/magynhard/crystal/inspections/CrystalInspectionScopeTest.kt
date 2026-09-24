package de.magynhard.crystal.inspections

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalInspectionScopeTest : BasePlatformTestCase() {

    fun testFixtureFileIsProjectSource() {
        myFixture.configureByText("main.cr", "puts 1\n")
        assertTrue(CrystalInspectionScope.isProjectSource(myFixture.file))
    }

    fun testBasePathFileIsProjectSource() {
        writeProjectFile("src/main.cr", "puts 1\n")
        assertTrue(CrystalInspectionScope.isProjectSource(psiFile("src/main.cr")))
    }

    fun testManagedLibFileIsNotProjectSource() {
        writeProjectFile("shard.yml", "name: app\n")
        writeProjectFile("lib/kemal/src/kemal.cr", "module Kemal\nend\n")
        assertFalse(CrystalInspectionScope.isProjectSource(psiFile("lib/kemal/src/kemal.cr")))
    }

    fun testLibFileWithoutManifestKeepsProjectSource() {
        writeProjectFile("lib/kemal/src/kemal.cr", "module Kemal\nend\n")
        assertTrue(CrystalInspectionScope.isProjectSource(psiFile("lib/kemal/src/kemal.cr")))
    }

    private fun psiFile(path: String): com.intellij.psi.PsiFile {
        val base = requireNotNull(project.basePath)
        val virtualFile = requireNotNull(LocalFileSystem.getInstance().findFileByPath("$base/$path"))
        return requireNotNull(PsiManager.getInstance(project).findFile(virtualFile))
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
                    fileSystem.findFileByPath("$basePath/$path")?.delete(this@CrystalInspectionScopeTest)
                }
            }
        } finally {
            super.tearDown()
        }
    }
}
