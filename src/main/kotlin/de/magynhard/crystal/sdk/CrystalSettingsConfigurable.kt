package de.magynhard.crystal.sdk

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
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
    private var stdlibStatusLabel: JLabel = JBLabel("")
    private var stdlibVersionLabel: JLabel = JBLabel("")
    private var stdlibPathLabel: JLabel = JBLabel("")

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
        }.also {
            // Load current state
            val settings = CrystalSettings.getInstance(project)
            crystalPathField.text = settings.state.crystalPath
            updateVersion(settings.getEffectiveCrystalPath())
            updateStdlibStatus()
        }
    }

    override fun isModified(): Boolean {
        val settings = CrystalSettings.getInstance(project)
        return crystalPathField.text != settings.state.crystalPath
    }

    override fun apply() {
        val settings = CrystalSettings.getInstance(project)
        val oldRoots = resolveStdlibRoots()
        settings.state.crystalPath = crystalPathField.text
        // Invalidate any cached stdlib path so the next call re-runs
        // `crystal env CRYSTAL_PATH` against the newly configured SDK.
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        CrystalStdlibIndexRefresher.refresh(project, oldRoots, resolveStdlibRoots())
    }

    override fun reset() {
        val settings = CrystalSettings.getInstance(project)
        crystalPathField.text = settings.state.crystalPath
        updateVersion(settings.getEffectiveCrystalPath())
        updateStdlibStatus()
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
}
