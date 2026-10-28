package de.magynhard.crystal.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier

/**
 * The stdlib synthetic library is attached by the platform's
 * [com.intellij.openapi.roots.AdditionalLibraryRootsProvider] query while the
 * project model is still being built, and that same query is what publishes the
 * discovered stdlib path. A single empty result is cached for the whole session:
 * the project then has no stdlib library *and* no stdlib in its require closure,
 * which is what makes the argument-count inspection blame a partial overload
 * pool (crystalline `ext/compiler.cr:156`). These cases pin the post-open
 * re-attachment and the publication the require resolver depends on.
 */
class CrystalStdlibLibraryRegistrationTest : BasePlatformTestCase() {

    private val activity = CrystalStdlibLibraryRegistrationActivity()

    fun testNonCrystalProjectNeedsNoStdlib() {
        myFixture.addFileToProject("nested/only.cr", "class Only\nend\n")

        assertNull(activity.missingLibraryRoots(project, inLibrary = { true }))
    }

    fun testStdlibOutsideLibraryScopeNeedsRegistration() {
        val roots = fakeStdlib()

        val missing = activity.missingLibraryRoots(project, inLibrary = { false })

        assertEquals(roots, missing)
    }

    fun testStdlibAlreadyInLibraryScopeNeedsNoRegistration() {
        fakeStdlib()

        assertNull(activity.missingLibraryRoots(project, inLibrary = { true }))
    }

    /**
     * `ProjectActivity.execute` runs on a background dispatcher without an ambient
     * read action, and `ProjectFileIndex.isInLibrary` reads the workspace-model
     * file index, which asserts read access. The default probe has to run on a
     * background thread to be exercised at all: on the EDT the implicit read
     * access hides a missing read action. The other cases here inject a lambda and
     * therefore pass with an unguarded default probe.
     */
    fun testDefaultLibraryScopeProbeIsReadActionSafeOffTheEventThread() {
        val roots = fakeStdlib()
        val done = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                activity.missingLibraryRoots(project)
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                done.set(true)
            }
        }
        PlatformTestUtil.waitWithEventsDispatching(
            "stdlib library-scope probe off the EDT",
            BooleanSupplier { done.get() },
            60,
        )

        assertNull(
            "the default probe must supply its own read access: ${failure.get()}",
            failure.get(),
        )
        assertFalse("the fake stdlib must be resolvable off the EDT", roots.isEmpty())
    }

    /**
     * The require path resolver is wired to `cachedStdlibPath`, so a stdlib that
     * was never published resolves no stdlib require at all. Publishing the path
     * — what the registration activity does — must restore it, otherwise every
     * diagnostic needing a stdlib overload works on a partial pool.
     */
    fun testStdlibRequireResolvesOnlyAfterPublication() {
        val stdlibDir = myFixture.addFileToProject("fake-stdlib/prelude.cr", "").virtualFile.parent!!
        myFixture.addFileToProject("fake-stdlib/compiler/crystal/compiler.cr", "module Crystal\nend\n")
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { stdlibDir }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        val entry = myFixture.addFileToProject("main.cr", "require \"compiler/crystal/compiler\"\n").virtualFile
        val resolver = CrystalRequirePathResolver(project) { CrystalStdlibResolver.cachedStdlibPath(project) }
        val compilerStdlib = stdlibDir.findChild("compiler")!!.findChild("crystal")!!.findChild("compiler.cr")

        val beforePublish = resolver.resolve(entry, "compiler/crystal/compiler")

        assertTrue(
            "Without a published stdlib path the stdlib require cannot resolve",
            beforePublish.files.isEmpty(),
        )

        CrystalStdlibResolver.resolveStdlibPath(project)

        assertEquals(
            listOf(compilerStdlib),
            resolver.resolve(entry, "compiler/crystal/compiler").files,
        )
    }

    /**
     * A flat stdlib root yields its loose files plus every directory child, so
     * one directory is enough to model `enumerate` output for these checks.
     */
    private fun fakeStdlib(): List<com.intellij.openapi.vfs.VirtualFile> {
        myFixture.addFileToProject("main.cr", "puts 1\n")
        myFixture.addFileToProject("fake-stdlib/prelude.cr", "")
        val stdlibDir = myFixture.addFileToProject("fake-stdlib/compiler/.keep", "").virtualFile.parent!!
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { stdlibDir.parent }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        val roots = activity.stdlibSourceRoots(project)!!
        assertTrue("enumerate() must yield the fixture roots", roots.containsAll(listOf(stdlibDir)))
        return roots
    }
}