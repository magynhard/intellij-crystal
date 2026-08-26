package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
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
            java.io.File(src, "http/client.cr").isFile
    }

    private fun setupRealStdlibProject() {
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
        addReal("prelude.cr"); addReal("uri.cr"); addReal("http.cr")
        addRealDir("http"); addRealDir("uri")

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
}
