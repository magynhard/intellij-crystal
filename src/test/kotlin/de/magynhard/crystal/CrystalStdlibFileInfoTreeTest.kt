package de.magynhard.crystal

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.navigation.CrystalGotoDeclarationHandler
import de.magynhard.crystal.psi.CrystalNamedElement
import java.io.File

/**
 * Verifies that the standard-library file `file/info.cr` parses cleanly (its PSI tree
 * is fully built — no parse-error cascade hiding definitions) and that its symbols are
 * reachable from project code via Go to Definition.
 *
 * Regression guard for two bugs that broke stdlib navigation:
 *  - a single unsupported construct cascading into a parse error that hid every
 *    definition after it (see slice.cr / Bytes);
 *  - the bounded stdlib text-scan recording offsets at the whole-match start (leading
 *    whitespace) and keying nested types as `Info::Info` instead of `File::Info`, so
 *    `materialize` landed on whitespace and qualified navigation (`File::Info`) failed.
 */
class CrystalStdlibFileInfoTreeTest : BasePlatformTestCase() {

    private val STDLIB = "/opt/homebrew/Cellar/crystal/1.20.3/share/crystal/src"

    override fun setUp() {
        super.setUp()
        VfsRootAccess.allowRootAccess(testRootDisposable, STDLIB)
        // shard.yml so the AdditionalLibraryRootsProvider loads the stdlib as a library.
        File(project.basePath!!).mkdirs()
        File(project.basePath!!, "shard.yml").writeText("name: test\nversion: 0.1.0\n")
    }

    fun testFileInfoCrTreeParsesCleanly() {
        val vfile = LocalFileSystem.getInstance().findFileByPath("$STDLIB/file/info.cr")!!
        val psi = com.intellij.psi.PsiManager.getInstance(project).findFile(vfile)!!
        val errors = PsiTreeUtil.collectElementsOfType(psi, PsiErrorElement::class.java)
        assertTrue(
            "file/info.cr should parse with no PsiErrorElement (tree fully built), found: " +
                errors.joinToString { it.errorDescription },
            errors.isEmpty()
        )
        val text = psi.text
        assertTrue("enum Type present", text.contains("enum Type"))
        assertTrue("enum Permissions present", text.contains("enum Permissions"))
        assertTrue("struct Info present", text.contains("struct Info"))
    }

    fun testFileInfoStructResolvesFromProjectCode() {
        val target = goto("File::In<caret>fo")?.firstOrNull()
        assertNotNull("File::Info should resolve to struct Info in file/info.cr", target)
        assertTrue("Target should be a named element (not whitespace)", target is CrystalNamedElement)
        assertEquals("Resolved name should be Info", "Info", (target as CrystalNamedElement).name)
        val nav = target.containingFile?.virtualFile?.path ?: ""
        assertTrue("Target should live in file/info.cr (was: $nav)", nav.endsWith("file/info.cr"))
    }

    private fun goto(code: String): Array<out com.intellij.psi.PsiElement>? {
        myFixture.configureByText("test.cr", code)
        val element = myFixture.file.findElementAt(myFixture.caretOffset) ?: return null
        val ref = element.reference ?: element.parent?.reference
        val resolved = ref?.resolve()
        if (resolved != null) return arrayOf(resolved)
        return CrystalGotoDeclarationHandler()
            .getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor)
    }
}
