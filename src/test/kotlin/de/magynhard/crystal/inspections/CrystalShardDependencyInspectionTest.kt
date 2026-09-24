package de.magynhard.crystal.inspections

import com.intellij.openapi.application.ApplicationManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CrystalShardDependencyInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalShardDependencyInspection::class.java)
    }

    fun testScannerFindsDependencyKeys() {
        val inspection = CrystalShardDependencyInspection()
        val ranges = inspection.shardDependencyKeyRanges(
            "name: app\n" +
                "dependencies:\n" +
                "  kemal:\n" +
                "    github: kemalcr/kemal\n" +
                "    version: \"~> 1.0\"\n" +
                "  pg:\n" +
                "    git: https://github.com/will/crystal-pg.git\n" +
                "development_dependencies:\n" +
                "  ameba:\n" +
                "    github: crystal-ameba/ameba\n"
        )
        assertEquals(setOf("kemal", "pg", "ameba"), ranges.keys)
        assertEquals(TextRange(26, 31), ranges.getValue("kemal"))
    }

    fun testScannerIgnoresCommentsAndNestedKeys() {
        val inspection = CrystalShardDependencyInspection()
        val ranges = inspection.shardDependencyKeyRanges(
            "# leading comment\n" +
                "dependencies: # trailing comment\n" +
                "  # indented comment\n" +
                "  kemal:\n" +
                "    github: kemalcr/kemal\n" +
                "    version: \"~> 1.0\"\n" +
                "\n" +
                "description: done\n"
        )
        assertEquals(setOf("kemal"), ranges.keys)
    }

    fun testMissingDependencyIsErrorWithQuickfix() {
        val file = writeProjectFile(
            "shard.yml",
            "name: app\ndependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        )
        openBasePathFile(file)
        val errors = myFixture.doHighlighting()
            .filter { it.severity == HighlightSeverity.ERROR }
        assertEquals(1, errors.size)
        assertTrue(
            "Unexpected error text: ${errors.single().description}",
            errors.single().description?.contains("'kemal'") == true
        )
        // Caret on the dependency key offers the install quickfix.
        myFixture.editor.caretModel.moveToOffset(28)
        val intentions = myFixture.filterAvailableIntentions("Run 'shards install'")
        assertFalse("Install quickfix must be offered", intentions.isEmpty())
    }

    fun testCleanManifestIsSilent() {
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
        openBasePathFile(file)
        myFixture.checkHighlighting()
    }

    fun testLibManifestIsSilent() {
        writeProjectFile("shard.yml", "name: app\n")
        writeProjectFile(
            "lib/kemal/shard.yml",
            "name: kemal\ndependencies:\n  missing_dep:\n    github: org/missing_dep\n"
        )
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/lib/kemal/shard.yml")
        )
        openBasePathFile(file)
        myFixture.checkHighlighting()
    }

    /**
     * Files under the project base path are outside the fixture content
     * roots; the highlighting daemon skips them until the base path joins
     * the module roots (mirrors production, where the project directory
     * is content).
     */
    private fun openBasePathFile(file: com.intellij.openapi.vfs.VirtualFile) {
        ModuleRootModificationUtil.updateModel(myFixture.module) { model ->
            model.addContentEntry("file://${requireNotNull(project.basePath)}")
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        myFixture.openFileInEditor(file)
    }

    private fun writeProjectFile(path: String, content: String): com.intellij.openapi.vfs.VirtualFile {
        var result: com.intellij.openapi.vfs.VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
            val base = requireNotNull(project.basePath)
            val parentPath = path.substringBeforeLast('/', "")
            val parent = VfsUtil.createDirectories(if (parentPath.isEmpty()) base else "$base/$parentPath")
            result = parent.findChild(path.substringAfterLast('/'))
                ?: parent.createChildData(this, path.substringAfterLast('/'))
            VfsUtil.saveText(result!!, content)
        }
        return requireNotNull(result)
    }

    override fun tearDown() {
        try {
            ApplicationManager.getApplication().runWriteAction {
                val basePath = project.basePath ?: return@runWriteAction
                val fileSystem = LocalFileSystem.getInstance()
                for (path in listOf("shard.yml", "shard.lock", "lib", "src")) {
                    fileSystem.findFileByPath("$basePath/$path")?.delete(this@CrystalShardDependencyInspectionTest)
                }
            }
        } finally {
            super.tearDown()
        }
    }
}
