package de.magynhard.crystal.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import de.magynhard.crystal.CrystalIcons
import de.magynhard.crystal.sdk.CrystalSdkDetector
import de.magynhard.crystal.sdk.CrystalSettings
import javax.swing.ButtonGroup
import javax.swing.Icon
import javax.swing.JComponent

class CrystalDirectoryProjectGenerator : DirectoryProjectGeneratorBase<CrystalProjectSettings>() {

    override fun getName(): String = "Crystal"
    override fun getLogo(): Icon = CrystalIcons.FILE
    override fun getDescription(): String = "Create a new Crystal project (application or library)"

    override fun createPeer(): ProjectGeneratorPeer<CrystalProjectSettings> = CrystalProjectGeneratorPeer()

    override fun generateProject(
        project: Project,
        baseDir: VirtualFile,
        settings: CrystalProjectSettings,
        module: Module
    ) {
        val projectType = settings.projectType
        val crystalPath = settings.crystalPath.ifBlank {
            CrystalSdkDetector.detect() ?: "crystal"
        }

        // Save crystal path to settings
        if (settings.crystalPath.isNotBlank()) {
            CrystalSettings.getInstance(project).state.crystalPath = settings.crystalPath
        }

        val basePath = project.basePath ?: return
        val projectName = project.name

        // Ensure module has content root
        WriteAction.runAndWait<Exception> {
            ModuleRootModificationUtil.addContentRoot(module, basePath)
        }

        // Run crystal init on background thread, then refresh VFS
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val process = ProcessBuilder(crystalPath, "init", projectType, projectName)
                    .directory(java.io.File(basePath).parentFile)
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().readText()
                process.waitFor()

                // Append IDE-specific entries to .gitignore
                val gitignore = java.io.File(basePath, ".gitignore")
                if (gitignore.exists()) {
                    val content = gitignore.readText()
                    val additions = buildString {
                        if (!content.contains(".idea/")) appendLine(".idea/")
                        if (!content.contains("*.iml")) appendLine("*.iml")
                    }
                    if (additions.isNotBlank()) {
                        gitignore.appendText("\n# IDE\n$additions")
                    }
                }
            } catch (_: Exception) {
                // crystal not available
            }

            // Refresh VFS from background thread
            VfsUtil.markDirtyAndRefresh(false, true, true, java.io.File(basePath))
        }
    }
}

class CrystalProjectGeneratorPeer : GeneratorPeerImpl<CrystalProjectSettings>() {

    private val settings = CrystalProjectSettings()
    private var appRadio: JBRadioButton? = null
    private var libRadio: JBRadioButton? = null
    private var crystalPathField: TextFieldWithBrowseButton? = null
    private var panel: JComponent? = null

    override fun getSettings(): CrystalProjectSettings {
        appRadio?.let { settings.projectType = if (it.isSelected) "app" else "lib" }
        crystalPathField?.let { settings.crystalPath = it.text }
        return settings
    }

    override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
        if (panel == null) {
            val app = JBRadioButton("Application", true)
            val lib = JBRadioButton("Library", false)
            val pathField = TextFieldWithBrowseButton()
            val group = ButtonGroup()
            group.add(app)
            group.add(lib)

            pathField.addBrowseFolderListener(
                "Select Crystal Executable",
                "Path to the Crystal compiler executable",
                null,
                FileChooserDescriptorFactory.singleFile()
            )

            val detected = CrystalSdkDetector.detect()
            if (detected != null) {
                pathField.text = detected
            }

            appRadio = app
            libRadio = lib
            crystalPathField = pathField

            panel = panel {
                group("Project Type") {
                    buttonsGroup {
                        row {
                            cell(app)
                            comment("Executable application (crystal init app)")
                        }
                        row {
                            cell(lib)
                            comment("Reusable library/shard (crystal init lib)")
                        }
                    }
                }
                group("Crystal SDK") {
                    row("Crystal path:") {
                        cell(pathField).align(AlignX.FILL)
                    }
                    row("") {
                        button("Detect") {
                            val detected2 = CrystalSdkDetector.detect()
                            if (detected2 != null) {
                                pathField.text = detected2
                            }
                        }
                        comment("Leave empty to auto-detect from PATH.")
                    }
                }
            }
        }
        return panel!!
    }
}
