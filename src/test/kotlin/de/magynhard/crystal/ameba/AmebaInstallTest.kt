package de.magynhard.crystal.ameba

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.AmebaDetector
import de.magynhard.crystal.sdk.CrystalSettings

class AmebaInstallTest : BasePlatformTestCase() {

    private var savedEnabled = false
    private var savedPath = ""

    override fun setUp() {
        super.setUp()
        val state = CrystalSettings.getInstance(project).state
        savedEnabled = state.amebaEnabled
        savedPath = state.amebaPath
        state.amebaEnabled = false
        state.amebaPath = ""
        AmebaBinary.clearCache(project)
        PropertiesComponent.getInstance(project).unsetValue("crystal.ameba.install.dismissed")
    }

    override fun tearDown() {
        try {
            val state = CrystalSettings.getInstance(project).state
            state.amebaEnabled = savedEnabled
            state.amebaPath = savedPath
            AmebaBinary.clearCache(project)
            PropertiesComponent.getInstance(project).unsetValue("crystal.ameba.install.dismissed")
            deleteProjectFile("shard.yml")
        } finally {
            super.tearDown()
        }
    }

    fun testEnsureDevDependencyAppendsMissingSection() {
        val result = AmebaInstall.ensureDevDependency("name: app\nversion: 0.1.0\n")
        assertNotNull(result)
        assertTrue(result!!.contains("development_dependencies:"))
        assertTrue(result.contains("  ameba:"))
        assertTrue(result.contains("github: crystal-ameba/ameba"))
        assertTrue(result.contains("version: ~> 1.7.0"))
        assertTrue(result.contains("name: app"))
    }

    fun testEnsureDevDependencyInsertsIntoExistingSection() {
        val text = "name: app\ndevelopment_dependencies:\n  kemal:\n    github: kemalcr/kemal\n"
        val result = AmebaInstall.ensureDevDependency(text)
        assertNotNull(result)
        assertTrue(result!!.contains("  ameba:"))
        assertTrue(result.contains("  kemal:"))
    }

    fun testEnsureDevDependencyLeavesExistingEntry() {
        val text = "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n"
        assertNull(AmebaInstall.ensureDevDependency(text))
    }

    fun testEnsureDevDependencyIgnoresAmebaTarget() {
        // An `ameba:` key under `targets:` is not a dependency.
        val text = "name: app\ntargets:\n  ameba:\n    main: lib/ameba/bin/ameba.cr\n"
        assertNotNull(AmebaInstall.ensureDevDependency(text))
    }

    fun testEnsureAmebaTargetAppendsMissingSection() {
        val result = AmebaInstall.ensureAmebaTarget("name: app\n")
        assertNotNull(result)
        assertTrue(result!!.contains("targets:"))
        assertTrue(result.contains("  ameba:"))
        assertTrue(result.contains("main: lib/ameba/bin/ameba.cr"))
    }

    fun testEnsureAmebaTargetKeepsCustomMain() {
        val text = "targets:\n  ameba:\n    main: src/custom.cr\n"
        assertNull(AmebaInstall.ensureAmebaTarget(text))
    }

    fun testShouldOfferInstallWhenEnabledAndNothingResolves() {
        // Hermeticity: a real ameba on PATH would resolve and rightfully
        // suppress the offer.
        if (AmebaDetector.detect() != null) return
        writeProjectFile("shard.yml", "name: app\n")
        CrystalSettings.getInstance(project).state.amebaEnabled = true
        AmebaBinary.clearCache(project)
        assertTrue(AmebaInstall.shouldOfferInstall(project))
    }

    fun testShouldNotOfferWhenDisabled() {
        writeProjectFile("shard.yml", "name: app\n")
        assertFalse(AmebaInstall.shouldOfferInstall(project))
    }

    fun testShouldNotOfferWithManualPath() {
        writeProjectFile("shard.yml", "name: app\n")
        CrystalSettings.getInstance(project).state.amebaEnabled = true
        CrystalSettings.getInstance(project).state.amebaPath = "/nonexistent/ameba"
        AmebaBinary.clearCache(project)
        assertFalse(AmebaInstall.shouldOfferInstall(project))
    }

    fun testDismissSuppressesOffer() {
        if (AmebaDetector.detect() != null) return
        writeProjectFile("shard.yml", "name: app\n")
        CrystalSettings.getInstance(project).state.amebaEnabled = true
        AmebaBinary.clearCache(project)
        assertTrue(AmebaInstall.shouldOfferInstall(project))
        AmebaInstall.dismiss(project)
        assertTrue(AmebaInstall.isDismissed(project))
        assertFalse(AmebaInstall.shouldOfferInstall(project))
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
            LocalFileSystem.getInstance()
                .findFileByPath("$base/$path")?.delete(this@AmebaInstallTest)
        }
    }
}
