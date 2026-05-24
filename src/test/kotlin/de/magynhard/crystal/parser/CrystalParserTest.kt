package de.magynhard.crystal.parser

import com.intellij.testFramework.ParsingTestCase
import de.magynhard.crystal.CrystalParserDefinition

class CrystalParserTest : ParsingTestCase("", "cr", CrystalParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData/parser"

    override fun skipSpaces(): Boolean = true

    override fun includeRanges(): Boolean = true

    fun testRequireStatement() {
        doTest(true)
    }

    fun testDescribeBlock() {
        doTest(true)
    }

    fun testClassDefinition() {
        doTest(true)
    }

    fun testMethodCalls() {
        doTest(true)
    }

    fun testBareMethodCalls() {
        doTest(true)
    }

    fun testSpecFile() {
        doTest(true)
    }

    fun testAssignments() {
        doTest(true)
    }

    fun testControlFlow() {
        doTest(true)
    }

    fun testSimpleBareCall() {
        doTest(true)
    }

    fun testTwoStatements() {
        doTest(true)
    }
}
