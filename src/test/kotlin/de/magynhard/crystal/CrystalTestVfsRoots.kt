package de.magynhard.crystal

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess

/**
 * Suite-wide VFS root allowance for the real Crystal stdlib distribution.
 *
 * The stdlib library provider enumerates /usr/lib/crystal (including
 * compiler/crystal/macros.cr for the builtin macro-method API). The VFS
 * persistence remembers the compiler/ subtree after its first refresh, so
 * EVERY test's directory scans would trip VfsRootAccess unless the root is
 * allowed for the whole test application (not just per-test disposables).
 *
 * The allowance is registered against a VM-lifetime disposable instead of the
 * current Application: the platform recreates (and disposes) the Application
 * per test class, and the disposal runs `disallowRootAccess` — async index
 * activities of a later class then fail because the static `registered` flag
 * hid the re-registration. A never-disposed parent keeps the allowance alive
 * for the whole test VM, making it immune to Application recreation.
 */
object CrystalTestVfsRoots {
    @Volatile private var registered = false

    private val vmLifetime: Disposable = Disposer.newDisposable("CrystalTestVfsRoots")

    fun ensureStdlibRootAllowed() {
        if (registered) return
        if (ApplicationManager.getApplication() == null) return
        if (!java.io.File("/usr/lib/crystal/prelude.cr").isFile) return
        VfsRootAccess.allowRootAccess(vmLifetime, "/usr/lib/crystal")
        registered = true
    }
}
