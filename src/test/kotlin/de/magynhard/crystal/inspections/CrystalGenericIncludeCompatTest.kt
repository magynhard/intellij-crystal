package de.magynhard.crystal.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Generic include-edge instantiation in argument type checking: an
 * `Array(X)` argument reaches an `Enumerable(Y)` parameter through the
 * transitive include chain (`Array(T)` → `Indexable::Mutable(T)` →
 * `Indexable(T)` → `Enumerable(T)`) with the include's type parameter
 * substituted by the includer's own — the kemal
 * `use "/multi", [TestHeaderHandler.new(...), ...]` shape.
 */
class CrystalGenericIncludeCompatTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalTypeCheckInspection::class.java)
    }

    fun testArrayArgumentThroughGenericIncludeChain() {
        myFixture.configureByText("handler.cr", "class HTTP::Handler\nend\n")
        myFixture.configureByText("dsl.cr", """
            def use(path : String, handlers : Enumerable(HTTP::Handler))
            end

            def use(path : String, handler : HTTP::Handler)
            end
        """.trimIndent())
        myFixture.configureByText("spec.cr", """
            class TestHeaderHandler < HTTP::Handler
            end

            use "/multi", [TestHeaderHandler.new("X-A", "a"), TestHeaderHandler.new("X-B", "b")]
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "An Array argument reaches Enumerable through the include chain",
            highlights.any { it.description?.contains("Type mismatch") == true },
        )
    }

    /**
     * stdlib array.cr:1265 shape: the splat restriction `*arrays : Array` is
     * `Array(_)` inside the body, and bare `Array(_)` reaches
     * `Indexable(Indexable)` through the include chain because the include's
     * type parameter is substituted by the wildcard. Single-file fixture:
     * dot-call receiver resolution is require-effective, so the declarations
     * and the call must share the source.
     */
    fun testBareGenericArrayArgumentReachesIndexableIndexable() {
        myFixture.configureByText("product.cr", """
            abstract struct Indexable(T)
              def self.cartesian_product(indexables : Indexable(Indexable))
                indexables
              end
            end

            struct Array(T)
              include Indexable(T)
            end

            def product(*arrays : Array)
              Indexable.cartesian_product(arrays)
            end
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "A bare Array reference is Array(_) and reaches Indexable(Indexable) through the include chain",
            highlights.any { it.description?.contains("Type mismatch") == true },
        )
    }

    fun testStringArgumentAgainstIndexableIndexableStaysReported() {
        myFixture.configureByText("user.cr", """
            abstract struct Indexable(T)
              def self.cartesian_product(indexables : Indexable(Indexable))
                indexables
              end
            end

            def wrap
              value = "not indexable"
              Indexable.cartesian_product(value)
            end
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        assertTrue(
            "A String argument never reaches Indexable(Indexable)",
            highlights.any { it.description?.contains("Type mismatch") == true },
        )
    }

    /** Bare generic parameter (`x : Indexable` is `Indexable(_)`). */
    fun testBareGenericParameterAcceptsInstantiatedArray() {
        myFixture.configureByText("collection.cr", """
            abstract struct Indexable(T)
            end

            struct Array(T)
              include Indexable(T)
            end

            module Collection
              def self.take(item : Indexable)
                item
              end
            end

            Collection.take([1, 2])
        """.trimIndent())
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "An Array(Int32) argument reaches the bare Indexable restriction",
            highlights.any { it.description?.contains("Type mismatch") == true },
        )
    }

    fun testDirectEnumerableParameterStaysReportedForWrongLeafType() {
        myFixture.configureByText("handler.cr", "class HTTP::Handler\nend\n")
        myFixture.configureByText("dsl.cr", """
            def use(path : String, handlers : Enumerable(Int32))
            end
        """.trimIndent())
        myFixture.configureByText("spec.cr", """
            class TestHeaderHandler < HTTP::Handler
            end

            use "/multi", [TestHeaderHandler.new("X-A", "a")]
        """.trimIndent())
        // The leaf comparison stays lenient for user types (crystal subclasses
        // are modeled by the hierarchy, not here) — this fixture only pins that
        // the include-edge helper never reports; the argument-count side is the
        // other inspection's domain.
        val highlights = myFixture.doHighlighting()
        assertFalse(
            "The include-edge helper only turns mismatches into acceptances",
            highlights.any { it.description?.contains("Type mismatch") == true },
        )
    }
}
