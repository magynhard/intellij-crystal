package de.magynhard.crystal

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalConstructorResolution
import de.magynhard.crystal.analysis.CrystalTypeSetResolver
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
import de.magynhard.crystal.inspections.CrystalArgumentCountInspection
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.CrystalMethodDefinition

/**
 * Integration tests against the REAL local Crystal stdlib when installed
 * (skipped gracefully otherwise). These mirror the reported production issue:
 * `HTTP::Client.new(...)` must resolve to the explicit `def self.new` overloads in
 * http/client.cr even though the same class generates methods through `{% for %}`
 * loops with macro-interpolated names, and the hover popup must render the real
 * constructor signature instead of the "Any (Variable)" fallback.
 */
class CrystalStdlibConstructorResolutionTest : BasePlatformTestCase() {

    private fun stdlibAvailable(): Boolean {
        val src = java.io.File("/usr/lib/crystal")
        return src.isDirectory && java.io.File(src, "prelude.cr").isFile &&
            java.io.File(src, "http/client.cr").isFile && java.io.File(src, "deque.cr").isFile &&
            java.io.File(src, "hash.cr").isFile
    }

    private fun setupRealStdlibProject() {
        VfsRootAccess.allowRootAccess(testRootDisposable, "/usr/lib/crystal/compiler", "/usr/lib/crystal")

        val src = java.io.File("/usr/lib/crystal")
        val rootPrefix = "realStdlib"
        fun addReal(relative: String) {
            val f = java.io.File(src, relative)
            if (f.isFile) myFixture.addFileToProject("$rootPrefix/$relative", f.readText())
        }
        fun addRealDir(relative: String) {
            val d = java.io.File(src, relative)
            if (!d.isDirectory) return
            d.walkTopDown().filter { it.isFile && it.extension == "cr" }.forEach {
                myFixture.addFileToProject("$rootPrefix/$relative/${it.relativeTo(d).path}", it.readText())
            }
        }
        addReal("prelude.cr"); addReal("iterable.cr"); addReal("enumerable.cr"); addReal("indexable.cr")
        addReal("uri.cr"); addReal("http.cr"); addReal("deque.cr"); addReal("hash.cr")
        addReal("crystal/hasher.cr")
        addRealDir("indexable"); addRealDir("http"); addRealDir("uri")

        val rootVFile = requireNotNull(
            myFixture.addFileToProject("$rootPrefix/.keep", "").virtualFile.parent
        )
        de.magynhard.crystal.sdk.CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { rootVFile }
        de.magynhard.crystal.sdk.CrystalStdlibResolver.installVersionForTests(project, testRootDisposable) { "Crystal 1.21.0" }
        de.magynhard.crystal.sdk.CrystalStdlibResolver.clearCachedStdlibPath(project)
        // Force a synchronous stub-index pass over the added files.
        val roots = de.magynhard.crystal.sdk.CrystalStdlibRoots.enumerate(rootVFile)
        de.magynhard.crystal.sdk.CrystalStdlibIndexRefresher.refresh(project, emptyList(), roots)
        // Warm the resolver cache exactly like the production library provider does.
        de.magynhard.crystal.sdk.CrystalStdlibResolver.resolveStdlibPath(project)
    }

    private fun gotoTargets(code: String): Array<out PsiElement>? {
        myFixture.configureByText("main.cr", code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        val ref = element.reference ?: element.parent?.reference
        val resolved = ref?.resolve()
        if (resolved != null) return arrayOf(resolved)
        return CrystalGotoDeclarationHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
    }

    fun testHttpClientNewResolvesToSelfNewOverloads() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        val targets = gotoTargets("require \"http\"\n\nHTTP::Client.n<caret>ew(URI.new(\"http\", \"h\", 80))")
        assertNotNull("HTTP::Client.new should resolve to def self.new overloads", targets)
        assertTrue(targets!!.isNotEmpty())
        assertTrue(targets.all { it is CrystalMethodDefinition })
        assertEquals("new", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testUriNewResolvesToInitialize() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        val targets = gotoTargets("require \"http\"\n\nURI.n<caret>ew(\"http\", \"h\", 80)")
        assertNotNull("URI.new should resolve to URI#initialize", targets)
        assertTrue(targets!!.isNotEmpty())
        assertEquals("initialize", (targets[0] as CrystalMethodDefinition).name)
    }

    fun testHoverShowsConstructorSignatureNotVariableFallback() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        myFixture.configureByText("main.cr", "require \"http\"\n\nHTTP::Client.n<caret>ew(\"u\")")
        val element = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = CrystalDocumentationProvider()
        val docElement = provider.getCustomDocumentationElement(
            myFixture.editor, myFixture.file, element, myFixture.caretOffset
        )
        assertNotNull("Hover should find a documentation element for .new", docElement)
        val doc = provider.generateDoc(docElement!!, element)
        assertNotNull(doc)
        org.junit.Assert.assertFalse(
            "Hover must not fall back to the variable rendering: $doc",
            doc!!.contains("(Variable)")
        )
        org.junit.Assert.assertTrue(
            "Hover should contain the constructor name: $doc",
            doc.contains("new")
        )
    }

    fun testDequeParameterlessNewUsesInitializeBesideExplicitSelfNewOverloads() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
        myFixture.configureByText("main.cr", "require \"deque\"\n\nd = Deque(Int32).new")
        val resolution = CrystalTypeSetResolver.session(myFixture.file)
            .resolveConstructor("Deque(Int32)", myFixture.file)
        assertTrue("Expected Deque constructor methods, got $resolution", resolution is CrystalConstructorResolution.Methods)
        val resolvedNames = (resolution as CrystalConstructorResolution.Methods).methods.map { it.name }
        assertTrue("Expected explicit self.new overloads: $resolvedNames", resolvedNames.contains("new"))
        assertTrue("Expected compiler-forwarded initialize overloads: $resolvedNames", resolvedNames.contains("initialize"))
        myFixture.checkHighlighting()

        val targets = gotoTargets("require \"deque\"\n\nd = Deque(Int32).n<caret>ew")
        assertNotNull("Deque(Int32).new should expose explicit new and initialize overloads", targets)
        val names = targets!!.map { (it as CrystalMethodDefinition).name }
        assertTrue("Expected explicit self.new overloads: $names", names.contains("new"))
        assertTrue("Expected compiler-forwarded initialize overloads: $names", names.contains("initialize"))
    }

    fun testHashDefaultValueNewResolvesToExplicitSelfNew() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)
        myFixture.configureByText("main.cr", "counts = Hash(String, Int32).new(0)")

        val resolution = CrystalTypeSetResolver.session(myFixture.file)
            .resolveConstructor("Hash(String, Int32)", myFixture.file)
        assertTrue("Expected Hash constructor methods, got $resolution", resolution is CrystalConstructorResolution.Methods)
        val methods = (resolution as CrystalConstructorResolution.Methods).methods
        assertTrue(
            "Expected Hash.self.new(default_value : V, ...), got ${methods.map { it.text.take(80) }}",
            methods.any { it.name == "new" && it.parameterList?.text?.contains("default_value : V") == true }
        )
        myFixture.checkHighlighting()

        val targets = gotoTargets("counts = Hash(String, Int32).n<caret>ew(0)")
        assertNotNull("Hash(String, Int32).new should resolve to constructor overloads", targets)
        assertTrue(
            "Expected Hash.self.new(default_value : V, ...), got ${targets!!.map { it.text.take(80) }}",
            targets.any {
                it is CrystalMethodDefinition &&
                    it.name == "new" &&
                    it.parameterList?.text?.contains("default_value : V") == true
            }
        )
    }
}
