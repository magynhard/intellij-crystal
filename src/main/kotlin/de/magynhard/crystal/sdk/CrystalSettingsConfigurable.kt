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
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
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
        settings.state.crystalPath = crystalPathField.text
        // Invalidate any cached stdlib path so the next call re-runs
        // `crystal env CRYSTAL_PATH` against the newly configured SDK.
        CrystalStdlibResolver.clearCachedStdlibPath(project)
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
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project) ?: return
        val version = CrystalStdlibResolver.resolveCrystalVersion(project) ?: "unknown"
        val stdlibRoots = CrystalStdlibRoots.enumerate(stdlibRoot)
        val module = com.intellij.openapi.module.ModuleManager.getInstance(project).modules.firstOrNull() ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Re-indexing Crystal Stdlib", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                indicator.isIndeterminate = true

                // Step 1: Remove module library
                indicator.text = "Removing old Crystal Stdlib index..."
                ModuleRootModificationUtil.updateModel(module) { model ->
                    val existingLib = model.moduleLibraryTable.getLibraryByName(CrystalStdlibSourceRootConfigurator.LIBRARY_NAME)
                    if (existingLib != null) {
                        model.moduleLibraryTable.removeLibrary(existingLib)
                    }
                }

                // Step 2: Re-add module library with the filtered enumeration,
                // excluding compiler/, lib_c/, llvm/, and other non-stdlib subtrees.
                indicator.text = "Adding Crystal Stdlib ($version)..."
                ModuleRootModificationUtil.updateModel(module) { model ->
                    val library = model.moduleLibraryTable.createLibrary(CrystalStdlibSourceRootConfigurator.LIBRARY_NAME)
                    val libraryModel = library.modifiableModel
                    for (root in stdlibRoots) {
                        libraryModel.addRoot(root, OrderRootType.SOURCES)
                    }
                    libraryModel.commit()
                }

                // Step 3: Force re-index each stdlib file individually.
                // This is necessary because StubUpdatingIndex caches by content hash —
                // if the file content hasn't changed, the old stubs are reused even
                // though the BNF grammar (and thus stub structure) may have changed.
                indicator.text = "Re-indexing Crystal Stdlib files ($version)..."
                val stdlibFiles = collectCrystalFiles(stdlibRoots, indicator)
                var processed = 0
                for (file in stdlibFiles) {
                    if (indicator.isCanceled) break
                    indicator.text = "Re-indexing (${++processed}/${stdlibFiles.size}): ${file.name}"
                    com.intellij.util.indexing.FileBasedIndex.getInstance().requestReindex(file)
                }
            }

            override fun onSuccess() {
                updateStdlibStatus()
                ApplicationManager.getApplication().invokeLater {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Crystal Reindex")
                        .createNotification(
                            "Crystal Stdlib index cleared",
                            "The old index was removed and is now rebuilding in the background (Crystal $version). Completion and navigation will become available as files are indexed.",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                }
            }
        })
    }

    private fun collectCrystalFiles(
        roots: List<com.intellij.openapi.vfs.VirtualFile>,
        indicator: com.intellij.openapi.progress.ProgressIndicator
    ): List<com.intellij.openapi.vfs.VirtualFile> {
        val result = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
        val stack = ArrayDeque<com.intellij.openapi.vfs.VirtualFile>()
        roots.forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            if (indicator.isCanceled) break
            val file = stack.removeFirst()
            if (file.isDirectory) {
                file.children?.forEach { stack.addLast(it) }
            } else if (file.extension == "cr") {
                result.add(file)
            }
        }
        return result
    }
}
