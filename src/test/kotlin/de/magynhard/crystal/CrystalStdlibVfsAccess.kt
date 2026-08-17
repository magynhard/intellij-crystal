package de.magynhard.crystal

import com.intellij.openapi.project.Project
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import java.io.File

internal class CrystalStdlibVfsAccess private constructor(
    private val project: Project,
    private val originalAllowedRoots: String?,
) {
    private var active = true

    fun restore() {
        synchronized(LOCK) {
            if (!active) return
            check(STACK.lastOrNull() === this) { "Crystal stdlib VFS scopes must be restored in LIFO order" }
            STACK.removeLast()
            active = false
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            if (originalAllowedRoots == null) {
                System.clearProperty(ALLOWED_ROOTS_PROPERTY)
            } else {
                System.setProperty(ALLOWED_ROOTS_PROPERTY, originalAllowedRoots)
            }
        }
    }

    companion object {
        private const val ALLOWED_ROOTS_PROPERTY = "vfs.additional-allowed-roots"
        private val LOCK = Any()
        private val STACK = ArrayDeque<CrystalStdlibVfsAccess>()

        fun allow(project: Project): CrystalStdlibVfsAccess {
            synchronized(LOCK) {
                val original = System.getProperty(ALLOWED_ROOTS_PROPERTY)
                val rootPath = CrystalStdlibResolver.resolveStdlibPath(project)?.path
                val allowedRoots = original.orEmpty()
                    .split(File.pathSeparator)
                    .filter(String::isNotBlank)
                    .toMutableSet()
                rootPath?.let(allowedRoots::add)
                System.setProperty(ALLOWED_ROOTS_PROPERTY, allowedRoots.joinToString(File.pathSeparator))
                CrystalStdlibResolver.clearCachedStdlibPath(project)
                return CrystalStdlibVfsAccess(project, original).also(STACK::addLast)
            }
        }
    }
}
