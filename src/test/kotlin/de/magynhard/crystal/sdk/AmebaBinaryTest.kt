package de.magynhard.crystal.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class AmebaBinaryTest : BasePlatformTestCase() {

    private lateinit var tempBin: File
    private var savedAmebaPath = ""
    private var savedAmebaEnabled = false
    private var savedAmebaConfig = ""

    override fun setUp() {
        super.setUp()
        val state = CrystalSettings.getInstance(project).state
        savedAmebaPath = state.amebaPath
        savedAmebaEnabled = state.amebaEnabled
        savedAmebaConfig = state.amebaConfigPath
        state.amebaPath = ""
        state.amebaConfigPath = ""
        tempBin = FileUtil.createTempDirectory("ameba-binary", null)
        AmebaBinary.clearCache(project)
    }

    override fun tearDown() {
        try {
            val state = CrystalSettings.getInstance(project).state
            state.amebaPath = savedAmebaPath
            state.amebaEnabled = savedAmebaEnabled
            state.amebaConfigPath = savedAmebaConfig
            AmebaBinary.clearCache(project)
            FileUtil.delete(tempBin)
            deleteProjectFile("shard.yml")
            deleteProjectFile("bin/ameba")
        } finally {
            super.tearDown()
        }
    }

    fun testManualPathWinsOverEverything() {
        if (SystemInfo.isWindows) return
        val manual = writeFake(tempBin, "manual-ameba", "ameba 9.9.9")
        writeProjectShard("development_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n")
        writeProjectBinary("bin/ameba")
        CrystalSettings.getInstance(project).state.amebaPath = manual.absolutePath
        AmebaBinary.clearCache(project)

        val resolved = AmebaBinary.resolve(project) { "/nonexistent/system-ameba" }
        assertNotNull(resolved)
        assertEquals(manual.absolutePath, resolved!!.path)
        assertEquals(AmebaBinary.Source.SETTINGS, resolved.source)
    }

    fun testBrokenManualPathResolvesToNothing() {
        CrystalSettings.getInstance(project).state.amebaPath = "/nonexistent/ameba"
        AmebaBinary.clearCache(project)
        writeProjectShard("development_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n")
        writeProjectBinary("bin/ameba")

        // No silent fallback: explicit misconfiguration surfaces as missing.
        assertNull(AmebaBinary.resolve(project) { null })
    }

    fun testProjectBinaryWinsOverSystem() {
        if (SystemInfo.isWindows) return
        val system = writeFake(tempBin, "system-ameba", "ameba 1.7.0")
        writeProjectShard("development_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n")
        val local = writeProjectBinary("bin/ameba")
        AmebaBinary.clearCache(project)

        val resolved = AmebaBinary.resolve(project) { system.absolutePath }
        assertNotNull(resolved)
        assertEquals(local.absolutePath, resolved!!.path)
        assertEquals(AmebaBinary.Source.PROJECT, resolved.source)
    }

    fun testProjectBinaryIgnoredWithoutDeclaration() {
        if (SystemInfo.isWindows) return
        val system = writeFake(tempBin, "system-ameba", "ameba 1.7.0")
        writeProjectShard("name: app\n")
        writeProjectBinary("bin/ameba")
        AmebaBinary.clearCache(project)

        val resolved = AmebaBinary.resolve(project) { system.absolutePath }
        assertNotNull(resolved)
        assertEquals(system.absolutePath, resolved!!.path)
        assertEquals(AmebaBinary.Source.SYSTEM, resolved.source)
    }

    fun testSystemBinaryUsedAsLastResort() {
        if (SystemInfo.isWindows) return
        val system = writeFake(tempBin, "system-ameba", "ameba 1.7.0")
        AmebaBinary.clearCache(project)

        val resolved = AmebaBinary.resolve(project) { system.absolutePath }
        assertNotNull(resolved)
        assertEquals(AmebaBinary.Source.SYSTEM, resolved!!.source)
    }

    fun testNothingResolvesWithoutAnyCandidate() {
        AmebaBinary.clearCache(project)
        assertNull(AmebaBinary.resolve(project) { null })
    }

    fun testOldManualBinaryResolvesToNothing() {
        if (SystemInfo.isWindows) return
        val manual = writeFake(tempBin, "old-ameba", "ameba 1.6.4")
        CrystalSettings.getInstance(project).state.amebaPath = manual.absolutePath
        AmebaBinary.clearCache(project)

        // No silent fallback for explicit configuration.
        assertNull(AmebaBinary.resolve(project) { null })
        val problem = AmebaBinary.versionProblem(project) { null }
        assertTrue(problem is AmebaBinary.VersionProblem.TooOld)
        assertEquals("1.6.4", (problem as AmebaBinary.VersionProblem.TooOld).version)
    }

    fun testOldProjectBinaryFallsThroughToSystem() {
        if (SystemInfo.isWindows) return
        val system = writeFake(tempBin, "system-ameba", "ameba 1.7.0")
        writeProjectShard("development_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n")
        writeOldProjectBinary("bin/ameba")
        AmebaBinary.clearCache(project)

        val resolved = AmebaBinary.resolve(project) { system.absolutePath }
        assertNotNull(resolved)
        assertEquals(system.absolutePath, resolved!!.path)
        assertEquals(AmebaBinary.Source.SYSTEM, resolved.source)
        assertNull(AmebaBinary.versionProblem(project) { system.absolutePath })
    }

    fun testOldSystemBinaryResolvesToNothing() {
        if (SystemInfo.isWindows) return
        val system = writeFake(tempBin, "old-system-ameba", "ameba 1.6.4")
        AmebaBinary.clearCache(project)

        assertNull(AmebaBinary.resolve(project) { system.absolutePath })
        assertTrue(AmebaBinary.versionProblem(project) { system.absolutePath } is AmebaBinary.VersionProblem.TooOld)
    }

    fun testVersionProblemNullWhenNothingConfigured() {
        AmebaBinary.clearCache(project)
        assertNull(AmebaBinary.versionProblem(project) { null })
    }

    fun testProjectBinaryMissingNeedsDeclarationAndBinary() {
        writeProjectShard("development_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n")
        assertTrue(AmebaBinary.projectBinaryMissing(project))
        assertTrue(AmebaBinary.declaresAmeba(project))
    }

    fun testProjectBinaryMissingSilentWithoutDeclaration() {
        writeProjectShard("name: app\n")
        assertFalse(AmebaBinary.declaresAmeba(project))
        assertFalse(AmebaBinary.projectBinaryMissing(project))
    }

    fun testResolutionIsCachedAcrossCalls() {
        if (SystemInfo.isWindows) return
        val system = writeFake(tempBin, "system-ameba", "ameba 1.7.0")
        AmebaBinary.clearCache(project)
        var calls = 0
        val lookup = {
            calls++
            system.absolutePath
        }
        assertNotNull(AmebaBinary.resolve(project, lookup))
        assertNotNull(AmebaBinary.resolve(project, lookup))
        // Second call served from cache: no repeated detection/validation.
        assertEquals(1, calls)
    }

    private fun writeFake(dir: File, name: String, version: String): File {
        val file = File(dir, name)
        file.writeText("#!/bin/sh\necho \"$version\"\nexit 0\n")
        assertTrue("Cannot make fake executable: $file", file.setExecutable(true))
        return file
    }

    private fun writeProjectShard(content: String) {
        writeProjectFile("shard.yml", content)
    }

    private fun writeProjectBinary(path: String): File {
        if (SystemInfo.isWindows) return File(requireNotNull(project.basePath), path)
        val base = requireNotNull(project.basePath)
        val file = File(base, path)
        file.parentFile.mkdirs()
        file.writeText("#!/bin/sh\necho \"ameba 1.7.0\"\nexit 0\n")
        assertTrue("Cannot make project fake executable: $file", file.setExecutable(true))
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        return file
    }

    private fun writeOldProjectBinary(path: String): File {
        if (SystemInfo.isWindows) return File(requireNotNull(project.basePath), path)
        val base = requireNotNull(project.basePath)
        val file = File(base, path)
        file.parentFile.mkdirs()
        file.writeText("#!/bin/sh\necho \"ameba 1.6.4\"\nexit 0\n")
        assertTrue("Cannot make project fake executable: $file", file.setExecutable(true))
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        return file
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

    private fun deleteProjectFile(path: String) {
        ApplicationManager.getApplication().runWriteAction {
            val base = project.basePath ?: return@runWriteAction
            LocalFileSystem.getInstance().findFileByPath("$base/$path")?.delete(this@AmebaBinaryTest)
        }
    }
}
