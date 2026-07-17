package de.magynhard.crystal

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.psi.CrystalReference

class StdlibReferenceGraphTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        VfsRootAccess.allowRootAccess(testRootDisposable, STDLIB)
        System.setProperty("grammar.kit.gpub.max.level", "6000")
    }

    /**
     * Validates the bounded stdlib text-scan across ALL files: every [SymbolLoc] offset must point
     * at a real identifier character (the definition's name start), NEVER at leading whitespace —
     * the bug that made Go to Definition land on blank space for every indented stdlib definition.
     */
    fun testAllSymbolTableOffsetsResolveToRealElements() {
        val root = LocalFileSystem.getInstance().findFileByPath(STDLIB)!!
        val table = CrystalReference.getStdlibSymbolTable(project)
        var whitespace = 0
        var outOfRange = 0
        val failedSamples = ArrayList<String>()
        val textCache = HashMap<String, String>()
        for ((name, loc) in table) {
            val text = textCache.getOrPut(loc.relPath) {
                val file = VfsUtilCore.findRelativeFile(loc.relPath, root) ?: continue
                String(file.contentsToByteArray(), Charsets.UTF_8)
            }
            if (loc.offset < 0 || loc.offset >= text.length) {
                outOfRange++
                if (failedSamples.size < 30) failedSamples.add("$name@${loc.relPath}:${loc.offset} (oob)")
                continue
            }
            val c = text[loc.offset]
            if (c.isWhitespace()) {
                whitespace++
                if (failedSamples.size < 30) failedSamples.add("$name@${loc.relPath}:${loc.offset} ('$c')")
            }
        }
        println("SYMTAB_OFFSET_CHECK total=${table.size} whitespace=$whitespace outOfRange=$outOfRange")
        failedSamples.forEach { println("SYMTAB_FAIL $it") }
        assertTrue(
            "Symbol-table offsets must point at identifier chars, not whitespace " +
                "(whitespace=$whitespace, outOfRange=$outOfRange)",
            whitespace == 0 && outOfRange == 0
        )
    }

    /**
     * Regression: the bracket-index operator `[]` (and its `[]=` / `[]?` variants) must parse both
     * as a method name (`def [](...)`) and as a subscript expression (`a[i]`, `foo()[i]`, `a[i][j]`).
     */
    fun testSubscriptAndBracketOperatorParses() {
        val snippets = linkedMapOf(
            "def[]" to "def [](x : Int) : Int\n x\nend\n",
            "def[]=" to "def []=(i : Int, v : Int)\n @a[i] = v\nend\n",
            "def[]?" to "def []?(i : Int) : Int?\n nil\nend\n",
            "arr[i]" to "x = arr[i]\n",
            "foo()[i]" to "a = foo()[i]\n",
            "nested[i][j]" to "a = arr[i][j]\n"
        )
        var totalErrors = 0
        for ((name, text) in snippets) {
            val psi = ReadAction.compute<PsiFile, Throwable> {
                PsiFileFactory.getInstance(project)
                    .createFileFromText("$name.cr", CrystalLanguage, text)
            }
            var errs = 0
            psi.accept(object : PsiRecursiveElementVisitor() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    errs++
                    super.visitErrorElement(element)
                }
            })
            totalErrors += errs
        }
        assertTrue("bracket-index operator must parse with zero errors (got $totalErrors)", totalErrors == 0)
    }

    companion object {
        private const val STDLIB = "/opt/homebrew/Cellar/crystal/1.20.3/share/crystal/src"
    }
}
