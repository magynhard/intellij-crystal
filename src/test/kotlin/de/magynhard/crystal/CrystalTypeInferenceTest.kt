package de.magynhard.crystal

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.completion.CrystalTypeInference

class CrystalTypeInferenceTest : BasePlatformTestCase() {

    fun testInferIntegerLiteral() {
        myFixture.configureByText("test.cr", "x = 1")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Int32", type)
    }

    fun testInferSuffixedIntegerLiteral() {
        myFixture.configureByText("test.cr", "x = 1_i64")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Int64", type)
    }

    fun testInferFloatLiteral() {
        myFixture.configureByText("test.cr", "x = 1.0")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Float64", type)
    }

    fun testInferSuffixedFloatLiteral() {
        myFixture.configureByText("test.cr", "x = 1_f32")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Float32", type)
    }

    fun testInferStringLiteral() {
        myFixture.configureByText("test.cr", "x = \"hello\"")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("String", type)
    }

    fun testInferCharLiteral() {
        myFixture.configureByText("test.cr", "x = 'a'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferCharLiteralWithEscapeSequences() {
        myFixture.configureByText("test.cr", "x = '\\n'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferCharLiteralWithHexEscape() {
        myFixture.configureByText("test.cr", "x = '\\x41'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferCharLiteralWithBackslashEscape() {
        myFixture.configureByText("test.cr", "x = '\\\\'")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Char", type)
    }

    fun testInferSymbolLiteral() {
        myFixture.configureByText("test.cr", "x = :foo")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Symbol", type)
    }

    fun testInferTrueLiteral() {
        myFixture.configureByText("test.cr", "x = true")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Bool", type)
    }

    fun testInferFalseLiteral() {
        myFixture.configureByText("test.cr", "x = false")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Bool", type)
    }

    fun testInferNilLiteral() {
        myFixture.configureByText("test.cr", "x = nil")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Nil", type)
    }

    fun testInferArrayLiteralWithOfType() {
        myFixture.configureByText("test.cr", "x = [] of Int32")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Array", type)
    }

    fun testInferArrayLiteralHomogeneous() {
        myFixture.configureByText("test.cr", "x = [1, 2, 3]")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Array", type)
    }

    fun testInferInstanceVariableFromAssignment() {
        myFixture.configureByText("test.cr", "@x = \"hello\"")
        val type = CrystalTypeInference.inferType("@x", myFixture.file, project)
        assertEquals("String", type)
    }

    fun testInferInstanceVariableFromIntegerAssignment() {
        myFixture.configureByText("test.cr", "@count = 42")
        val type = CrystalTypeInference.inferType("@count", myFixture.file, project)
        assertEquals("Int32", type)
    }

    fun testInferHashLiteral() {
        myFixture.configureByText("test.cr", "x = {\"a\" => 1}")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Hash", type)
    }

    fun testInferHashLiteralShorthand() {
        myFixture.configureByText("test.cr", "x = {a: 1}")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Hash", type)
    }

    fun testInferTupleLiteral() {
        myFixture.configureByText("test.cr", "x = {1, \"hi\"}")
        val type = CrystalTypeInference.inferType("x", myFixture.file, project)
        assertEquals("Tuple", type)
    }
}
