package de.magynhard.crystal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess

/**
 * Suite-wide VFS root allowance for the real Crystal stdlib distribution.
 *
 * The stdlib library provider enumerates /usr/lib/crystal (including
 * compiler/crystal/macros.cr for the builtin macro-method API). The VFS
 * persistence remembers the compiler/ subtree after its first refresh, so
 * EVERY test's directory scans would trip VfsRootAccess unless the root is
 * allowed for the whole test application (not just per-test disposables).
 */
object CrystalTestVfsRoots {
    @Volatile private var registered = false

    fun ensureStdlibRootAllowed() {
        if (registered) return
        val app = ApplicationManager.getApplication() ?: return
        if (!java.io.File("/usr/lib/crystal/prelude.cr").isFile) return
        VfsRootAccess.allowRootAccess(app, "/usr/lib/crystal")
        registered = true
    }
}
