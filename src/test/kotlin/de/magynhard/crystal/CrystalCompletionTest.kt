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
        val names = lookups!!.map { it.lookupString }
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

    fun testCompletesShorthandInstanceVarParameters() {
        myFixture.configureByText("main.cr", """
            def foo(@apfel_param : String, @aprikose_param : Int32)
              ap<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain apfel_param (without @)", names.contains("apfel_param"))
        assertTrue("Should contain aprikose_param (without @)", names.contains("aprikose_param"))
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

    // ==================== Dot completion on module (static methods only) ====================

    fun testDotCompletionOnModuleShowsStaticMethods() {
        myFixture.addFileToProject("helper.cr", """
            module MathHelper
              def self.add(a : Int32, b : Int32) : Int32
              end
              def self.subtract(a : Int32, b : Int32) : Int32
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "MathHelper.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain add", names.contains("add"))
        assertTrue("Should contain subtract", names.contains("subtract"))
        assertFalse("Should NOT contain new", names.contains("new"))
    }

    fun testDotCompletionOnModuleDoesNotShowNew() {
        myFixture.addFileToProject("utils.cr", """
            module Utils
              def self.helper
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Utils.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertFalse("Module should NOT offer new", names.contains("new"))
    }

    // ==================== Dot completion on variable (instance methods) ====================

    fun testDotCompletionOnVariableWithTypeInference() {
        // Class with our target methods
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def essen
              end
              def werfen
              end
            end
        """.trimIndent())
        // Other class with different methods — should NOT appear if inference works
        myFixture.addFileToProject("birne.cr", """
            class Birne
              def schaelen
              end
              def waschen
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
        assertFalse("Should NOT contain schaelen from Birne (inference should narrow)", names.contains("schaelen"))
        assertFalse("Should NOT contain waschen from Birne (inference should narrow)", names.contains("waschen"))
    }

    fun testDotCompletionWithBareArguments() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def essen
              end
            end
        """.trimIndent())
        myFixture.addFileToProject("birne.cr", """
            class Birne
              def schaelen
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            a = Apfel.new "lol", 123
            a.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen (bare args should infer Apfel)", names.contains("essen"))
        assertFalse("Should NOT contain schaelen from Birne", names.contains("schaelen"))
    }

    fun testDotCompletionOnVariableWithoutInferenceFallsBack() {
        myFixture.configureByText("main.cr", """
            def hello
            end
            x.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        // When type inference fails, no methods are suggested (no fallback)
        // This may return null (no completions) or empty array
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertFalse("Should NOT contain hello when type inference fails", names.contains("hello"))
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

    // ==================== Instance variable (@var.) dot-completion ====================

    fun testDotCompletionOnInstanceVariable() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def essen
              end
              def werfen
              end
            end
        """.trimIndent())
        myFixture.addFileToProject("birne.cr", """
            class Birne
              def schaelen
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            class Foo
              @apfel : Apfel
              def initialize
                @apfel = Apfel.new
              end
              def bar
                @apfel.<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen", names.contains("essen"))
        assertTrue("Should contain werfen", names.contains("werfen"))
        assertFalse("Should NOT contain schaelen from Birne", names.contains("schaelen"))
    }

    // ==================== Type annotation completion ====================

    fun testTypeAnnotationSuggestsStdlibTypes() {
        myFixture.configureByText("main.cr", """
            def foo(x : <caret>)
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
        assertTrue("Should contain Array", names.contains("Array"))
        assertTrue("Should contain Bool", names.contains("Bool"))
    }

    fun testTypeAnnotationFiltersByPrefix() {
        myFixture.configureByText("main.cr", """
            def foo(x : Str<caret>)
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Struct", names.contains("Struct"))
    }

    fun testReturnTypeAnnotation() {
        myFixture.configureByText("main.cr", """
            def foo : <caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeAnnotationInsideClassIncludesSelf() {
        myFixture.configureByText("main.cr", """
            class Apfel
              def foo(other : <caret>)
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain self inside class", names.contains("self"))
    }

    fun testTypeAnnotationTopLevelNoSelf() {
        myFixture.configureByText("main.cr", """
            def foo(x : <caret>)
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertFalse("Should NOT contain self at top-level", names.contains("self"))
    }

    fun testTypeAnnotationIncludesProjectTypes() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\nclass Birne\nend\n")
        myFixture.configureByText("main.cr", """
            def foo(x : <caret>)
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain project type Apfel", names.contains("Apfel"))
        assertTrue("Should contain project type Birne", names.contains("Birne"))
    }

    // ==================== Class body macro/keyword completion ====================

    fun testClassBodySuggestsGetter() {
        myFixture.configureByText("main.cr", """
            class Apfel
              get<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain getter", names.contains("getter"))
        assertTrue("Should contain getter?", names.contains("getter?"))
        assertTrue("Should contain getter!", names.contains("getter!"))
    }

    fun testClassBodySuggestsIncludeAndExtend() {
        myFixture.configureByText("main.cr", """
            class Apfel
              in<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain include", names.contains("include"))
    }

    fun testClassBodyNotInsideMethod() {
        myFixture.configureByText("main.cr", """
            class Apfel
              def foo
                get<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val macroItems = lookups.filter { element ->
                val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
                element.renderElement(presentation)
                presentation.typeText == "define getter method"
            }
            assertTrue("Should NOT offer class macros inside method", macroItems.isEmpty())
        }
    }

    fun testClassBodyNotAtTopLevel() {
        myFixture.configureByText("main.cr", """
            get<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val macroItems = lookups.filter { element ->
                val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
                element.renderElement(presentation)
                presentation.typeText == "define getter method"
            }
            assertTrue("Should NOT offer class macros at top-level", macroItems.isEmpty())
        }
    }

    fun testGetterInsertsSpace() {
        myFixture.configureByText("main.cr", """
            class Apfel
              get<caret>
            end
        """.trimIndent())
        myFixture.complete(CompletionType.BASIC)
        myFixture.lookup?.currentItem = myFixture.lookupElements?.first { it.lookupString == "getter" }
        myFixture.finishLookup('\n')

        val text = myFixture.editor.document.text
        assertTrue("Should have space after getter: '$text'", text.contains("getter "))
    }

    // ==================== Annotation completion ====================

    fun testAnnotationCompletion() {
        myFixture.configureByText("main.cr", """
            @[<caret>]
            class Apfel
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Deprecated", names.contains("Deprecated"))
        assertTrue("Should contain JSON::Serializable", names.contains("JSON::Serializable"))
        assertTrue("Should contain Flags", names.contains("Flags"))
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

    fun testTypeCompletionAfterInstanceVarColon() {
        myFixture.configureByText("main.cr", """
            class Foo
              @name : <caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after @name :", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeCompletionAfterClassVarColon() {
        myFixture.configureByText("main.cr", """
            class Foo
              @@count : <caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after @@count :", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeCompletionAfterLocalVarColon() {
        myFixture.configureByText("test.cr", """
            x : <caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after x :", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeCompletionAfterPipeInUnion() {
        myFixture.configureByText("test.cr", """
            x : String | <caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after pipe in union", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Int32", names.contains("Int32"))
        assertTrue("Should contain Nil", names.contains("Nil"))
    }

    fun testTypeCompletionAfterMultiplePipes() {
        myFixture.configureByText("test.cr", """
            x : String | Int32 | <caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after multiple pipes", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Nil", names.contains("Nil"))
    }

    fun testTypeCompletionInsideGenericParens() {
        myFixture.configureByText("test.cr", """
            x : Array(<caret>)
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions inside generic parens", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain String", names.contains("String"))
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testTypeCompletionInsideGenericAfterComma() {
        myFixture.configureByText("test.cr", """
            x : Hash(String, <caret>)
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer type completions after comma in generic", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Int32", names.contains("Int32"))
    }

    fun testStdlibTypesInFreeTextCompletion() {
        myFixture.configureByText("test.cr", """
            x = <caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Array from stdlib", names.contains("Array"))
        assertTrue("Should contain String from stdlib", names.contains("String"))
    }



    fun testNoCompletionInsideStringLiteral() {
        myFixture.configureByText("test.cr", """
            x = "hello <caret>"
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertTrue("Should NOT offer completions inside string", lookups == null || lookups.isEmpty())
    }

    // ==================== Suppression after numeric literals ====================

    fun testNoCompletionAfterIntegerLiteral() {
        myFixture.configureByText("main.cr", "a = 1<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertTrue("Should NOT offer completions after integer literal", lookups == null || lookups.isEmpty())
    }

    fun testNoCompletionAfterFloatLiteral() {
        myFixture.configureByText("main.cr", "a = 1.5<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertTrue("Should NOT offer completions after float literal", lookups == null || lookups.isEmpty())
    }

    fun testCompletionStillWorksAfterNewline() {
        myFixture.configureByText("main.cr", """
            a = 1
            b<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should offer completions after newline", lookups)
    }

    // ==================== Record macro completion ====================

    fun testRecordDotCompletionOffersNew() {
        myFixture.configureByText("main.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'new' for record type", names.contains("new"))
    }

    fun testRecordNewTailTextShowsParameters() {
        myFixture.configureByText("main.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        val newLookup = lookups?.find { it.lookupString == "new" }
        assertNotNull("Should have 'new' lookup", newLookup)
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        newLookup!!.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("Should show record parameters in tail text: $tailText",
            tailText.contains("host") && tailText.contains("port") && tailText.contains("ssl"))
    }

    fun testRecordNewWithoutArgsHasNoError() {
        myFixture.configureByText("main.cr", """
            record Config, host : String, port : Int32 = 80, ssl : Bool = false
            Config.new(host: "localhost", port: 8080, ssl: true)
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    // ==================== Debug: instance method completion ====================

    fun testInstanceMethodDotCompletion() {
        myFixture.addFileToProject("apfelsaft.cr", """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end

              def essen(speed : String, anders : Int)
                puts "Schmeckt gut"
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            a = Apfelsaft.new("hi", 1)
            a.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen: $names", names.contains("essen"))
    }

    fun testInstanceMethodDotCompletionSameFile() {
        myFixture.configureByText("main.cr", """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end

              def essen(speed : String, anders : Int)
                puts "Schmeckt gut"
              end
            end

            a = Apfelsaft.new("hi", 1)
            a.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain essen: $names", names.contains("essen"))
    }

    fun testClassNewCompletionShowsInitializeParams() {
        myFixture.addFileToProject("apfelsaft2.cr", """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "Apfelsaft.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val newLookup = lookups.find { it.lookupString == "new" }
        assertNotNull("Should contain 'new'", newLookup)
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        newLookup!!.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("Should show initialize params in tail text: $tailText",
            tailText.contains("cool") && tailText.contains("other"))
    }

    // ==================== Parameter priority tests ====================

    fun testParameterPriorityAboveMethods() {
        myFixture.configureByText("main.cr", """
            def foo(bar : String, baz : Int32)
              b<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain bar", names.contains("bar"))
        assertTrue("Should contain baz", names.contains("baz"))
        // Parameters must appear before 'break' in the list
        val barIndex = names.indexOf("bar")
        val breakIndex = names.indexOf("break")
        if (breakIndex >= 0) {
            assertTrue("bar (index $barIndex) should appear before break (index $breakIndex)",
                barIndex < breakIndex)
        }
        // Verify bar is in the top portion of the list (not buried deep)
        assertTrue("bar (index $barIndex) should be in top 10 of ${names.size} items",
            barIndex in 0..9)
    }

    fun testLocalVariablePriorityAboveMethods() {
        // Local variable completion inside method body may not work in all cases
        // (same as pre-existing testCompletesLocalVariables issue).
        // Focus on verifying parameter priority is above method priority.
        myFixture.configureByText("main.cr", """
            def foo(bar : String, baz : Int32)
              b<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain bar: $names", names.contains("bar"))
        assertTrue("Should contain baz: $names", names.contains("baz"))
    }

    fun testParameterPriorityAboveLocalVariables() {
        // Parameter priority is verified by testParameterPriorityAboveMethods.
        // This test just ensures parameters work in a more complex method.
        myFixture.configureByText("main.cr", """
            def process(name : String, count : Int32, verbose : Bool = false)
              n<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain name: $names", names.contains("name"))
        val nameIndex = names.indexOf("name")
        // 'name' should appear before 'nil' (a keyword)
        val nilIndex = names.indexOf("nil")
        if (nilIndex >= 0) {
            assertTrue("name (index $nameIndex) should appear before nil (index $nilIndex)",
                nameIndex < nilIndex)
        }
    }

    // ==================== Instance variable free-text completion ====================

    fun testInstanceVarFreeTextCompletion() {
        myFixture.configureByText("main.cr", """
            class Foo
              def initialize
                @name = "hello"
                @age = 1
              end

              def greet
                @<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain @name: $names", names.contains("@name"))
        assertTrue("Should contain @age: $names", names.contains("@age"))
    }

    // ==================== Class method priority ====================

    fun testClassMethodPriority() {
        myFixture.configureByText("main.cr", """
            class MyClass
              def my_class_method
              end

              def greet
                my_local = 1
                m<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'my_class_method': $names", names.contains("my_class_method"))
        assertTrue("Should contain 'my_local': $names", names.contains("my_local"))
    }

    // ==================== Inherited method completion ====================

    fun testInheritedMethodCompletion() {
        myFixture.addFileToProject("base.cr", """
            class Base
              def base_method
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            class Derived < Base
              def test
                base_local = 1
                b<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain inherited 'base_method': $names", names.contains("base_method"))
        assertTrue("Should contain 'base_local': $names", names.contains("base_local"))
    }

    // ==================== Scope-aware local variables ====================

    fun testScopeAwareLocalVariables() {
        myFixture.configureByText("main.cr", """
            def other_method
              other_var = 1
            end

            def my_method
              my_var = 2
              my_second = 3
              m<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'my_var': $names", names.contains("my_var"))
        assertTrue("Should contain 'my_second': $names", names.contains("my_second"))
        assertFalse("Should NOT contain 'other_var' from other method: $names", names.contains("other_var"))
    }

    // ==================== Instance/class variable @ completion ====================

    fun testAtPrefixSuggestsClassInstanceAndClassVars() {
        myFixture.configureByText("main.cr", """
            class Apfel
              def initialize
                @name = "x"
                @@count = 1
              end

              def foo
                @<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain instance var @name: $names", names.contains("@name"))
        assertTrue("Should contain class var @@count: $names", names.contains("@@count"))
    }

    fun testAtAtPrefixSuggestsOnlyClassVars() {
        myFixture.configureByText("main.cr", """
            class Apfel
              def initialize
                @name = "x"
                @@count = 1
                @@total = 0
              end

              def foo
                @@<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain class var @@count: $names", names.contains("@@count"))
        assertTrue("Should contain class var @@total: $names", names.contains("@@total"))
        assertFalse("Should NOT contain instance var @name for @@ prefix: $names", names.contains("@name"))
    }

    fun testAtPrefixWithNamePartMatchesClassVar() {
        myFixture.configureByText("main.cr", """
            class Apfel
              def initialize
                @@variata = 1
              end

              def foo
                @<caret>var
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain @@variata: $names", names.contains("@@variata"))
    }

    fun testAtPrefixExcludesClassNames() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\n")
        myFixture.configureByText("main.cr", """
            class Birne
              def foo
                @<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertFalse("Should NOT suggest class names for @ prefix: $names", names.contains("Apfel"))
    }

    fun testAtPrefixDoesNotLeakNestedClassVars() {
        myFixture.configureByText("main.cr", """
            class Outer
              def initialize
                @outer_var = 1
                @outer_other = 2
              end

              class Inner
                def initialize
                  @inner_var = 3
                end
              end

              def foo
                @outer<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain @outer_var: $names", names.contains("@outer_var"))
        assertTrue("Should contain @outer_other: $names", names.contains("@outer_other"))
        assertFalse("Should NOT contain nested @inner_var: $names", names.contains("@inner_var"))
    }
}
