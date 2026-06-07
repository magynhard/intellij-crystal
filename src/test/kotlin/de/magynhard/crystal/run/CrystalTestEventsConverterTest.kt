package de.magynhard.crystal.run

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

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

    @Test
    fun testIndexer_directoryWithMultipleFiles() {
        val tempDir = Files.createTempDirectory("spec_dir").toFile()
        try {
            val spec1 = File(tempDir, "math_spec.cr")
            spec1.writeText("""
                describe "Math" do
                  it "adds" do
                    expect(1 + 1).to eq(2)
                  end
                end
            """.trimIndent())

            val spec2 = File(tempDir, "string_spec.cr")
            spec2.writeText("""
                describe "String" do
                  it "concatenates" do
                    expect("hello" + " world").to eq("hello world")
                  end
                end
            """.trimIndent())

            // Also create a non-spec file that should be ignored
            val helper = File(tempDir, "helper.cr")
            helper.writeText("require \"spec\"")

            CrystalSpecFileIndexer.clearCache()
            val locations = CrystalSpecFileIndexer.getTestLocationsForDirectory(tempDir.absolutePath)

            assertEquals(2, locations.size)
            assertNotNull(locations["Math adds"])
            assertNotNull(locations["String concatenates"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testIndexer_directoryWithSubdirectories() {
        val tempDir = Files.createTempDirectory("spec_dir").toFile()
        val subDir = File(tempDir, "models")
        subDir.mkdirs()
        try {
            val spec1 = File(tempDir, "app_spec.cr")
            spec1.writeText("""
                describe "App" do
                  it "works" do
                    expect(true).to be_true
                  end
                end
            """.trimIndent())

            val spec2 = File(subDir, "user_spec.cr")
            spec2.writeText("""
                describe "User" do
                  it "validates" do
                    expect(true).to be_true
                  end
                end
            """.trimIndent())

            CrystalSpecFileIndexer.clearCache()
            val locations = CrystalSpecFileIndexer.getTestLocationsForDirectory(tempDir.absolutePath)

            assertEquals(2, locations.size)
            assertNotNull(locations["App works"])
            assertNotNull(locations["User validates"])
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ==================== Tree Parsing Tests (Two-Pass Architecture) ====================

    @Test
    fun testParse_simpleSuite() {
        val output = """
            |Calculator
            |  adds  adds
            |  subtracts  subtracts
            |Finished in 0.01s
            |2 examples, 0 failures
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        assertEquals(1, tree.size)
        val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("Calculator", suite.name)
        assertEquals(2, suite.children.size)
        val test1 = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("adds", test1.name)
        assertEquals("Calculator adds", test1.fullName)
        assertFalse(test1.failed)
    }

    @Test
    fun testParse_nestedSuites() {
        val output = """
            |User
            |  when admin
            |    can delete  can delete
            |  when guest
            |    cannot delete  cannot delete
            |Finished in 0.01s
            |2 examples, 0 failures
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        assertEquals(1, tree.size)
        val userSuite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("User", userSuite.name)
        assertEquals(2, userSuite.children.size)

        val adminSuite = userSuite.children[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("when admin", adminSuite.name)
        assertEquals(1, adminSuite.children.size)
        assertEquals("can delete", (adminSuite.children[0] as CrystalTestEventsConverter.TestNode.Test).name)

        val guestSuite = userSuite.children[1] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals("when guest", guestSuite.name)
        assertEquals(1, guestSuite.children.size)
    }

    @Test
    fun testParse_multipleTopLevelSuites() {
        val output = """
            |Math
            |  adds  adds
            |String
            |  concat  concat
            |Finished in 0.01s
            |2 examples, 0 failures
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        assertEquals(2, tree.size)
        assertEquals("Math", tree[0].name)
        assertEquals("String", tree[1].name)

        val mathTests = (tree[0] as CrystalTestEventsConverter.TestNode.Suite).children
        assertEquals(1, mathTests.size)
        assertEquals("adds", (mathTests[0] as CrystalTestEventsConverter.TestNode.Test).name)

        val stringTests = (tree[1] as CrystalTestEventsConverter.TestNode.Suite).children
        assertEquals(1, stringTests.size)
        assertEquals("concat", (stringTests[0] as CrystalTestEventsConverter.TestNode.Test).name)
    }

    @Test
    fun testParse_failureMarksTestAsFailed() {
        val output = """
            |Math basics
            |  adds  adds
            |  fails  fails
            |Failures:
            |  1) Math basics fails
            |     Failure/Error: assert false
            |     # spec/math_spec.cr:10
            |Finished in 0.01s
            |2 examples, 1 failure
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        assertEquals(1, tree.size)
        val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals(2, suite.children.size)

        val passingTest = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("adds", passingTest.name)
        assertFalse(passingTest.failed)

        val failingTest = suite.children[1] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("fails", failingTest.name)
        assertTrue(failingTest.failed)
        assertEquals("assert false", failingTest.failureMessage)
        assertEquals("${CrystalTestLocator.PROTOCOL}://spec/math_spec.cr:10", failingTest.failureDetails)
    }

    @Test
    fun testParse_multipleFailures() {
        val output = """
            |Math basics
            |  adds  adds
            |  fails1  fails1
            |  fails2  fails2
            |Failures:
            |  1) Math basics fails1
            |     Failure/Error: assert 1 == 2
            |  2) Math basics fails2
            |     Failure/Error: assert nil
            |Finished in 0.01s
            |3 examples, 2 failures
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        assertEquals(3, suite.children.size)

        assertFalse((suite.children[0] as CrystalTestEventsConverter.TestNode.Test).failed)
        assertTrue((suite.children[1] as CrystalTestEventsConverter.TestNode.Test).failed)
        assertTrue((suite.children[2] as CrystalTestEventsConverter.TestNode.Test).failed)
        assertEquals("assert 1 == 2", (suite.children[1] as CrystalTestEventsConverter.TestNode.Test).failureMessage)
        assertEquals("assert nil", (suite.children[2] as CrystalTestEventsConverter.TestNode.Test).failureMessage)
    }

    @Test
    fun testParse_failureInNestedSuite() {
        val output = """
            |User
            |  when admin
            |    can delete  can delete
            |    fails here  fails here
            |  when guest
            |    cannot delete  cannot delete
            |Failures:
            |  1) User when admin fails here
            |     Failure/Error: boom
            |Finished in 0.01s
            |3 examples, 1 failure
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val userSuite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        val adminSuite = userSuite.children[0] as CrystalTestEventsConverter.TestNode.Suite

        val failingTest = adminSuite.children[1] as CrystalTestEventsConverter.TestNode.Test
        assertEquals("fails here", failingTest.name)
        assertTrue(failingTest.failed)
        assertEquals("boom", failingTest.failureMessage)

        val guestSuite = userSuite.children[1] as CrystalTestEventsConverter.TestNode.Suite
        val guestTest = guestSuite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertFalse(guestTest.failed)
    }

    @Test
    fun testParse_allPassing() {
        val output = """
            |Math
            |  adds  adds
            |Finished in 0.01s
            |1 example, 0 failures
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
        val test = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
        assertFalse(test.failed)
        assertEquals("", test.failureMessage)
    }

    @Test
    fun testParse_emptyOutput() {
        val tree = CrystalTestEventsConverter.parseForTest("")
        assertEquals(0, tree.size)
    }

    // ==================== JUnit XML Timing Tests ====================

    @Test
    fun testJUnitTiming_appliesPerTestDuration() {
        val output = """
            |Calculator
            |  adds correctly  adds correctly
            |  subtracts correctly  subtracts correctly
            |  multiplies correctly  multiplies correctly
            |Finished in 150.0 ms
            |3 examples, 0 failures
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val junitXml = File.createTempFile("junit_timing", ".xml")
        try {
            junitXml.writeText("""
                <?xml version="1.0"?>
                <testsuite tests="3" time="0.150">
                  <testcase name="Calculator adds correctly" time="0.001"/>
                  <testcase name="Calculator subtracts correctly" time="0.048"/>
                  <testcase name="Calculator multiplies correctly" time="0.101"/>
                </testsuite>
            """.trimIndent())

            CrystalTestEventsConverter.applyJUnitTimingFromXml(junitXml, tree)

            val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
            assertEquals(3, suite.children.size)

            val adds = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
            val subtracts = suite.children[1] as CrystalTestEventsConverter.TestNode.Test
            val multiplies = suite.children[2] as CrystalTestEventsConverter.TestNode.Test

            assertEquals(1L, adds.durationMs)
            assertEquals(48L, subtracts.durationMs)
            assertEquals(101L, multiplies.durationMs)
        } finally {
            junitXml.delete()
        }
    }

    @Test
    fun testJUnitTiming_handlesMissingTimeAttribute() {
        val output = """
            |Math
            |  test_a  test_a
            |  test_b  test_b
            |Finished in 50.0 ms
            |2 examples, 0 failures
        """.trimMargin()

        val tree = CrystalTestEventsConverter.parseForTest(output)

        val junitXml = File.createTempFile("junit_timing", ".xml")
        try {
            junitXml.writeText("""
                <?xml version="1.0"?>
                <testsuite tests="2" time="0.050">
                  <testcase name="Math test_a" time="0.020"/>
                  <testcase name="Math test_b"/>
                </testsuite>
            """.trimIndent())

            CrystalTestEventsConverter.applyJUnitTimingFromXml(junitXml, tree)

            val suite = tree[0] as CrystalTestEventsConverter.TestNode.Suite
            val testA = suite.children[0] as CrystalTestEventsConverter.TestNode.Test
            val testB = suite.children[1] as CrystalTestEventsConverter.TestNode.Test

            assertEquals(20L, testA.durationMs)
            assertEquals(-1L, testB.durationMs) // unchanged default
        } finally {
            junitXml.delete()
        }
    }
}
