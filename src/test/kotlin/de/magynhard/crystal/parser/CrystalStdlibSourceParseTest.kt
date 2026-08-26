package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Parses real Crystal stdlib sources when a local Crystal installation is present.
 * These files exercise syntax far beyond hand-written fixtures (setter definitions,
 * operator methods, macro-heavy declarations) and act as an early-warning canary:
 * a grammar gap that breaks a stdlib file silently degrades stub indexing of that
 * file's members, which then surfaces as false-positive inspections downstream.
 *
 * Skips gracefully when no local Crystal stdlib is found.
 */
class CrystalStdlibSourceParseTest : BasePlatformTestCase() {

    private fun findStdlibFile(name: String): File? =
        listOf("/usr/lib/crystal", "/usr/local/lib/crystal", "/opt/crystal/lib/crystal")
            .map { File(it, name) }
            .firstOrNull { it.isFile }

    private fun assertParsesCleanly(file: File) {
        myFixture.configureByText(file.name, file.readText())
        val errors = PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java)
        if (errors.isNotEmpty()) {
            val sb = StringBuilder("=== Parse errors in ${file.path} (first 5 of ${errors.size}) ===\n")
            for (error in errors.take(5)) {
                sb.appendLine("At offset ${error.textOffset}: ${error.errorDescription}")
                sb.appendLine("  Context: '${error.parent.text.take(120).replace('\n', ' ')}'")
            }
            fail(sb.toString())
        }
    }

    fun testUriCrParsesWithoutErrors() {
        val uri = findStdlibFile("uri.cr") ?: return
        assertParsesCleanly(uri)
    }

    fun testHttpClientCrParsesWithoutErrors() {
        // http/client.cr combines setter-style assignments, keyword args and blocks.
        val client = findStdlibFile("http/client.cr") ?: return
        assertParsesCleanly(client)
    }
}
