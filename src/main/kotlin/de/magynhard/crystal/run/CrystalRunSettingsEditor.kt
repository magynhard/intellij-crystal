package de.magynhard.crystal.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class CrystalRunSettingsEditor(private val project: Project) : SettingsEditor<CrystalRunConfiguration>() {

    private val commandCombo = ComboBox(CrystalCommand.entries.toTypedArray())
    private val fileField = TextFieldWithBrowseButton()
    private val argumentsField = JBTextField()
    private val workDirField = TextFieldWithBrowseButton()
    private val envField = JBTextField()
    private val crystalPathField = JBTextField()

    init {
        fileField.addBrowseFolderListener(
            "Select Crystal File", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("cr")
        )
        workDirField.addBrowseFolderListener(
            "Select Working Directory", null, project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }

    override fun resetEditorFrom(config: CrystalRunConfiguration) {
        commandCombo.selectedItem = config.command
        fileField.text = config.filePath
        argumentsField.text = config.arguments
        workDirField.text = config.workingDirectory
        envField.text = config.environmentVariables
        crystalPathField.text = config.crystalPath
    }

    override fun applyEditorTo(config: CrystalRunConfiguration) {
        config.command = commandCombo.selectedItem as CrystalCommand
        config.filePath = fileField.text
        config.arguments = argumentsField.text
        config.workingDirectory = workDirField.text
        config.environmentVariables = envField.text
        config.crystalPath = crystalPathField.text
    }

    override fun createEditor(): JComponent {
        return panel {
            row("Command:") { cell(commandCombo) }
            row("File:") { cell(fileField).align(AlignX.FILL) }
            row("Arguments:") { cell(argumentsField).align(AlignX.FILL) }
            row("Working directory:") { cell(workDirField).align(AlignX.FILL) }
            row("Environment variables:") {
                cell(envField).align(AlignX.FILL)
                    .comment("KEY=VALUE (one per line)")
            }
            row("Crystal path:") {
                cell(crystalPathField).align(AlignX.FILL)
                    .comment("Path to crystal binary (default: crystal)")
            }
        }
    }
}
