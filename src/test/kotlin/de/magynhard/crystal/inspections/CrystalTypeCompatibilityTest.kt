package de.magynhard.crystal.inspections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bare unparameterized generics in restrictions (`paragraph : Array`) are
 * `Array(_)` in Crystal and accept every instantiation of the same base,
 * while mismatched known bases stay definite mismatches (compiler-verified
 * against Crystal 1.21: `def f(a : Array); end; f([1, "x"])` compiles,
 * `def g(a : Array(String)); end; g(1)` does not).
 */
class CrystalTypeCompatibilityTest {

    @Test
    fun bareGenericParameterAcceptsAnyInstantiation() {
        assertTrue(CrystalTypeCompatibility.isCompatible("Array(String)", "Array"))
        assertTrue(CrystalTypeCompatibility.isCompatible("Array(Int32)", "Array"))
        assertTrue(CrystalTypeCompatibility.isCompatible("Array(TestHeaderHandler)", "Array"))
    }

    @Test
    fun bareGenericParameterRejectsDifferentBase() {
        assertFalse(CrystalTypeCompatibility.isCompatible("Hash(String, Int32)", "Array"))
    }

    @Test
    fun builtinParameterStaysDefiniteAgainstGenericArgument() {
        assertFalse(CrystalTypeCompatibility.isCompatible("Array(String)", "String"))
        assertFalse(CrystalTypeCompatibility.isCompatible("Array(String)", "Int32"))
    }

    @Test
    fun genericParameterStaysDefiniteAgainstBuiltinArgument() {
        assertFalse(CrystalTypeCompatibility.isCompatible("Int32", "Enumerable(HTTP::Handler)"))
        assertFalse(CrystalTypeCompatibility.isCompatible("String", "Array(String)"))
    }

    @Test
    fun bareGenericArgumentMatchesGenericParameterBase() {
        assertTrue(CrystalTypeCompatibility.isCompatible("Array", "Array(String)"))
        assertFalse(CrystalTypeCompatibility.isCompatible("Hash", "Array(String)"))
    }

    @Test
    fun unknownUserTypesStayLenient() {
        assertTrue(CrystalTypeCompatibility.isCompatible("SomeUserType", "SomeOtherType"))
        assertTrue(CrystalTypeCompatibility.isCompatible("Array(String)", "SomeHandlerModule"))
    }
}
