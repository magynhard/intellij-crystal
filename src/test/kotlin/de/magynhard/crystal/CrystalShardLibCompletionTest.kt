package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Completion from installed shard sources: bare require paths resolve
 * into `lib/`, and dependency modules are suggested after requiring.
 */
class CrystalShardLibCompletionTest : BasePlatformTestCase() {

    fun testBareRequireSuggestsInstalledShard() {
        writeProjectFile("shard.yml", "name: app\n")
        writeProjectFile("lib/kemal/src/kemal.cr", "module Kemal\nend\n")
        openMain("require \"kem\"\n", "require \"kem".length)
        val names = myFixture.complete(CompletionType.BASIC)?.map { it.lookupString } ?: emptyList()
        assertTrue("Installed shard 'kemal' must be suggested for bare requires: $names", names.contains("kemal"))
    }

    fun testModuleFromInstalledShardSuggestedAfterRequire() {
        myFixture.addFileToProject(
            "lib/kemal/src/kemal.cr",
            "module Kemal\nend\n"
        )
        myFixture.configureByText("main.cr", "require \"./lib/kemal/src/kemal\"\nx = Kem<caret>")
        val names = myFixture.complete(CompletionType.BASIC)?.map { it.lookupString } ?: emptyList()
        assertTrue("Installed shard module 'Kemal' must be suggested: $names", names.contains("Kemal"))
    }

    private fun openMain(content: String, caretOffset: Int) {
        writeProjectFile("src/main.cr", content)
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        val base = requireNotNull(project.basePath)
        val file = requireNotNull(LocalFileSystem.getInstance().findFileByPath("$base/src/main.cr"))
        myFixture.openFileInEditor(file)
        myFixture.editor.caretModel.moveToOffset(caretOffset)
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
                    fileSystem.findFileByPath("$basePath/$path")?.delete(this@CrystalShardLibCompletionTest)
                }
            }
        } finally {
            super.tearDown()
        }
    }
}
