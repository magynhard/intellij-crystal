package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.inspections.CrystalEmptyCollectionInspection

class CrystalEmptyCollectionInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CrystalEmptyCollectionInspection())
    }

    fun testEmptyArrayLiteralReported() {
        myFixture.configureByText("test.cr", "a = []")
        val highlights = myFixture.doHighlighting()
        assertTrue("Empty array should be reported as error",
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
