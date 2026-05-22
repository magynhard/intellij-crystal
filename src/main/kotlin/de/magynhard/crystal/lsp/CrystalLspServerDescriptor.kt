package de.magynhard.crystal.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import java.io.File

internal class CrystalLspServerDescriptor(project: Project) :
    ProjectWideLspServerDescriptor(project, "Crystalline") {

    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "cr"

    override fun createCommandLine(): GeneralCommandLine {
        val crystallinePath = findCrystallinePath()
        return GeneralCommandLine(crystallinePath).apply {
            withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        }
    }

    companion object {
        fun findCrystallinePath(): String {
            // Check common locations
            val candidates = listOf(
                // User-installed via shards or direct
                System.getenv("HOME") + "/.local/bin/crystalline",
                System.getenv("HOME") + "/bin/crystalline",
                // System-wide
                "/usr/local/bin/crystalline",
                "/usr/bin/crystalline",
                // Snap
                "/snap/bin/crystalline",
            )
            for (path in candidates) {
                if (File(path).canExecute()) return path
            }
            // Fall back to PATH lookup
            return "crystalline"
        }

        fun isAvailable(): Boolean {
            return try {
                val path = findCrystallinePath()
                if (path == "crystalline") {
                    // Check if it's on PATH
                    val process = ProcessBuilder("which", "crystalline")
                        .redirectErrorStream(true)
                        .start()
                    process.waitFor() == 0
                } else {
                    File(path).canExecute()
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
