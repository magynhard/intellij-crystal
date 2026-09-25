package de.magynhard.crystal.sdk

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import javax.swing.JComponent
import javax.swing.JLabel

class CrystalSettingsConfigurable private constructor(
    private val project: Project,
    private val refreshStdlibRoots: (oldRoots: List<VirtualFile>, newRoots: List<VirtualFile>) -> Unit,
) : Configurable {

    constructor(project: Project) : this(
        project,
        { oldRoots, newRoots -> CrystalStdlibIndexRefresher.refresh(project, oldRoots, newRoots) },
    )

    private lateinit var crystalPathField: TextFieldWithBrowseButton
    private var versionLabel: JLabel = JBLabel("")
    private var stdlibStatusLabel: JLabel = JBLabel("")
    private var stdlibVersionLabel: JLabel = JBLabel("")
    private var stdlibPathLabel: JLabel = JBLabel("")
    // Eager defaults (not lateinit): tests drive apply()/isModified() with
    // only some fields injected and must never see uninitialized access.
    // createComponent() replaces these with the live widgets.
    private var amebaEnabledBox: JBCheckBox = JBCheckBox("Enable Ameba linting")
    private var amebaPathField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
    private var amebaVersionLabel: JLabel = JBLabel("")
    private var amebaConfigField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
    private var amebaFixOnSaveBox: JBCheckBox = JBCheckBox("Run ameba --fix on save")

    override fun getDisplayName(): String = "Crystal"

    override fun createComponent(): JComponent {
        crystalPathField = TextFieldWithBrowseButton()
        crystalPathField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.singleFile()
                    .withTitle("Select Crystal Executable")
                    .withDescription("Path to the Crystal compiler executable"),
                project
            )
        )
        amebaEnabledBox = JBCheckBox("Enable Ameba linting")
        amebaPathField = TextFieldWithBrowseButton()
        amebaPathField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.singleFile()
                    .withTitle("Select Ameba Executable")
                    .withDescription("Path to the Ameba linter executable (bin/ameba)"),
                project
            )
        )
        amebaConfigField = TextFieldWithBrowseButton()
        amebaConfigField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.singleFile()
                    .withTitle("Select Ameba Configuration")
                    .withDescription("Path to a custom .ameba.yml configuration file"),
                project
            )
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
            group("Standard Library") {
                row("Status:") {
                    cell(stdlibStatusLabel).align(AlignX.FILL)
                }
                row("Version:") {
                    cell(stdlibVersionLabel).align(AlignX.FILL)
                }
                row("Path:") {
                    cell(stdlibPathLabel).align(AlignX.FILL)
                }
                row {
                    button("Force Re-index") {
                        forceReindex()
                    }
                }
            }
            group("Ameba Linter") {
                row {
                    cell(amebaEnabledBox)
                }
                row("Ameba path:") {
                    cell(amebaPathField).align(AlignX.FILL)
                }
                row("") {
                    button("Detect") {
                        val detected = AmebaDetector.detect()
                        if (detected != null) {
                            amebaPathField.text = detected
                            updateAmebaVersion(detected)
                        } else {
                            amebaVersionLabel.text = "Ameba not found"
                        }
                    }
                    cell(amebaVersionLabel)
                }
                row {
                    comment("Leave empty to resolve bin/ameba, then PATH. A set path wins and must validate.")
                }
                row("Config file:") {
                    cell(amebaConfigField).align(AlignX.FILL)
                }
                row {
                    comment("Leave empty to use the nearest .ameba.yml above the linted file.")
                }
                row {
                    cell(amebaFixOnSaveBox)
                }
                row {
                    comment("Runs ameba --fix on Crystal files after saving (never on ECR templates).")
                }
            }
        }.also {
            // Load current state
            val settings = CrystalSettings.getInstance(project)
            crystalPathField.text = settings.state.crystalPath
            updateVersion(settings.getEffectiveCrystalPath())
            updateStdlibStatus()
            amebaEnabledBox.isSelected = settings.state.amebaEnabled
            amebaPathField.text = settings.state.amebaPath
            updateAmebaVersion(AmebaBinary.resolve(project)?.path ?: settings.state.amebaPath)
            amebaConfigField.text = settings.state.amebaConfigPath
            amebaFixOnSaveBox.isSelected = settings.state.amebaFixOnSave
        }
    }

    override fun isModified(): Boolean {
        val settings = CrystalSettings.getInstance(project)
        return crystalPathField.text != settings.state.crystalPath ||
            amebaEnabledBox.isSelected != settings.state.amebaEnabled ||
            amebaPathField.text != settings.state.amebaPath ||
            amebaConfigField.text != settings.state.amebaConfigPath ||
            amebaFixOnSaveBox.isSelected != settings.state.amebaFixOnSave
    }

    override fun apply() {
        val settings = CrystalSettings.getInstance(project)
        val amebaChanged = amebaEnabledBox.isSelected != settings.state.amebaEnabled ||
            amebaPathField.text != settings.state.amebaPath ||
            amebaConfigField.text != settings.state.amebaConfigPath ||
            amebaFixOnSaveBox.isSelected != settings.state.amebaFixOnSave
        val oldRoots = resolveStdlibRoots()
        settings.state.crystalPath = crystalPathField.text
        settings.state.amebaEnabled = amebaEnabledBox.isSelected
        settings.state.amebaPath = amebaPathField.text
        settings.state.amebaConfigPath = amebaConfigField.text
        settings.state.amebaFixOnSave = amebaFixOnSaveBox.isSelected
        AmebaBinary.clearCache(project)
        AmebaNotifications.reset(project)
        if (amebaChanged) {
            // Re-run highlighting so Ameba diagnostics appear or disappear
            // without waiting for the next keystroke; stdlib roots are
            // untouched by Ameba settings.
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
        // Invalidate any cached stdlib path so the next call re-runs
        // `crystal env CRYSTAL_PATH` against the newly configured SDK.
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        val requireGraph = CrystalRequireGraphService.getInstance(project)
        try {
            requireGraph.invalidateAll()
            val newRoots = resolveStdlibRoots()
            refreshStdlibRoots(oldRoots, newRoots)
        } finally {
            requireGraph.invalidateAll()
        }
    }

    override fun reset() {
        val settings = CrystalSettings.getInstance(project)
        crystalPathField.text = settings.state.crystalPath
        updateVersion(settings.getEffectiveCrystalPath())
        updateStdlibStatus()
        amebaEnabledBox.isSelected = settings.state.amebaEnabled
        amebaPathField.text = settings.state.amebaPath
        updateAmebaVersion(AmebaBinary.resolve(project)?.path ?: settings.state.amebaPath)
        amebaConfigField.text = settings.state.amebaConfigPath
        amebaFixOnSaveBox.isSelected = settings.state.amebaFixOnSave
    }

    private fun updateAmebaVersion(path: String) {
        if (path.isBlank()) {
            amebaVersionLabel.text = "Not configured"
            return
        }
        val raw = AmebaDetector.validate(path)
        if (raw == null) {
            amebaVersionLabel.text = "Not detected"
            return
        }
        val warning = AmebaVersion.warningForRawVersion(path, raw)
        amebaVersionLabel.text = warning
            ?: raw.lineSequence().firstOrNull().orEmpty().ifBlank { "Detected" }
    }

    private fun updateVersion(path: String) {
        val version = CrystalSdkDetector.validate(path)
        versionLabel.text = version ?: "Not detected"
    }

    private fun updateStdlibStatus() {
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project)
        val version = CrystalStdlibResolver.resolveCrystalVersion(project)
        if (stdlibRoot != null) {
            stdlibStatusLabel.text = "Available"
            stdlibVersionLabel.text = version ?: "unknown"
            stdlibPathLabel.text = stdlibRoot.path
        } else {
            stdlibStatusLabel.text = "Not available"
            stdlibVersionLabel.text = "-"
            stdlibPathLabel.text = "Crystal not installed or not configured"
        }
    }

    private fun forceReindex() {
        val stdlibRoots = resolveStdlibRoots()
        if (stdlibRoots.isEmpty()) return
        val version = CrystalStdlibResolver.resolveCrystalVersion(project) ?: "unknown"

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Re-indexing Crystal Stdlib", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                indicator.isIndeterminate = true
                // Notify the platform that the synthetic library changed, then
                // force each filtered Crystal source through the current indexes.
                // This is necessary because StubUpdatingIndex caches by content hash —
                // if the file content hasn't changed, the old stubs are reused even
                // though the BNF grammar (and thus stub structure) may have changed.
                indicator.text = "Re-indexing Crystal Stdlib files ($version)..."
                CrystalStdlibIndexRefresher.refresh(project, emptyList(), stdlibRoots, indicator)
            }

            override fun onSuccess() {
                updateStdlibStatus()
                ApplicationManager.getApplication().invokeLater {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Crystal Reindex")
                        .createNotification(
                            "Crystal Stdlib reindex requested",
                            "The filtered standard-library sources are rebuilding in the background (Crystal $version). Completion and navigation will become available as files are indexed.",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                }
            }
        })
    }

    private fun resolveStdlibRoots() =
        if (CrystalProjectDetector.isCrystalProject(project)) {
            CrystalStdlibResolver.resolveStdlibPath(project)?.let(CrystalStdlibRoots::enumerate).orEmpty()
        } else {
            emptyList()
        }

    companion object {
        internal fun forTest(
            project: Project,
            refreshStdlibRoots: (oldRoots: List<VirtualFile>, newRoots: List<VirtualFile>) -> Unit,
        ): CrystalSettingsConfigurable = CrystalSettingsConfigurable(project, refreshStdlibRoots)
    }
}
