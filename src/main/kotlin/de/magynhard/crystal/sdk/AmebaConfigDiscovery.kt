package de.magynhard.crystal.sdk

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Locates the Ameba configuration file (`.ameba.yml`) for a lint run.
 *
 * Resolution: explicit settings override first, otherwise the nearest
 * `.ameba.yml` walking up from the linted file's directory to (and
 * including) the project base directory — the same convention RuboCop and
 * ESLint integrations use. Pure VFS reads, safe during indexing.
 */
object AmebaConfigDiscovery {

    const val CONFIG_FILE_NAME = ".ameba.yml"

    /**
     * Returns the explicit settings override when set, otherwise the nearest
     * `.ameba.yml` above [file]. Null when neither exists.
     */
    fun discover(project: Project, file: VirtualFile?): VirtualFile? {
        val override = CrystalSettings.getInstance(project).state.amebaConfigPath
        if (override.isNotBlank()) {
            return findFile(override)?.takeIf { !it.isDirectory }
        }
        if (file == null) return null
        val basePath = project.basePath ?: return null
        var dir = if (file.isDirectory) file else file.parent
        while (dir != null) {
            dir.findChild(CONFIG_FILE_NAME)?.takeIf { !it.isDirectory }?.let { return it }
            if (dir.path == basePath) break
            dir = dir.parent
        }
        return null
    }

    private fun findFile(path: String): VirtualFile? {
        return try {
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
        } catch (_: Exception) {
            null
        }
    }
}
