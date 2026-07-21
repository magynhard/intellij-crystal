package de.magynhard.crystal.parser

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.time.measureTime

class CrystalParserPerformanceTest : BasePlatformTestCase() {
    fun testNestedSpecDslParsesWithoutReplay() {
        val source = buildString {
            repeat(9) { depth ->
                appendLine("context \"level $depth\" do")
            }
            appendLine("it \"parses configured input\" do")
            appendLine("configure(parser: Parser.new) do")
            appendLine("parse(source).result.should eq(expected)")
            appendLine("parse(source).errors.should be_empty")
            appendLine("end")
            appendLine("end")
            repeat(9) {
                appendLine("end")
            }
        }

        val elapsed = measureTime {
            val file = myFixture.configureByText("NestedSpecDslPerformance.cr", source)
            val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
            assertEmpty("Synthetic Spec DSL fixture should parse without errors", errors.toList())
        }

        assertTrue("Nested Spec DSL parsing took $elapsed", elapsed.inWholeSeconds < 10)
    }
}
