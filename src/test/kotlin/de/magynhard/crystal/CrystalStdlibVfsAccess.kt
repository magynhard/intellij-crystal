package de.magynhard.crystal

import com.intellij.openapi.project.Project
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import java.io.File

internal class CrystalStdlibVfsAccess private constructor(
    private val project: Project,
    private val originalAllowedRoots: String?,
) {
    fun restore() {
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        if (originalAllowedRoots == null) {
            System.clearProperty(ALLOWED_ROOTS_PROPERTY)
        } else {
            System.setProperty(ALLOWED_ROOTS_PROPERTY, originalAllowedRoots)
        }
    }

    companion object {
        private const val ALLOWED_ROOTS_PROPERTY = "vfs.additional-allowed-roots"
        private var stdlibRootPath: String? = null

        fun allow(project: Project): CrystalStdlibVfsAccess {
            val original = System.getProperty(ALLOWED_ROOTS_PROPERTY)
            val rootPath = stdlibRootPath ?: CrystalStdlibResolver.resolveStdlibPath(project)?.path?.also {
                stdlibRootPath = it
            }
            val allowedRoots = original.orEmpty()
                .split(File.pathSeparator)
                .filter(String::isNotBlank)
                .toMutableSet()
            rootPath?.let(allowedRoots::add)
            System.setProperty(ALLOWED_ROOTS_PROPERTY, allowedRoots.joinToString(File.pathSeparator))
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            return CrystalStdlibVfsAccess(project, original)
        }
    }
}
