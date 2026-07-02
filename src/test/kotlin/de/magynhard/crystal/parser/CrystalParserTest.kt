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

    fun testAnnotationUsage() {
        doTest(true)
    }

    fun testPostfixControl() {
        doTest(true)
    }

    fun testTypedDeclaration() {
        doTest(true)
    }

    fun testStringInterpolation() {
        doTest(true)
    }

    fun testMultiLineLiterals() {
        doTest(true)
    }

    fun testAsmAndUninitialized() {
        doTest(true)
    }

    fun testDefaultParam() {
        doTest(true)
    }

    fun testMultiLineParams() {
        doTest(true)
    }

    fun testBareSplat() {
        doTest(true)
    }

    fun testNestedStringInterpolation() {
        doTest(true)
    }

    fun testBlockParam() {
        doTest(true)
    }

    fun testYieldExpr() {
        doTest(true)
    }

    fun testMultiParamBlock() {
        doTest(true)
    }

    fun testAbstractDef() {
        doTest(true)
    }

    fun testMacroBody() {
        doTest(true)
    }

    fun testMultiAssignment() {
        doTest(true)
    }

    fun testNamedTuple() {
        doTest(true)
    }

    fun testOperatorPrecedence() {
        doTest(true)
    }

    fun testPatternMatching() {
        doTest(true)
    }

    fun testSelectStatement() {
        doTest(true)
    }

    fun testTernaryOperator() {
        doTest(true)
    }

    fun testVisibilityModifiers() {
        doTest(true)
    }

    fun testWithYield() {
        doTest(true)
    }

    fun testPointerofOffsetof() {
        doTest(true)
    }

    fun testGenerics() {
        doTest(true)
    }

    fun testMultiLineNamedTupleType() {
        doTest(false)
    }

    fun testWrappingOperators() {
        doTest(true)
    }

    fun testLoop() {
        doTest(true)
    }

    fun testLibExternalVar() {
        doTest(true)
    }

    fun testConditionAssignment() {
        doTest(true)
    }

    fun testLineContinuation() {
        doTest(true)
    }

    fun testTrailingCommas() {
        doTest(true)
    }

    fun testShortBlockSyntax() {
        doTest(true)
    }

    fun testProcLiterals() {
        doTest(true)
    }

    fun testCommandLiterals() {
        doTest(true)
    }

    fun testNamedArgs() {
        doTest(true)
    }

    fun testEmptyCollectionsOf() {
        doTest(true)
    }

    fun testRecordMacro() {
        doTest(true)
    }

    fun testNilSafeIndex() {
        doTest(true)
    }

    fun testMacroInterpolationWithSymbol() {
        doTest(true)
    }

    fun testLibTypeAlias() {
        doTest(true)
    }

    fun testTopLevelMacroControl() {
        doTest(true)
    }

    fun testMacroForLoop() {
        doTest(true)
    }

    fun testKeywordAsMethodName() {
        doTest(true)
    }

    fun testSampleModule() {
        doTest(true)
    }

    fun testCaseWithTapBlock() {
        doTest(true)
    }

    fun testLibFunPointerParams() {
        doTest(true)
    }

    fun testNamespaceAccess() {
        doTest(true)
    }

    fun testQuestionPostfix() {
        doTest(true)
    }
}
