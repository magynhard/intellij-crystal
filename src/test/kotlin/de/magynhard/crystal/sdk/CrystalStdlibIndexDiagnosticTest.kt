package de.magynhard.crystal.sdk

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalNamespaceReference
import de.magynhard.crystal.psi.CrystalReference

/**
 * Regression tests for stdlib Go to Definition.
 *
 * Stdlib symbols live under a SyntheticLibrary scope that no GlobalSearchScope can
 * query via StubIndex, and stdlib stub building is intentionally skipped (it would
 * otherwise hog CPU during first project open and starve other plugins' indexing).
 * Resolution is served by a bounded VFS scan cache in [CrystalReference] /
 * [CrystalNamespaceReference], which is what these tests exercise.
 */
class CrystalStdlibIndexDiagnosticTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun setUp() {
        super.setUp()
        // Stdlib navigation tests parse files under the SDK directory, which the
        // test VFS sandbox forbids by default. Allow read access for the duration
        // of the test so constants/classes (DEFAULT_CREATE_PERMISSIONS, Math::PI,
        // File) resolve into the real stdlib.
        de.magynhard.crystal.sdk.CrystalStdlibResolver.resolveStdlibPath(project)?.path?.let {
            com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(testRootDisposable, it)
        }
    }

    private fun setupProject() {
        myFixture.addFileToProject("main.cr", "puts 1")
    }

    fun testStdlibPathResolves() {
        val path = de.magynhard.crystal.sdk.CrystalStdlibResolver.resolveStdlibPath(project)
        assertNotNull("Stdlib path should resolve", path)
    }

    fun testStdlibClassGotoResolves() {
        setupProject()
        myFixture.configureByText("test.cr", "x : String\ny = File.new")
        val allRefs = PsiTreeUtil.collectElements(myFixture.file) { it.reference is CrystalReference }
        for ((name, expectedFile) in listOf("String" to "string.cr", "File" to "file.cr")) {
            val ref = allRefs.firstOrNull { it.text == name }
                ?: throw AssertionError("Expected a CrystalReference named '$name'")
            val resolved = (ref.reference as CrystalReference).resolve()
            assertNotNull("Stdlib '$name' should resolve to a definition", resolved)
            val path = resolved!!.containingFile.virtualFile.path
            assertTrue(
                "Stdlib '$name' should resolve into the Crystal stdlib, not the project",
                path.contains("share/crystal/src")
            )
            assertTrue(
                "Stdlib '$name' should resolve to its canonical file $expectedFile, got: $path",
                path.endsWith("/$expectedFile")
            )
        }
    }

    fun testStdlibConstantResolves() {
        // `DEFAULT_CREATE_PERMISSIONS` is a top-level constant (CrystalConstantAssignment),
        // not a CrystalNamedElement, so the cached stdlib scan must capture it.
        setupProject()
        myFixture.configureByText("test.cr", "x = DEFAULT_CREATE_PERMISSIO<caret>NS")
        val allRefs = PsiTreeUtil.collectElements(myFixture.file) { it.reference is CrystalReference }
        val ref = allRefs.firstOrNull { it.text == "DEFAULT_CREATE_PERMISSIONS" }
            ?: throw AssertionError("Expected a CrystalReference named 'DEFAULT_CREATE_PERMISSIONS'")
        val resolved = (ref.reference as CrystalReference).resolve()
        assertNotNull("DEFAULT_CREATE_PERMISSIONS should resolve to a stdlib definition", resolved)
        val path = resolved!!.containingFile.virtualFile.path
        assertTrue("DEFAULT_CREATE_PERMISSIONS should resolve into the Crystal stdlib", path.contains("share/crystal/src"))
    }

    fun testStdlibNamespacedConstantResolves() {
        // `Math::PI` is a namespaced constant (CrystalNamespaceReference), not a class,
        // so it needs the stdlib fallback in CrystalNamespaceReference.
        setupProject()
        myFixture.configureByText("test.cr", "x = Math::PI")
        val allRefs = PsiTreeUtil.collectElements(myFixture.file) { it.reference is CrystalNamespaceReference }
        assertTrue("Expected a CrystalNamespaceReference for Math::PI", allRefs.isNotEmpty())
        val piRef = allRefs.firstOrNull { it.text.contains("PI") }
            ?: throw AssertionError("Expected the ::PI namespace reference")
        val resolved = (piRef.reference as CrystalNamespaceReference).resolve()
        assertNotNull("Math::PI should resolve to its stdlib definition", resolved)
        val path = resolved!!.containingFile.virtualFile.path
        assertTrue("Math::PI should resolve into the Crystal stdlib", path.contains("share/crystal/src"))
    }
}
