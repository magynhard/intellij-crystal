package de.magynhard.crystal.ameba

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AmebaBuildBannerProviderTest : BasePlatformTestCase() {

    fun testBannerShowsForRootManifestWithDeclaredButMissingBinary() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n"
        )
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
        )
        assertTrue(AmebaBuildBannerProvider().bannerNeeded(project, file))
    }

    fun testBannerSilentWhenBinaryPresent() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n"
        )
        writeProjectFile("bin/ameba", "#!/bin/sh\necho ameba\n")
        val base = requireNotNull(project.basePath)
        java.io.File(base, "bin/ameba").setExecutable(true)
        val file = requireNotNull(LocalFileSystem.getInstance().findFileByPath("$base/shard.yml"))
        assertFalse(AmebaBuildBannerProvider().bannerNeeded(project, file))
    }

    fun testBannerSilentWithoutDeclaration() {
        writeProjectFile("shard.yml", "name: app\n")
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
        )
        assertFalse(AmebaBuildBannerProvider().bannerNeeded(project, file))
    }

    fun testBannerSilentForOtherFiles() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n"
        )
        writeProjectFile("src/app.cr", "puts 1")
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/src/app.cr")
        )
        assertFalse(AmebaBuildBannerProvider().bannerNeeded(project, file))
    }

    fun testBannerShowsPinnedOldWarning() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n    version: 1.6.0\n"
        )
        writeExecutableProjectFile("bin/ameba")
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
        )
        val state = AmebaBuildBannerProvider().bannerState(project, file)
        assertTrue(state is AmebaBuildBannerProvider.BannerState.PinnedOld)
        assertEquals("1.6.0", (state as AmebaBuildBannerProvider.BannerState.PinnedOld).requirement)
    }

    fun testBannerSilentWithFreshPin() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n    version: \"~> 1.7.0\"\n"
        )
        writeExecutableProjectFile("bin/ameba")
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
        )
        assertEquals(
            AmebaBuildBannerProvider.BannerState.None,
            AmebaBuildBannerProvider().bannerState(project, file)
        )
    }

    fun testBannerSilentWithBranchPin() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n    branch: master\n"
        )
        writeExecutableProjectFile("bin/ameba")
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
        )
        assertEquals(
            AmebaBuildBannerProvider.BannerState.None,
            AmebaBuildBannerProvider().bannerState(project, file)
        )
    }

    fun testMissingBinaryTakesPrecedenceOverOldPin() {
        writeProjectFile(
            "shard.yml",
            "name: app\ndevelopment_dependencies:\n  ameba:\n    github: crystal-ameba/ameba\n    version: 1.6.0\n"
        )
        val file = requireNotNull(
            LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
        )
        assertEquals(
            AmebaBuildBannerProvider.BannerState.BinaryMissing,
            AmebaBuildBannerProvider().bannerState(project, file)
        )
    }

    fun testBannerShowsInstallOfferWhenUndeclared() {
        writeProjectFile("shard.yml", "name: app\n")
        withAmebaEnabled {
            val file = requireNotNull(
                LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
            )
            assertEquals(
                AmebaBuildBannerProvider.BannerState.NotDeclared,
                AmebaBuildBannerProvider().bannerState(project, file)
            )
        }
    }

    fun testBannerSilentWhenInstallDismissed() {
        writeProjectFile("shard.yml", "name: app\n")
        withAmebaEnabled {
            AmebaInstall.dismiss(project)
            val file = requireNotNull(
                LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
            )
            assertEquals(
                AmebaBuildBannerProvider.BannerState.None,
                AmebaBuildBannerProvider().bannerState(project, file)
            )
        }
    }

    fun testBannerSilentWhenDisabled() {
        writeProjectFile("shard.yml", "name: app\n")
        val state = de.magynhard.crystal.sdk.CrystalSettings.getInstance(project).state
        val saved = state.amebaEnabled
        state.amebaEnabled = false
        de.magynhard.crystal.sdk.AmebaBinary.clearCache(project)
        try {
            val file = requireNotNull(
                LocalFileSystem.getInstance().findFileByPath("${requireNotNull(project.basePath)}/shard.yml")
            )
            assertEquals(
                AmebaBuildBannerProvider.BannerState.None,
                AmebaBuildBannerProvider().bannerState(project, file)
            )
        } finally {
            state.amebaEnabled = saved
            de.magynhard.crystal.sdk.AmebaBinary.clearCache(project)
        }
    }

    private fun withAmebaEnabled(block: () -> Unit) {
        val state = de.magynhard.crystal.sdk.CrystalSettings.getInstance(project).state
        val savedEnabled = state.amebaEnabled
        val savedPath = state.amebaPath
        state.amebaEnabled = true
        state.amebaPath = ""
        de.magynhard.crystal.sdk.AmebaBinary.clearCache(project)
        com.intellij.ide.util.PropertiesComponent.getInstance(project)
            .unsetValue("crystal.ameba.install.dismissed")
        try {
            block()
        } finally {
            state.amebaEnabled = savedEnabled
            state.amebaPath = savedPath
            de.magynhard.crystal.sdk.AmebaBinary.clearCache(project)
            com.intellij.ide.util.PropertiesComponent.getInstance(project)
                .unsetValue("crystal.ameba.install.dismissed")
        }
    }

    private fun writeExecutableProjectFile(path: String) {
        writeProjectFile(path, "#!/bin/sh\necho ameba\n")
        java.io.File(requireNotNull(project.basePath), path).setExecutable(true)
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
                for (path in listOf("shard.yml", "bin", "src")) {
                    fileSystem.findFileByPath("$basePath/$path")?.delete(this@AmebaBuildBannerProviderTest)
                }
            }
        } finally {
            super.tearDown()
        }
    }
}
