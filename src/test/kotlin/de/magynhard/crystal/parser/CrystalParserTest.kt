package de.magynhard.crystal.parser

import com.intellij.testFramework.ParsingTestCase
import de.magynhard.crystal.CrystalParserDefinition
import java.io.File

class CrystalParserTest : ParsingTestCase("", "cr", CrystalParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData/parser"

    // Pin the recursion limit so golden-file comparisons are deterministic and not
    // affected by other tests mutating grammar.kit.gpub.max.level at runtime.
    override fun setUp() {
        super.setUp()
        System.setProperty("grammar.kit.gpub.max.level", "6000")
    }

    override fun skipSpaces(): Boolean = true

    override fun includeRanges(): Boolean = true    fun testShorthandBlockTypeCast() {
        doTest(true)
    }

    fun testConditionalAssignment() {
        doTest(true)
    }

    fun testMacroSetter() {
        doTest(true)
    }

    fun testVisibilityRecordEnum() {
        doTest(true)
    }

    fun testSelfStarType() {
        doTest(true)
    }

    fun testRecordWithDoBlock() {
        doTest(true)
    }

    fun testMultiValueReturnAndYield() {
        doTest(true)
    }

    fun testPropertyGetterKeywordNames() {
        doTest(true)
    }

    fun testImplicitObjectCallBlock() {
        doTest(true)
    }

    fun testTypeReceiverAndFunAlias() {
        doTest(true)
    }

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

    fun testCaseInPattern() {
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

    fun testLibConstant() {
        doTest(true)
    }

    fun testMacroControlExpression() {
        doTest(true)
    }

    fun testDoubleColonCall() {
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

    fun testRescueTypes() {
        doTest(true)
    }

    fun testImplicitObjectCallBracket() {
        doTest(true)
    }

    fun testPercentLiteralInterpolation() {
        doTest(true)
    }

    fun testRegexInterpolation() {
        doTest(true)
    }

    fun testCommandInterpolation() {
        doTest(true)
    }

    /**
     * Regression test for the infinite-recursion / IDE-freeze bug: the grammar previously
     * had a mutual recursion cycle between `expression` and `bare_expression`
     * (expression -> method_call_expression -> bare_argument_list -> bare_argument ->
     *  bare_expression -> bare_method_call_expression -> call_args -> argument -> expression).
     * Parsing a deeply nested construct through that cycle made the generated parser recurse
     * forever (or StackOverflow), freezing the IDE on file open. The watchdog fails the test
     * if parsing does not terminate within the timeout.
     */
    fun testNoInfiniteRecursionInNestedCalls() {
        // Regression test for the IDE-freeze bug: deeply nested call/block structures must
        // terminate (grammar-kit recursion bound). Use YAML serialization as a real-world
        // deeply-nested stdlib file.
        val text = File("src/test/testData/parser/YamlSerialization.cr").readText()
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        var error: Throwable? = null
        val thread = Thread({
            try {
                parseFile("YamlSerialization.cr", text)
                done.set(true)
            } catch (t: Throwable) {
                error = t
                done.set(true)
            }
        }, "crystal-parser-recursion-watchdog")
        thread.isDaemon = true
        thread.start()
        thread.join(20000)
        if (!done.get()) {
            fail("Parser did not terminate (infinite recursion suspected) for YamlSerialization.cr")
        }
        assertNull("Parsing threw an exception: ${error?.message}", error)
    }

    fun testDebugRecordParse() {
        val tree = parseFile("test.cr", "record Config, host : String, port : Int32 = 80\n")
        val errors = mutableListOf<String>()
        tree.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitErrorElement(element: com.intellij.psi.PsiErrorElement) {
                errors.add("ERROR at '${element.text}': ${element.errorDescription}")
                super.visitErrorElement(element)
            }
        })
        println("=== RECORD TREE ===")
        printTree(tree, "  ")
        println("=== ERRORS: ${errors.size} ===")
        errors.forEach { println(it) }
        assertTrue("Record parse produced errors: $errors", errors.isEmpty())
    }

    private fun printTree(node: com.intellij.psi.PsiElement, indent: String) {
        if (node.firstChild == null) {
            println("$indent${node.node.elementType} '${node.text}'")
            return
        }
        println("$indent${node.javaClass.simpleName} '${node.text.take(40)}'")
        node.children.forEach { printTree(it, "$indent  ") }
    }

    fun testOutArg() {
        doTest(true)
    }

    fun testDefPercent() {
        doTest(true)
    }

    fun testInterpFunAlias() {
        doTest(true)
    }

    fun testParamUnion() {
        doTest(true)
    }

    fun testAliasUnion2() {
        doTest(true)
    }

    fun testAliasUnion() {
        doTest(true)
    }

    fun testMacroInterpInSig() {
        doTest(true)
    }

    fun testDoBlock() {
        doTest(true)
    }

    fun testNestedMacroControl() {
        doTest(true)
    }

    fun testYamlSerialization() {
        doTest(true)
    }

}
