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

    fun testRequireInNestedContexts() {
        doTest(true)
    }

    fun testMacroControlledArguments() {
        doTest(true)
    }

    fun testNamedTypeBareArguments() {
        doTest(true)
    }

    fun testPointerTypeArguments() {
        doTest(true)
    }

    fun testKeywordNamedArguments() {
        doTest(true)
    }

    fun testOutArguments() {
        doTest(true)
    }

    fun testExternalStorageParameters() {
        doTest(true)
    }

    fun testMultiValueAbruptStatements() {
        doTest(true)
    }

    fun testBangTildeOperator() {
        doTest(true)
    }

    fun testIncompleteBinaryOperatorRecovery() {
        doTest(true)
    }

    fun testPostfixIndexedAssignments() {
        doTest(true)
    }

    fun testDotCompoundAssignment() {
        doTest(true)
    }

    fun testControlFlowAssignmentValues() {
        doTest(true)
    }

    fun testKemalRangeBlock() {
        doTest(true)
    }

    fun testDotRegexDivision() {
        doTest(true)
    }

    fun testTernaryAssignments() {
        doTest(true)
    }

    fun testEnumerableStdlibPatterns() {
        doTest(true)
    }

    fun testGlobalScopeCalls() {
        doTest(true)
    }

    fun testPercentLiteralInStringInterpolation() {
        doTest(true)
    }

    fun testMacroGeneratedOperators() {
        doTest(true)
    }

    fun testEndlessRangeNewlineBoundary() {
        doTest(true)
    }

    fun testWrapOperatorBinaryBoundary() {
        doTest(true)
    }

    fun testAmpersandBlockPassBoundary() {
        doTest(true)
    }

    fun testHeredocInterleavedBodies() {
        doTest(true)
    }

    fun testBraceBlockBareArguments() {
        doTest(true)
    }

    fun testOperatorSymbols() {
        doTest(true)
    }

    fun testMacroStringInterpolation() {
        doTest(true)
    }

    fun testEmptyRegexLiteral() {
        doTest(true)
    }

    fun testPercentLiterals() {
        doTest(true)
    }

    fun testStringStdlibPatterns() {
        doTest(true)
    }

    fun testRescueMultilineUnion() {
        doTest(true)
    }

    fun testPrivateModuleInClass() {
        doTest(true)
    }

    fun testPrivateLibDefinition() {
        doTest(true)
    }

    fun testDescribeBlock() {
        doTest(true)
    }

    fun testPercentLiteralRawBackslash() {
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

    fun testLooseGroupedPostfixArguments() {
        doTest(true)
    }

    fun testFloatSuffixWithoutUnderscore() {
        doTest(true)
    }

    fun testEnumClassVarAndVisibility() {
        doTest(true)
    }

    fun testParameterlessDoProcLiteral() {
        doTest(true)
    }

    fun testKeywordMemberAssignment() {
        doTest(true)
    }

    fun testTypeDeclarationArrayElements() {
        doTest(true)
    }

    fun testMacroSplicedMethodCalls() {
        doTest(true)
    }

    fun testPointerofLibExternalVar() {
        doTest(true)
    }

    fun testAssignmentCallArguments() {
        doTest(true)
    }

    fun testProcShorthandDoBlock() {
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

    fun testLoopExpressions() {
        doTest(true)
    }

    fun testRecordBlocks() {
        doTest(true)
    }

    fun testWhenRangeEntries() {
        doTest(true)
    }

    fun testLooseArrayArguments() {
        doTest(true)
    }

    fun testIndexedArgumentThenSecondArgument() {
        doTest(true)
    }

    fun testEscapedMacroStatements() {
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

    fun testMultiLineParamsDotChain() {
        doTest(true)
    }

    fun testMultilineParamsAnonBlock() {
        doTest(true)
    }

    fun testBareSplat() {
        doTest(true)
    }

    fun testNestedStringInterpolation() {
        doTest(true)
    }

    fun testGlobalVarInterpolation() {
        doTest(true)
    }

    fun testPostfixModifierAssignment() {
        doTest(true)
    }

    fun testCharLiteralInterpolation() {
        doTest(true)
    }

    fun testSetterMethodDefinition() {
        doTest(true)
    }

    fun testQualifiedReceiverMethodDefinitions() {
        doTest(true)
    }

    fun testMacroInterpolationLiterals() {
        doTest(true)
    }

    fun testBarePredicateCallees() {
        doTest(true)
    }

    fun testMixedWhenEntries() {
        doTest(true)
    }

    fun testTupleAssignmentEntries() {
        doTest(true)
    }

    fun testGroupedSemicolonExpressions() {
        doTest(true)
    }

    fun testOutVariable() {
        doTest(true)
    }

    fun testWhenIdentifier() {
        doTest(true)
    }

    fun testChainedPostfixModifiers() {
        doTest(true)
    }

    fun testBareSplatCallArguments() {
        doTest(true)
    }

    fun testMacroMultiAssignTarget() {
        doTest(true)
    }

    fun testMacroInterpolatedCallee() {
        doTest(true)
    }

    fun testNilableParenthesizedType() {
        doTest(true)
    }

    fun testBlockParam() {
        doTest(true)
    }

    fun testYieldExpr() {
        doTest(true)
    }

    fun testYieldPostfixModifier() {
        doTest(true)
    }

    fun testKeywordExternalParameter() {
        doTest(true)
    }

    fun testSetterSymbolArgument() {
        doTest(true)
    }

    fun testMultiAssignMemberTargets() {
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

    fun testPrimitiveStdlibParsePatterns() {
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

    fun testTypedCollectionsAndWithYield() {
        doTest(true)
    }

    fun testPointerofOffsetof() {
        doTest(true)
    }

    fun testGenerics() {
        doTest(true)
    }

    fun testMultiLineNamedTupleType() {
        doTest(true)
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

    fun testMacroControlledProcParameters() {
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

    fun testEmptyCallBrackets() {
        doTest(true)
    }

    fun testEmptyBlocks() {
        doTest(true)
    }






    fun testMultiHeredocBodies() {
        doTest(true)
    }

    fun testSpecModulesWithHeredocs() {
        doTest(true)
    }

    fun testHeredocClosingParenCalls() {
        doTest(true)
    }

    fun testGroupedAssignmentParens() {
        doTest(true)
    }

    fun testRecordMacro() {
        doTest(true)
    }

    fun testNilSafeIndex() {
        doTest(true)
    }

    fun testConstantHeredocAssignments() {
        doTest(true)
    }

    fun testHeredocModifierBodies() {
        doTest(true)
    }

    fun testIndexedAccessTernary() {
        doTest(true)
    }

    fun testMacroInterpolationWithSymbol() {
        doTest(true)
    }

    fun testMacroGeneratedSymbols() {
        doTest(true)
    }

    fun testKeywordAssignedKeywords() {
        doTest(true)
    }

    fun testKeywordParameterNames() {
        doTest(true)
    }

    fun testTypeofMultipleArguments() {
        doTest(true)
    }

    fun testUninitializedKeywordReference() {
        doTest(true)
    }

    fun testLibTypeAlias() {
        doTest(true)
    }

    fun testTopLevelMacroControl() {
        doTest(true)
    }

    fun testMacroConditionalMethodDefinition() {
        doTest(true)
    }

    fun testMacroForLoop() {
        doTest(true)
    }

    fun testMacroControlFloats() {
        doTest(true)
    }

    fun testMacroGeneratedTypeNames() {
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

    fun testLibUntypedFunParameters() {
        doTest(true)
    }

    fun testLibFunExternalAliases() {
        doTest(true)
    }

    fun testLibAggregateFields() {
        doTest(true)
    }

    fun testLibFunKeywordParameters() {
        doTest(true)
    }

    fun testSuperBlock() {
        doTest(true)
    }

    fun testAnnotationSemicolon() {
        doTest(true)
    }

    fun testDoubleSplatRestriction() {
        doTest(true)
    }

    fun testMultilineTernary() {
        doTest(true)
    }

    fun testKeywordSetter() {
        doTest(true)
    }

    fun testLibFunUppercaseNames() {
        doTest(true)
    }

    fun testFunKeywordNames() {
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

    fun testLibDefinitionWithStub() {
        doTest(true)
    }

    fun testLibConstants() {
        doTest(true)
    }

    fun testLibAnnotations() {
        doTest(true)
    }

    fun testLibMacroForms() {
        doTest(true)
    }

    fun testMacroSplicedNames() {
        doTest(true)
    }

    fun testMacroSplicedLiterals() {
        doTest(true)
    }

    fun testMacroInterpolationBareCall() {
        doTest(true)
    }

    fun testBacktickInInterpolation() {
        doTest(true)
    }

    fun testMacroExpressions() {
        doTest(true)
    }

    fun testMacroIfEnvelope() {
        doTest(true)
    }

    fun testLeadingNamespaceBareArgument() {
        doTest(true)
    }

    fun testStringNamedArguments() {
        doTest(true)
    }

    fun testNestedLibDefinition() {
        doTest(true)
    }

    fun testPointerofQualifiedInstanceVar() {
        doTest(true)
    }

    fun testProcPointerVariableReceivers() {
        doTest(true)
    }

    fun testMacroCaseClauses() {
        doTest(true)
    }

    fun testMacroSplatParameters() {
        doTest(true)
    }

    fun testChainedIndexedAssignments() {
        doTest(true)
    }

    fun testMacroFreshVariables() {
        doTest(true)
    }

    fun testDoubleBangComparisons() {
        doTest(true)
    }

    fun testBangSuffixChains() {
        doTest(true)
    }

    fun testAnnotationDefinitionWithStub() {
        doTest(true)
    }

    fun testAliasDefinitionWithStub() {
        doTest(true)
    }

    fun testExpressionAndRangeReplay() {
        doTest(true)
    }

    fun testMacroGeneratedIvarAccess() {
        doTest(true)
    }

    fun testMacroGeneratedMultiAssign() {
        doTest(true)
    }

    fun testMacroGeneratedLabels() {
        doTest(true)
    }
}
