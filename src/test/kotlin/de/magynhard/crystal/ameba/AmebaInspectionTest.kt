package de.magynhard.crystal.ameba

import com.intellij.codeInspection.InspectionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.CrystalSettings
import java.io.File

class AmebaInspectionTest : BasePlatformTestCase() {

    private var savedEnabled = false
    private var savedPath = ""
    private var savedConfig = ""

    override fun setUp() {
        super.setUp()
        val state = CrystalSettings.getInstance(project).state
        savedEnabled = state.amebaEnabled
        savedPath = state.amebaPath
        savedConfig = state.amebaConfigPath
    }

    override fun tearDown() {
        try {
            val state = CrystalSettings.getInstance(project).state
            state.amebaEnabled = savedEnabled
            state.amebaPath = savedPath
            state.amebaConfigPath = savedConfig
            de.magynhard.crystal.sdk.AmebaBinary.clearCache(project)
            deleteProjectFile("main.cr")
        } finally {
            super.tearDown()
        }
    }

    fun testOnTheFlyCheckFileNeverRunsBinary() {
        if (SystemInfo.isWindows) return
        val binary = writeFakeAmeba()
        enableAmeba(binary.absolutePath)
        val file = writeProjectFile("main.cr", "a.try { |i| i.odd? }\n")
        val psi = requireNotNull(PsiManager.getInstance(project).findFile(file))

        // Threading contract: the 3-arg overload runs under a read action
        // (live highlighting and batch alike), where synchronous process
        // execution is forbidden — and it must not duplicate the annotator's
        // diagnostics. Batch work happens only in runBatchCheck.
        // onTheFly=true exercises the live path, false the batch dispatch.
        assertNull(AmebaInspection().checkFile(psi, InspectionManager.getInstance(project), true))
        assertNull(AmebaInspection().checkFile(psi, InspectionManager.getInstance(project), false))
    }

    fun testBatchCheckReportsFakeFindings() {
        if (SystemInfo.isWindows) return
        val binary = writeFakeAmeba()
        enableAmeba(binary.absolutePath)
        val file = writeProjectFile("main.cr", "a.try { |i| i.odd? }\n")
        val psi = requireNotNull(PsiManager.getInstance(project).findFile(file))

        val descriptors = AmebaInspection().runBatchCheck(psi, InspectionManager.getInstance(project))

        assertNotNull(descriptors)
        assertEquals(1, descriptors!!.size)
        val descriptor = descriptors.single()
        assertTrue(descriptor.descriptionTemplate.contains("short block"))
        assertTrue(descriptor.descriptionTemplate.contains("Style/VerboseBlock"))
        assertEquals("try { |i| i.odd? }", fileTextRange(descriptor))
        assertTrue(descriptor.fixes?.any { it is AmebaFileFix } == true)
    }

    fun testBatchCheckSilentWhenDisabled() {
        CrystalSettings.getInstance(project).state.amebaEnabled = false
        AmebaBinary.clearCache(project)
        if (SystemInfo.isWindows) return
        writeFakeAmeba()
        // amebaEnabled stays false: no diagnostics, no binary required.
        val file = writeProjectFile("main.cr", "a.try { |i| i.odd? }\n")
        val psi = requireNotNull(PsiManager.getInstance(project).findFile(file))

        val descriptors = AmebaInspection().runBatchCheck(psi, InspectionManager.getInstance(project))

        assertTrue(descriptors == null || descriptors.isEmpty())
    }

    fun testBatchCheckSilentWithoutBinary() {
        CrystalSettings.getInstance(project).state.amebaEnabled = true
        CrystalSettings.getInstance(project).state.amebaPath = "/nonexistent/ameba"
        de.magynhard.crystal.sdk.AmebaBinary.clearCache(project)
        val file = writeProjectFile("main.cr", "puts 1\n")
        val psi = requireNotNull(PsiManager.getInstance(project).findFile(file))

        val descriptors = AmebaInspection().runBatchCheck(psi, InspectionManager.getInstance(project))

        assertTrue(descriptors == null || descriptors.isEmpty())
    }

    fun testBatchCheckReportsEcrFindingsAtTemplatePositions() {
        if (SystemInfo.isWindows) return
        val binary = writeEcrFakeAmeba()
        enableAmeba(binary.absolutePath)
        val file = writeProjectFile(
            "page.ecr",
            "<h1>Hi <%= name %></h1>\n<% greeting = \"hi\" %>\n<% greeting = \"hello\" %>\n<p><%= greeting %></p>\n"
        )
        val psi = requireNotNull(PsiManager.getInstance(project).findFile(file))

        val descriptors = AmebaInspection().runBatchCheck(psi, InspectionManager.getInstance(project))

        assertNotNull(descriptors)
        assertEquals(1, descriptors!!.size)
        val descriptor = descriptors.single()
        assertTrue(descriptor.descriptionTemplate.contains("Useless assignment"))
        // Template coordinates (line 2, `greeting`), not generated code.
        assertEquals("greeting", fileTextRange(descriptor))
    }

    private fun fileTextRange(descriptor: com.intellij.codeInspection.ProblemDescriptor): String {
        val element = descriptor.psiElement ?: return ""
        val file = element.containingFile ?: return ""
        val range = descriptor.textRangeInElement ?: return ""
        return file.text.substring(range.startOffset, range.endOffset)
    }

    private fun enableAmeba(path: String) {
        val state = CrystalSettings.getInstance(project).state
        state.amebaEnabled = true
        state.amebaPath = path
        state.amebaConfigPath = ""
        de.magynhard.crystal.sdk.AmebaBinary.clearCache(project)
    }

    private var fakeCounter = 0

    private fun writeFakeAmeba(): File {
        val base = requireNotNull(project.basePath)
        fakeCounter++
        val file = File(base, "fake-ameba-$fakeCounter")
        file.parentFile.mkdirs()
        file.writeText(
            """
            #!/bin/sh
            if [ "$1" = "--version" ]; then
              echo "ameba 1.7.0"
              exit 0
            fi
            cat > /dev/null
            echo '{"sources": [{"path": "main.cr", "issues": [{"rule_name": "Style/VerboseBlock", "severity": "Convention", "message": "Use short block notation.", "location": {"line": 1, "column": 3}, "end_location": {"line": 1, "column": 20}}]}]}'
            exit 1
            """.trimIndent() + "\n"
        )
        assertTrue(file.setExecutable(true))
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        return file
    }

    private fun writeEcrFakeAmeba(): File {
        val base = requireNotNull(project.basePath)
        fakeCounter++
        val file = File(base, "fake-ameba-$fakeCounter")
        file.parentFile.mkdirs()
        file.writeText(
            """
            #!/bin/sh
            if [ "$1" = "--version" ]; then
              echo "ameba 1.7.0"
              exit 0
            fi
            cat > /dev/null
            echo '{"sources": [{"path": "page.ecr", "issues": [{"rule_name": "Lint/UselessAssign", "severity": "Warning", "message": "Useless assignment to variable `greeting`.", "location": {"line": 2, "column": 4}, "end_location": {"line": 2, "column": 11}}]}]}'
            exit 1
            """.trimIndent() + "\n"
        )
        assertTrue(file.setExecutable(true))
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        return file
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
            LocalFileSystem.getInstance().findFileByPath("$base/$path")?.delete(this@AmebaInspectionTest)
        }
    }
}
