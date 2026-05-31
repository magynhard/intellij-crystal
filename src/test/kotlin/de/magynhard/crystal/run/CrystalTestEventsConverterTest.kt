package de.magynhard.crystal.run

import org.junit.Assert.*
import org.junit.Test

class CrystalTestEventsConverterTest {

    @Test
    fun testIsDuplicatedName_simple() {
        assertTrue(CrystalTestEventsConverter.isDuplicatedName("adds  adds"))
    }

    @Test
    fun testIsDuplicatedName_multiWord() {
        assertTrue(CrystalTestEventsConverter.isDuplicatedName("adds correctly  adds correctly"))
    }

    @Test
    fun testIsDuplicatedName_extraSpaces() {
        assertTrue(CrystalTestEventsConverter.isDuplicatedName("test name here   test name here"))
    }

    @Test
    fun testIsDuplicatedName_suiteName() {
        assertFalse(CrystalTestEventsConverter.isDuplicatedName("Math"))
    }

    @Test
    fun testIsDuplicatedName_suiteWithSpaces() {
        // Suite names like "#kurz" are NOT duplicated
        assertFalse(CrystalTestEventsConverter.isDuplicatedName("#kurz"))
    }

    @Test
    fun testIsDuplicatedName_shortString() {
        assertFalse(CrystalTestEventsConverter.isDuplicatedName("ab"))
    }

    @Test
    fun testIsDuplicatedName_singleWord() {
        assertFalse(CrystalTestEventsConverter.isDuplicatedName("Apfel"))
    }
}
