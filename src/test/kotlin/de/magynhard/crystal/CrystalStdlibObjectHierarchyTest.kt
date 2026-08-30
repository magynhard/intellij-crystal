package de.magynhard.crystal

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.inspections.CrystalArgumentCountInspection
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.sdk.CrystalStdlibIndexRefresher
import de.magynhard.crystal.sdk.CrystalStdlibResolver
import de.magynhard.crystal.sdk.CrystalStdlibRoots

/**
 * Integration tests against the REAL local Crystal stdlib (skipped gracefully
 * otherwise): the compiler-imposed base hierarchy (class → Reference → Object,
 * struct → Struct → Value → Object) must make methods written inside a reopened
 * `class Object` reachable from every receiver. Reported false positive:
 * `{error: "User not found"}.to_json` (after `require "json"`) was flagged
 * "Missing required argument(s): 'json'" because only
 * NamedTuple#to_json(json : JSON::Builder) was in the overload pool, while
 * Object#to_json : String — the target the real compiler dispatches a bare
 * call to — was invisible (the hierarchy walk never reached Object).
 */
class CrystalStdlibObjectHierarchyTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        de.magynhard.crystal.CrystalTestVfsRoots.ensureStdlibRootAllowed()
    }

    private fun stdlibAvailable(): Boolean {
        val src = java.io.File("/usr/lib/crystal")
        return src.isDirectory && java.io.File(src, "prelude.cr").isFile &&
            java.io.File(src, "json/to_json.cr").isFile
    }

    private fun setupRealStdlibProject() {
        de.magynhard.crystal.CrystalTestVfsRoots.ensureStdlibRootAllowed()

        val src = java.io.File("/usr/lib/crystal")
        val rootPrefix = "realObjectHierarchyStdlib"
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
        addReal("object.cr"); addReal("struct.cr"); addReal("value.cr")
        addReal("reference.cr"); addReal("named_tuple.cr"); addReal("string.cr")
        addReal("array.cr"); addReal("comparable.cr"); addReal("enumerable.cr")
        addReal("iterable.cr"); addReal("indexable.cr"); addReal("int.cr")
        addReal("number.cr"); addReal("float.cr"); addReal("steppable.cr")
        addReal("enum.cr"); addReal("json.cr")
        addRealDir("json")
        addRealDir("string"); addRealDir("indexable"); addRealDir("enumerable")

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

    private fun highlights(code: String): List<String> {
        myFixture.configureByText("main.cr", code)
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    private fun resolvedDefs(code: String): List<CrystalMethodDefinition> {
        myFixture.configureByText("main.cr", code)
        var current: PsiElement? = myFixture.file.findElementAt(myFixture.caretOffset)
        while (current != null) {
            val ref = current.reference
            if (ref is PsiPolyVariantReference) {
                return ref.multiResolve(false).mapNotNull { it.element as? CrystalMethodDefinition }
            }
            current = current.parent
        }
        return emptyList()
    }

    fun testNamedTupleToJsonCallNotFlaggedMissingArgument() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)

        val descriptions = highlights(
            """
            require "json"

            {error: "User not found"}.to_json
            """.trimIndent()
        )

        assertFalse(
            "Object#to_json (zero args) must absorb the bare call, got: $descriptions",
            descriptions.any { it.contains("Missing required argument") })
    }

    fun testNamedTupleToJsonResolvesToAllThreeOverloads() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()

        val defs = resolvedDefs(
            """
            require "json"

            {error: "User not found"}.to_j<caret>son
            """.trimIndent()
        )

        // NamedTuple#to_json(json : JSON::Builder) + Object#to_json : String +
        // Object#to_json(io : IO) — all from json/to_json.cr.
        assertEquals(3, defs.size)
        assertTrue(
            "Zero-argument Object#to_json must be resolvable",
            defs.any { it.parameterList == null && it.name == "to_json" })
        assertTrue(
            "NamedTuple#to_json(json) must stay resolvable",
            defs.any { it.parameterList != null && it.name == "to_json" })
    }

    fun testStringToJsonCallNotFlaggedMissingArgument() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)

        val descriptions = highlights(
            """
            require "json"

            "payload".to_json
            """.trimIndent()
        )

        assertFalse(
            "String receivers must reach Object#to_json through Reference, got: $descriptions",
            descriptions.any { it.contains("Missing required argument") })
    }

    fun testArrayToJsonCallNotFlaggedMissingArgument() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)

        val descriptions = highlights(
            """
            require "json"

            [1, 2, 3].to_json
            """.trimIndent()
        )

        assertFalse(
            "Array receivers must reach Object#to_json through Reference, got: $descriptions",
            descriptions.any { it.contains("Missing required argument") })
    }

    fun testIntRoundStillResolvesThroughNumberChain() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()

        val defs = resolvedDefs("5.rou<caret>nd")

        assertTrue(
            "Int32 → Int → Number chain must keep Number#round resolvable, got ${defs.size} targets",
            defs.isNotEmpty() && defs.all { it.name == "round" })
    }

    // ==================== User-defined enum receivers ====================

    fun testEnumTypedParameterToJsonCallNotFlaggedMissingArgument() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()
        myFixture.enableInspections(CrystalArgumentCountInspection::class.java)

        val descriptions = highlights(
            """
            require "json"

            enum Status
              ACTIVE
              DELETED
            end

            def render(s : Status)
              s.to_json
            end
            """.trimIndent()
        )

        assertFalse(
            "Enum receivers must reach Object#to_json through Enum → Value, got: $descriptions",
            descriptions.any { it.contains("Missing required argument") })
    }

    fun testEnumToJsonResolvesToEnumAndObjectOverloads() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()

        val defs = resolvedDefs(
            """
            require "json"

            enum Status
              ACTIVE
              DELETED
            end

            def render(s : Status)
              s.to_j<caret>son
            end
            """.trimIndent()
        )

        // Enum#to_json(json : JSON::Builder) from enum.cr's reopened struct Enum
        // (via json/to_json.cr) + Object#to_json : String + Object#to_json(io : IO).
        assertTrue(
            "Enum receiver must resolve to_json overloads, got ${defs.size} targets: ${defs.map { it.containingFile.name }}",
            defs.size >= 2)
        assertTrue(
            "Zero-argument Object#to_json must be resolvable from enum receivers",
            defs.any { it.parameterList == null && it.name == "to_json" })
    }

    fun testEnumReachesEnumBaseMethodsForNavigation() {
        if (!stdlibAvailable()) return
        setupRealStdlibProject()

        val defs = resolvedDefs(
            """
            enum Status
              ACTIVE
            end

            def render(s : Status)
              s.to_<caret>s
            end
            """.trimIndent()
        )

        assertTrue(
            "Enum#to_s must be reachable from enum receivers, got: ${defs.map { it.containingFile.name }}",
            defs.isNotEmpty() && defs.all { it.name == "to_s" })
    }
}
