package de.magynhard.crystal.sdk

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JLabel

class CrystalSettingsConfigurable(private val project: Project) : Configurable {

    private lateinit var crystalPathField: TextFieldWithBrowseButton
    private var versionLabel: JLabel = JBLabel("")

    override fun getDisplayName(): String = "Crystal"

    override fun createComponent(): JComponent {
        crystalPathField = TextFieldWithBrowseButton()
        crystalPathField.addBrowseFolderListener(
            "Select Crystal Executable",
            "Path to the Crystal compiler executable",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        return panel {
            group("Crystal SDK") {
                row("Crystal path:") {
                    cell(crystalPathField).align(AlignX.FILL)
                }
                row("") {
                    button("Detect") {
                        val detected = CrystalSdkDetector.detect()
                        if (detected != null) {
                            crystalPathField.text = detected
                            updateVersion(detected)
                        } else {
                            versionLabel.text = "Crystal not found"
                        }
                    }
                    cell(versionLabel)
                }
                row {
                    comment("Leave empty to auto-detect from PATH.")
                }
            }
        }.also {
            // Load current state
            val settings = CrystalSettings.getInstance(project)
            crystalPathField.text = settings.state.crystalPath
            updateVersion(settings.getEffectiveCrystalPath())
        }
    }

    override fun isModified(): Boolean {
        val settings = CrystalSettings.getInstance(project)
        return crystalPathField.text != settings.state.crystalPath
    }

    override fun apply() {
        val settings = CrystalSettings.getInstance(project)
        settings.state.crystalPath = crystalPathField.text
    }

    override fun reset() {
        val settings = CrystalSettings.getInstance(project)
        crystalPathField.text = settings.state.crystalPath
        updateVersion(settings.getEffectiveCrystalPath())
    }

    private fun updateVersion(path: String) {
        val version = CrystalSdkDetector.validate(path)
        versionLabel.text = version ?: "Not detected"
    }
}
