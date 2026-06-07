package de.magynhard.crystal.run

import org.junit.Assert.*
import org.junit.Test
import java.io.File

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

    // ==================== CrystalSpecFileIndexer Tests ====================

    @Test
    fun testIndexer_simpleSpecFile() {
        val specContent = """
            require "spec"

            describe "Calculator" do
              it "adds numbers" do
                expect(1 + 1).to eq(2)
              end

              it "subtracts numbers" do
                expect(5 - 3).to eq(2)
              end
            end
        """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(2, locations.size)
            assertNotNull(locations["Calculator adds numbers"])
            assertNotNull(locations["Calculator subtracts numbers"])
            assertEquals(tempFile.absolutePath, locations["Calculator adds numbers"]?.file)
            assertEquals(4, locations["Calculator adds numbers"]?.line) // 1-based
            assertEquals(8, locations["Calculator subtracts numbers"]?.line)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_nestedContextBlocks() {
        val specContent = """
            require "spec"

            describe "User" do
              context "when admin" do
                it "can delete" do
                  expect(true).to be_true
                end
              end

              context "when guest" do
                it "cannot delete" do
                  expect(false).to be_false
                end
              end
            end
        """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(2, locations.size)
            assertNotNull(locations["User when admin can delete"])
            assertNotNull(locations["User when guest cannot delete"])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_singleQuotes() {
        val specContent = """
            require 'spec'

            describe 'Math' do
              it 'works' do
                expect(1).to eq(1)
              end
            end
        """.trimIndent()

        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText(specContent)
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(1, locations.size)
            assertNotNull(locations["Math works"])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_emptyFile() {
        val tempFile = File.createTempFile("test_spec", ".cr")
        try {
            tempFile.writeText("")
            val indexer = CrystalSpecFileIndexer(tempFile.absolutePath)
            val locations = indexer.buildIndex()

            assertEquals(0, locations.size)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testIndexer_nonExistentFile() {
        val indexer = CrystalSpecFileIndexer("/nonexistent/path/spec.cr")
        val locations = indexer.buildIndex()

        assertEquals(0, locations.size)
    }
}
