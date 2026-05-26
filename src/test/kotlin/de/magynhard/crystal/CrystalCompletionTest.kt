package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for CrystalCompletionContributor.
 */
class CrystalCompletionTest : BasePlatformTestCase() {

    // ==================== Free-text completion ====================

    fun testCompletesClassNames() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\nclass Aprikose\nend\n")
        myFixture.configureByText("main.cr", "x = Ap<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Apfel", names.contains("Apfel"))
        assertTrue("Should contain Aprikose", names.contains("Aprikose"))
    }

    fun testCompletesMethodNames() {
        myFixture.addFileToProject("lib.cr", "def tanzen\nend\ndef tauchen\nend\n")
        myFixture.configureByText("main.cr", "ta<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain tanzen", names.contains("tanzen"))
        assertTrue("Should contain tauchen", names.contains("tauchen"))
    }

    fun testCompletesLocalVariables() {
        myFixture.configureByText("main.cr", """
            def foo
              meine_variable = 42
              mein_anderes = 99
              mein<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain meine_variable", names.contains("meine_variable"))
        assertTrue("Should contain mein_anderes", names.contains("mein_anderes"))
    }

    fun testCompletesParameters() {
        myFixture.configureByText("main.cr", """
            def foo(apfel_param : String, aprikose_param : Int32)
              ap<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain apfel_param", names.contains("apfel_param"))
        assertTrue("Should contain aprikose_param", names.contains("aprikose_param"))
    }

    // ==================== Dot completion on class (static methods) ====================

    fun testDotCompletionOnClassShowsStaticMethods() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def self.create
              end
              def eat
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Apfel.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain create", names.contains("create"))
        assertTrue("Should contain new", names.contains("new"))
        assertFalse("Should NOT contain instance method eat", names.contains("eat"))
    }

    fun testDotCompletionOnClassShowsNew() {
        myFixture.addFileToProject("birne.cr", """
            class Birne
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Birne.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain new", names.contains("new"))
    }

    fun testNewShowsInitializeParameters() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def initialize(name : String, gewicht : Int32)
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Apfel.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val newElement = lookups.first { it.lookupString == "new" }
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        newElement.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("Should show parameters: $tailText", tailText.contains("name") && tailText.contains("gewicht"))
    }

    fun testNewWithParameterlessInitialize() {
        myFixture.addFileToProject("birne.cr", """
            class Birne
              def initialize
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Birne.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val newElement = lookups.first { it.lookupString == "new" }
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        newElement.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("Parameterless initialize should show empty tail: '$tailText'", tailText.isEmpty() || tailText == "()")
    }

    fun testNewWithoutInitialize() {
        myFixture.addFileToProject("kirsche.cr", """
            class Kirsche
              def essen
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Kirsche.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val newElement = lookups.first { it.lookupString == "new" }
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        newElement.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("No initialize should show empty tail: '$tailText'", tailText.isEmpty() || tailText == "()")
    }

    // ==================== Dot completion on variable (instance methods) ====================

    fun testDotCompletionOnVariableWithTypeInference() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def essen
              end
              def werfen
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            a = Apfel.new
            a.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen", names.contains("essen"))
        assertTrue("Should contain werfen", names.contains("werfen"))
    }

    fun testDotCompletionOnVariableWithoutInferenceFallsBack() {
        myFixture.configureByText("main.cr", """
            def hello
            end
            x.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        // Should not crash, and should show all methods as fallback
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain hello as fallback", names.contains("hello"))
    }

    // ==================== Parameter type-annotated inference ====================

    fun testDotCompletionOnParameterWithTypeAnnotation() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def schmecken
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            def foo(a : Apfel)
              a.<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain schmecken", names.contains("schmecken"))
    }

    // ==================== Edge cases ====================

    fun testNoCompletionsInEmptyFile() {
        myFixture.configureByText("main.cr", "<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        // Should not crash — may return null (auto-inserted) or empty
        // Just verifying no exception is thrown
    }

    // ==================== Override method completion (def inside class) ====================

    fun testDefInsideClassSuggestsInitialize() {
        myFixture.configureByText("main.cr", """
            class Apfel
              def ini<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain initialize", names.contains("initialize"))
    }

    fun testDefInsideClassSuggestsToS() {
        myFixture.configureByText("main.cr", """
            class Apfel
              def to<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain to_s", names.contains("to_s"))
    }

    fun testDefInsideClassInitializeInsertsSuper() {
        myFixture.configureByText("main.cr", """
            class Apfel
              def ini<caret>
            end
        """.trimIndent())
        myFixture.complete(CompletionType.BASIC)
        // Select "initialize" from the list
        myFixture.lookup?.currentItem = myFixture.lookupElements?.first { it.lookupString == "initialize" }
        myFixture.finishLookup('\n')

        val text = myFixture.editor.document.text
        assertTrue("Should contain 'super' in body: $text", text.contains("super"))
        assertTrue("Should contain 'end' closing: $text", text.contains("end"))
    }

    fun testDefOutsideClassNoOverrideSuggestions() {
        myFixture.configureByText("main.cr", """
            def ini<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val names = lookups.map { it.lookupString }
            // Should NOT contain override methods with "override" type text
            val overrideItems = lookups.filter { element ->
                val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
                element.renderElement(presentation)
                presentation.typeText == "override"
            }
            assertTrue("Should have no override suggestions outside class", overrideItems.isEmpty())
        }
    }
}
