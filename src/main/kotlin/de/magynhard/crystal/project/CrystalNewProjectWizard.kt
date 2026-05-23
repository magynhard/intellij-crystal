package de.magynhard.crystal.project

import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignX
import de.magynhard.crystal.CrystalIcons
import de.magynhard.crystal.sdk.CrystalSdkDetector
import de.magynhard.crystal.sdk.CrystalSettings
import javax.swing.ButtonGroup
import javax.swing.Icon

class CrystalNewProjectWizard : LanguageGeneratorNewProjectWizard {

    override val icon: Icon = CrystalIcons.FILE
    override val name: String = "Crystal"
    override val ordinal: Int = 500

    override fun createStep(parent: NewProjectWizardStep): NewProjectWizardStep {
        return CrystalNewProjectStep(parent)
    }
}

class CrystalNewProjectStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    private val appRadio = JBRadioButton("Application", true)
    private val libRadio = JBRadioButton("Library", false)
    private val crystalPathField = TextFieldWithBrowseButton()

    init {
        val group = ButtonGroup()
        group.add(appRadio)
        group.add(libRadio)

        crystalPathField.addBrowseFolderListener(
            "Select Crystal Executable",
            "Path to the Crystal compiler executable",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        val detected = CrystalSdkDetector.detect()
        if (detected != null) {
            crystalPathField.text = detected
        }
    }

    override fun setupUI(builder: com.intellij.ui.dsl.builder.Panel) {
        builder.apply {
            group("Project Type") {
                buttonsGroup {
                    row {
                        cell(appRadio)
                        comment("Executable application (crystal init app)")
                    }
                    row {
                        cell(libRadio)
                        comment("Reusable library/shard (crystal init lib)")
                    }
                }
            }
            group("Crystal SDK") {
                row("Crystal path:") {
                    cell(crystalPathField).align(AlignX.FILL)
                }
                row("") {
                    button("Detect") {
                        val detected = CrystalSdkDetector.detect()
                        if (detected != null) {
                            crystalPathField.text = detected
                        }
                    }
                    comment("Leave empty to auto-detect from PATH.")
                }
            }
        }
    }

    override fun setupProject(project: Project) {
        val projectType = if (appRadio.isSelected) "app" else "lib"
        val crystalPath = crystalPathField.text.ifBlank {
            CrystalSdkDetector.detect() ?: "crystal"
        }

        // Save crystal path to settings
        if (crystalPathField.text.isNotBlank()) {
            CrystalSettings.getInstance(project).state.crystalPath = crystalPathField.text
        }

        val basePath = project.basePath ?: return
        val projectName = project.name

        // Create module with content root so the project tree shows files
        WriteAction.runAndWait<Exception> {
            val moduleManager = ModuleManager.getInstance(project)
            val modulePath = "$basePath/$projectName.iml"
            val module = moduleManager.newModule(modulePath, "WEB_MODULE")
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
