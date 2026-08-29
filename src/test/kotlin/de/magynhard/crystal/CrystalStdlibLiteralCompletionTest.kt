package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Integration tests against the REAL local Crystal stdlib when installed
 * (skipped gracefully otherwise): DOT completion on integer and float
 * literals must offer the primitive's methods (`5.<caret>` → `to_s`, `times`;
 * `5.5.<caret>` → Float methods). Regression context: grammar gaps in
 * int.cr/float.cr (macro-generated method names, macro-interpolated types)
 * silently removed Int32/Float64 from stub indexing, which emptied literal
 * completion while String completion kept working.
 */
class CrystalStdlibLiteralCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        de.magynhard.crystal.CrystalTestVfsRoots.ensureStdlibRootAllowed()
    }

    private fun stdlibAvailable(): Boolean {
        val src = java.io.File("/usr/lib/crystal")
        return src.isDirectory && java.io.File(src, "prelude.cr").isFile &&
            java.io.File(src, "int.cr").isFile && java.io.File(src, "float.cr").isFile &&
            java.io.File(src, "string.cr").isFile
    }

    private fun setupRealStdlibProject() {
        de.magynhard.crystal.CrystalTestVfsRoots.ensureStdlibRootAllowed()

        val src = java.io.File("/usr/lib/crystal")
        val rootPrefix = "realLiteralStdlib"
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
        addReal("prelude.cr")
        addReal("int.cr"); addReal("float.cr"); addReal("number.cr")
        addReal("comparable.cr"); addReal("string.cr"); addReal("char.cr")
        addReal("enumerable.cr"); addReal("iterable.cr"); addReal("indexable.cr")
        addReal("crystal/hasher.cr")
        addRealDir("string"); addRealDir("char"); addRealDir("indexable")

        de.magynhard.crystal.sdk.CrystalStdlibResolver.installDiscoveryForTests(project, testRootDisposable) {
            myFixture.addFileToProject("$rootPrefix/.keep", "").virtualFile.parent
        }
        de.magynhard.crystal.sdk.CrystalStdlibResolver.installVersionForTests(project, testRootDisposable) { "Crystal 1.21.0" }
        de.magynhard.crystal.sdk.CrystalStdlibResolver.clearCachedStdlibPath(project)
        // Force a synchronous stub-index pass over the added files.
        val rootVFile = de.magynhard.crystal.sdk.CrystalStdlibResolver.resolveStdlibPath(project)
        de.magynhard.crystal.sdk.CrystalStdlibIndexRefresher.refresh(
            project,
            emptyList(),
            de.magynhard.crystal.sdk.CrystalStdlibRoots.enumerate(rootVFile!!),
        )
    }

    private fun completionNames(source: String): Set<String> {
        myFixture.configureByText("main.cr", source)
        return myFixture.complete(CompletionType.BASIC)?.map { it.lookupString }.orEmpty().toSet()
    }

    fun testIntLiteralDotCompletionOffersIntMethods() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()

        val names = completionNames("5.<caret>")

        assertTrue(
            "Int literal DOT completion should offer stdlib methods, got: $names",
            names.contains("to_s"),
        )
    }

    fun testFloatLiteralDotCompletionOffersFloatMethods() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()

        val names = completionNames("5.5.<caret>")

        assertTrue(
            "Float literal DOT completion should offer stdlib methods, got: $names",
            names.contains("round"),
        )
    }

    fun testStringLiteralDotCompletionStillWorks() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()

        val names = completionNames("\"text\".<caret>")

        assertTrue(
            "String literal DOT completion must keep working, got: $names",
            names.contains("upcase"),
        )
    }
}
