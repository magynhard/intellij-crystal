package de.magynhard.crystal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import org.junit.Assume

/**
 * Tests for Go to Definition on require statements — navigation to the required
 * file(s). Resolution path matches the IDE: leaf element (REQUIRE keyword or the
 * string literal) → CrystalGotoDeclarationHandler → CrystalRequireResolver →
 * CrystalRequirePathResolver (compiler semantics).
 */
class CrystalRequireGotoTest : BasePlatformTestCase() {

    private fun requireTargets(): Array<out PsiElement>? {
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        return CrystalGotoDeclarationHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
    }

    // ==================== Relative requires ====================

    fun testRelativeRequireNavigatesToFile() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\n")
        myFixture.configureByText("main.cr", "require \"./ap<caret>fel\"\n")

        val targets = requireTargets()

        assertNotNull("Relative require should navigate to apfel.cr", targets)
        assertEquals(1, targets!!.size)
        assertTrue("Target should be a PsiFile, got ${targets[0]}", targets[0] is PsiFile)
        assertEquals("apfel.cr", (targets[0] as PsiFile).name)
    }

    fun testRelativeRequireWithSubdirectoryNavigatesToFile() {
        myFixture.addFileToProject("models/birne.cr", "class Birne\nend\n")
        myFixture.configureByText("main.cr", "require \"./mo<caret>dels/birne\"\n")

        val targets = requireTargets()

        assertNotNull(targets)
        assertEquals(1, targets!!.size)
        assertEquals("birne.cr", (targets[0] as PsiFile).name)
    }

    fun testCaretOnRequireKeywordNavigatesToFile() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\n")
        myFixture.configureByText("main.cr", "re<caret>quire \"./apfel\"\n")

        val targets = requireTargets()

        assertNotNull("Caret on the require keyword should navigate too", targets)
        assertEquals(1, targets!!.size)
        assertEquals("apfel.cr", (targets[0] as PsiFile).name)
    }

    // ==================== Wildcards ====================

    fun testWildcardRequireReturnsAllMatchingFiles() {
        myFixture.addFileToProject("models/a.cr", "class A\nend\n")
        myFixture.addFileToProject("models/z.cr", "class Z\nend\n")
        myFixture.configureByText("main.cr", "require \"./models/<caret>*\"\n")

        val targets = requireTargets()

        assertNotNull(targets)
        assertEquals(2, targets!!.size)
        assertEquals("a.cr", (targets[0] as PsiFile).name)
        assertEquals("z.cr", (targets[1] as PsiFile).name)
    }

    fun testBareDirectoryRequireIsNotExpanded() {
        myFixture.addFileToProject("models/a.cr", "class A\nend\n")
        myFixture.configureByText("main.cr", "require \"./mo<caret>dels\"\n")

        val targets = requireTargets()

        assertNull("A bare directory is not expanded by the compiler", targets)
    }

    // ==================== Unresolvable requires ====================

    fun testMissingRelativeRequireReturnsNull() {
        myFixture.configureByText("main.cr", "require \"./no<caret>pe\"\n")

        assertNull(requireTargets())
    }

    fun testInterpolatedRequireReturnsNull() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\n")
        myFixture.configureByText("main.cr", "def name\n  \"apfel\"\nend\nrequire \"./#{<caret>name}\"\n")

        assertNull("Dynamic (interpolated) requires are unresolvable", requireTargets())
    }

    // ==================== Library requires (project lib / shards) ====================

    fun testShardRequireResolvesViaProjectLib() {
        projectFile("lib/kemal/src/kemal.cr", "module Kemal\nend\n")
        myFixture.configureByText("main.cr", "require \"ke<caret>mal\"\n")

        val targets = requireTargets()

        assertNotNull("Shard require should resolve via <root>/lib", targets)
        assertEquals(1, targets!!.size)
        assertEquals("kemal.cr", (targets[0] as PsiFile).name)
    }

    // ==================== Standard library requires ====================

    fun testStdlibRequireNavigatesIntoStdlibSource() {
        val stdlibRoot = CrystalStdlibResolver.resolveStdlibPath(project)
        Assume.assumeTrue("No Crystal standard library available", stdlibRoot != null)
        // The standard library lives outside the test project — grant VFS access.
        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, stdlibRoot!!.path)
        myFixture.configureByText("main.cr", "require \"js<caret>on\"\n")

        val targets = requireTargets()

        assertNotNull("Stdlib require should resolve when the compiler is installed", targets)
        assertEquals(1, targets!!.size)
        assertEquals("json.cr", (targets[0] as PsiFile).name)
    }

    /**
     * Creates a file inside the physical project root (LocalFileSystem), mirroring
     * the established CrystalRequirePathResolverTest helper — `addFileToProject`
     * files live in the in-memory test file system and are invisible to
     * project-root-based library resolution.
     */
    private fun projectFile(path: String, text: String): VirtualFile {
        var result: VirtualFile? = null
        ApplicationManager.getApplication().runWriteAction {
            val parent = VfsUtil.createDirectories(
                FileUtil.join(requireNotNull(project.basePath), path.substringBeforeLast('/', "")))
            val name = path.substringAfterLast('/')
            result = parent.findChild(name) ?: parent.createChildData(this, name)
            VfsUtil.saveText(requireNotNull(result), text)
        }
        return requireNotNull(result)
    }
}
