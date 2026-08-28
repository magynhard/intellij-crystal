package de.magynhard.crystal

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.sdk.CrystalStdlibIndexRefresher
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import de.magynhard.crystal.sdk.CrystalStdlibRoots

/**
 * v13: inside {{ … }} macro interpolations and macro bodies, unqualified call
 * names must resolve to macros / builtin Crystal::Macros macro-methods —
 * never to ordinary project methods that merely share the name
 * (reported: {{ run("…") }} resolved to Catalyst::CLI.run(Array(String))).
 */
class CrystalMacroContextResolutionTest : BasePlatformTestCase() {

    private fun stdlibAvailable(): Boolean {
        val src = java.io.File("/usr/lib/crystal")
        return src.isDirectory && java.io.File(src, "compiler/crystal/macros.cr").isFile
    }

    private fun setupRealStdlibProject() {
        VfsRootAccess.allowRootAccess(testRootDisposable, "/usr/lib/crystal/compiler", "/usr/lib/crystal")

        val src = java.io.File("/usr/lib/crystal")
        val rootPrefix = "realStdlib"
        val f = java.io.File(src, "compiler/crystal/macros.cr")
        myFixture.addFileToProject("$rootPrefix/compiler/crystal/macros.cr", f.readText())
        val rootVFile = requireNotNull(
            myFixture.addFileToProject("$rootPrefix/.keep", "").virtualFile.parent
        )
        CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) { rootVFile }
        CrystalStdlibResolver.installVersionForTests(project, testRootDisposable) { "Crystal 1.21.0" }
        CrystalStdlibResolver.clearCachedStdlibPath(project)
        val roots = CrystalStdlibRoots.enumerate(rootVFile)
        CrystalStdlibIndexRefresher.refresh(project, emptyList(), roots)
        CrystalStdlibResolver.resolveStdlibPath(project)
    }

    private fun resolveAt(code: String): PsiElement? {
        myFixture.configureByText("main.cr", code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        val ref = element.reference ?: element.parent?.reference
        return ref?.resolve()
    }

    fun testMacroInterpolationRunResolvesToBuiltinMacroMethod() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        val code = """
            module Catalyst
              class CLI
                def self.run(args : Array(String)) : Int32
                  0
                end
              end
            end

            {{ r<caret>un("../scripts/generate_rules_require.cr") }}
        """.trimIndent()
        val resolved = resolveAt(code)
        assertNotNull("{{ run }} must resolve inside macro context", resolved)
        assertTrue(resolved is CrystalMethodDefinition)
        assertEquals("run", (resolved as CrystalMethodDefinition).name)
        // getEnclosingClassName reports the LAST name segment
        assertTrue(
            "Owner must be the builtin macro-method module",
            CrystalCompletionHelper.getEnclosingClassName(resolved) in setOf("Crystal::Macros", "Macros"))
    }

    fun testMacroBodySystemResolvesToBuiltinMacroMethod() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        val code = """
            macro my_macro
              {{ s<caret>ystem("echo hi") }}
            end
        """.trimIndent()
        val resolved = resolveAt(code)
        assertNotNull("macro body {{ system }} must resolve", resolved)
        assertEquals("system", (resolved as CrystalMethodDefinition).name)
        assertTrue(
            "Owner must be the builtin macro-method module",
            CrystalCompletionHelper.getEnclosingClassName(resolved) in setOf("Crystal::Macros", "Macros"))
    }
}
