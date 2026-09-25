package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.inspections.CrystalEmptyCollectionInspection
import de.magynhard.crystal.sdk.AmebaBinary
import de.magynhard.crystal.sdk.CrystalSettings

class CrystalEmptyCollectionInspectionTest : BasePlatformTestCase() {

    private var savedAmebaEnabled = false

    override fun setUp() {
        super.setUp()
        // This class pins the built-in behavior: the Ameba overlap gate must
        // not silence it, regardless of any ameba binary on the test machine.
        val state = CrystalSettings.getInstance(project).state
        savedAmebaEnabled = state.amebaEnabled
        state.amebaEnabled = false
        AmebaBinary.clearCache(project)
        myFixture.enableInspections(CrystalEmptyCollectionInspection())
    }

    override fun tearDown() {
        try {
            CrystalSettings.getInstance(project).state.amebaEnabled = savedAmebaEnabled
            AmebaBinary.clearCache(project)
        } finally {
            super.tearDown()
        }
    }

    fun testEmptyArrayLiteralReported() {
        myFixture.configureByText("test.cr", "a = []")
        val highlights = myFixture.doHighlighting()
        assertTrue("Empty array should be reported as error",
            highlights.any { it.description?.contains("Empty array literal") == true })
    }

    fun testEmptyBracketsCallNotReported() {
        // `Int64[]` is the zero-arity Number `[]` macro call (number_spec.cr:398),
        // not an empty array literal
        myFixture.configureByText("test.cr", "a = Int64[]")
        val highlights = myFixture.doHighlighting()
        assertFalse("Empty tight brackets after a receiver are a `[]` call, not an array literal",
            highlights.any { it.description?.contains("Empty array literal") == true })
    }

    fun testEmptyBracketsVariableCallNotReported() {
        myFixture.configureByText("test.cr", "foo = 1\na = foo[]")
        val highlights = myFixture.doHighlighting()
        assertFalse("`foo[]` is a zero-argument call",
            highlights.any { it.description?.contains("Empty array literal") == true })
    }

    fun testEmptyBracketsWithElementsNotReported() {
        myFixture.configureByText("test.cr", "a = Int64[1, 2, 3]")
        val highlights = myFixture.doHighlighting()
        assertFalse("Typed array with elements is not empty",
            highlights.any { it.description?.contains("Empty array literal") == true })
    }

    fun testEmptyHashLiteralReported() {
        myFixture.configureByText("test.cr", "h = {}")
        val highlights = myFixture.doHighlighting()
        assertTrue("Empty hash should be reported as error",
            highlights.any { it.description?.contains("Empty hash literal") == true })
    }

    fun testArrayWithElementsNotReported() {
        myFixture.configureByText("test.cr", "a = [1, 2, 3]")
        val highlights = myFixture.doHighlighting()
        assertFalse("Non-empty array should not be reported",
            highlights.any { it.description?.contains("Empty array literal") == true })
    }

    fun testHashWithEntriesNotReported() {
        myFixture.configureByText("test.cr", "h = {\"a\" => 1}")
        val highlights = myFixture.doHighlighting()
        assertFalse("Non-empty hash should not be reported",
            highlights.any { it.description?.contains("Empty hash literal") == true })
    }

    fun testArrayWithOfNotReported() {
        myFixture.configureByText("test.cr", "a = [] of String")
        val highlights = myFixture.doHighlighting()
        assertFalse("Array with 'of' should not be reported",
            highlights.any { it.description?.contains("Empty array literal") == true })
    }

    fun testHashWithOfNotReported() {
        myFixture.configureByText("test.cr", "h = {} of String => Int32")
        val highlights = myFixture.doHighlighting()
        assertFalse("Hash with 'of' should not be reported",
            highlights.any { it.description?.contains("Empty hash literal") == true })
    }

    fun testEmptyBraceBlockNotReported() {
        myFixture.configureByText("test.cr", "Thread.new { }")
        val highlights = myFixture.doHighlighting()
        assertFalse("Empty brace block after method call is not a hash literal",
            highlights.any { it.description?.contains("Empty hash literal") == true })
    }

    fun testSpawnEmptyBlockNotReported() {
        myFixture.configureByText("test.cr", "spawn { }")
        val highlights = myFixture.doHighlighting()
        assertFalse("Empty brace block passed to spawn is not a hash literal",
            highlights.any { it.description?.contains("Empty hash literal") == true })
    }

    fun testNestedEmptyBlocksNotReported() {
        myFixture.configureByText(
            "test.cr",
            """
            n = 5
            Benchmark.measure do
                n.times do
                  spawn { }
                end
            end
            """.trimIndent()
        )
        val highlights = myFixture.doHighlighting()
        assertFalse("Nested empty brace blocks are not hash literals",
            highlights.any { it.description?.contains("Empty hash literal") == true })
    }

    fun testMidListEmptyHashStillReported() {
        // Real Crystal also parses `f 1, { }` as a (typeless) empty hash argument
        myFixture.configureByText("test.cr", "f(1, { })")
        val highlights = myFixture.doHighlighting()
        assertTrue("Empty hash in argument position should be reported",
            highlights.any { it.description?.contains("Empty hash literal") == true })
    }
}
