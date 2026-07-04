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
}
