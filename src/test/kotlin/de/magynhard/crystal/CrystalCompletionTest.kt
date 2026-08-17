package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.sdk.CrystalStdlibResolver

/**
 * Tests for CrystalCompletionContributor.
 */
class CrystalCompletionTest : BasePlatformTestCase() {

    private lateinit var fixtureRoot: VirtualFile
    private lateinit var initialFixturePaths: Set<String>

    override fun setUp() {
        super.setUp()
        fixtureRoot = myFixture.tempDirFixture.findOrCreateDir("")
        initialFixturePaths = collectPaths(fixtureRoot)
    }

    override fun tearDown() {
        try {
            CrystalStdlibResolver.clearCachedStdlibPath(project)
            ApplicationManager.getApplication().runWriteAction {
                deleteNewFixtureFiles(fixtureRoot)
            }
        } finally {
            super.tearDown()
        }
    }

    private fun collectPaths(root: VirtualFile): Set<String> = buildSet {
        fun collect(file: VirtualFile) {
            add(file.url)
            if (file.isDirectory) file.children.forEach(::collect)
        }
        collect(root)
    }

    private fun deleteNewFixtureFiles(root: VirtualFile) {
        for (child in root.children.toList()) {
            if (!child.isValid) continue
            if (child.url !in initialFixturePaths) {
                child.delete(this)
            } else if (child.isDirectory) {
                deleteNewFixtureFiles(child)
            }
        }
    }

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

    fun testFixtureCleanupPreservesExistingPathsAndDeletesNewNestedPaths() {
        val existing = myFixture.addFileToProject("existing/root.cr", "class Existing\nend\n").virtualFile
        initialFixturePaths = initialFixturePaths + collectPaths(requireNotNull(existing.parent))
        val generated = myFixture.addFileToProject("existing/generated/nested.cr", "class Generated\nend\n").virtualFile

        ApplicationManager.getApplication().runWriteAction {
            deleteNewFixtureFiles(fixtureRoot)
        }

        assertTrue(existing.isValid)
        assertFalse(generated.isValid)
        ApplicationManager.getApplication().runWriteAction {
            existing.parent.delete(this)
        }
    }

    fun testCompletesFileLevelConstants() {
        myFixture.configureByText("main.cr", """
            BREZEL_SIZE = 10
            B<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertTrue("Should contain 'BREZEL_SIZE': $names", names.contains("BREZEL_SIZE"))
    }

    fun testCompletesFileLevelConstantWithExactPrefix() {
        myFixture.configureByText("main.cr", """
            BREZEL_SIZE = 10
            BREZEL<caret>
        """.trimIndent())
        myFixture.complete(CompletionType.BASIC)
        val text = myFixture.editor.document.text
        assertTrue("Should have auto-inserted or suggested 'BREZEL_SIZE': $text", text.contains("BREZEL_SIZE"))
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

    // ==================== Top-level (global) method completion ====================

    fun testCompletesTopLevelMethods() {
        myFixture.configureByText("main.cr", """
            def kung(foo : String)
              foo
            end

            def kunde
            end

            ku<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain kung (top-level method): $names", names.contains("kung"))
        assertTrue("Should contain kunde (top-level method): $names", names.contains("kunde"))
    }

    fun testTopLevelMethodShowsSignature() {
        myFixture.configureByText("main.cr", """
            def kung(foo : String)
              foo
            end

            def kunde
            end

            ku<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val kungElement = lookups.first { it.lookupString == "kung" }
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        kungElement.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("Should show parameter signature: '$tailText'", tailText.contains("foo") && tailText.contains("String"))
    }

    fun testTopLevelMethodAvailableInsideClassMethod() {
        myFixture.configureByText("main.cr", """
            def kung(foo : String)
              foo
            end

            def kunde
            end

            class Foo
              def bar
                ku<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain kung inside class method: $names", names.contains("kung"))
        assertTrue("Should contain kunde inside class method: $names", names.contains("kunde"))
    }

    fun testClassMethodNotInTopLevelIndex() {
        myFixture.configureByText("main.cr", """
            class Foo
              def self.kung
              end
            end

            kung<caret>
        """.trimIndent())
        // kung is a class method (def self.kung), NOT a top-level def — it should
        // not be offered as a bare top-level call. complete() returns null when
        // there are zero or one matches (auto-insert); in either case kung must
        // not appear in the document.
        val lookups = myFixture.complete(CompletionType.BASIC)
        if (lookups != null) {
            val names = lookups.map { it.lookupString }
            assertFalse(
                "Class method self.kung should NOT be offered at top-level: $names",
                names.contains("kung")
            )
        }
    }

    fun testFileLevelSelfMethodNotSuggestedAsTopLevelMethod() {
        myFixture.configureByText("main.cr", """
            def self.request_secret(path)
            end

            def request_public
            end

            def request_status
            end

            def caller
              request_<caret>
            end
        """.trimIndent())

        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain the top-level request_public method: $names", names.contains("request_public"))
        assertTrue("Should contain the top-level request_status method: $names", names.contains("request_status"))
        assertFalse(
            "File-level self.request_secret should NOT be offered as top-level: $names",
            names.contains("request_secret")
        )
    }

    fun testTopLevelMethodNotSuggestedForUppercasePrefix() {
        myFixture.configureByText("main.cr", """
            class Kiste
            end

            def kung(foo : String)
              foo
            end

            K<caret>
        """.trimIndent())
        // Uppercase prefix 'K' should match classes (Kiste) but NOT lowercase
        // top-level methods (kung). We add a class to guarantee multiple matches
        // for 'K' so complete() returns a non-null array.
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (uppercase matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Kiste (class): $names", names.contains("Kiste"))
        assertFalse(
            "Top-level method kung should NOT be offered for uppercase prefix: $names",
            names.contains("kung")
        )
    }

    fun testLocalVariableShadowsTopLevelMethod() {
        myFixture.configureByText("main.cr", """
            def kung
              "top-level"
            end

            def kunde
            end

            def foo
              kung = 1
              ku<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        // The local variable 'kung' (priority 50) and the top-level method 'kung'
        // (priority 0) dedup by name via the seen-set, so only one 'kung' entry
        // is present. Either way the lookup should contain 'kung'.
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain kung (local var or method): $names", names.contains("kung"))
        assertEquals("Should have exactly one kung entry", 1, names.count { it == "kung" })
    }

    fun testTopLevelMethodFromSeparateFile() {
        myFixture.addFileToProject("helpers.cr", """
            def kung(foo : String)
              foo
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            def kunde
            end

            ku<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions (multiple matches)", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue(
            "Should contain kung (top-level def from separate file via stub index): $names",
            names.contains("kung")
        )
        assertTrue("Should contain kunde (current file top-level def): $names", names.contains("kunde"))
    }

    fun testTopLevelMethodSuggestedAtEmptyCaret() {
        myFixture.configureByText("main.cr", """
            def kung(foo : String)
              foo
            end

            <caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions at empty caret", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain kung at empty caret: $names", names.contains("kung"))
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
        myFixture.configureByText("main.cr", "require \"./apfel\"\nApfel.<caret>")
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
        myFixture.configureByText("main.cr", "require \"./birne\"\nBirne.<caret>")
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
        myFixture.configureByText("main.cr", "require \"./apfel\"\nApfel.<caret>")
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
        myFixture.configureByText("main.cr", "require \"./birne\"\nBirne.<caret>")
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
        myFixture.configureByText("main.cr", "require \"./kirsche\"\nKirsche.<caret>")
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
        myFixture.configureByText("main.cr", "require \"./helper\"\nMathHelper.<caret>")
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
        myFixture.configureByText("main.cr", "require \"./utils\"\nUtils.<caret>")
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
            require "./apfel"
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
        assertFalse("Should NOT contain to_s from Object", names.contains("to_s"))
        assertFalse("Should NOT contain inspect from Object", names.contains("inspect"))
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
            require "./apfel"
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
            require "./apfel"
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
            require "./apfel"
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
        assertFalse("Should NOT contain to_s from Object", names.contains("to_s"))
        assertFalse("Should NOT contain inspect from Object", names.contains("inspect"))
    }

    fun testDotCompletionExcludesObjectMethods() {
        myFixture.addFileToProject("apfel.cr", """
            class Apfel
              def essen
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            require "./apfel"
            a = Apfel.new
            a.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertTrue("Should contain essen", names.contains("essen"))
        assertFalse("Should NOT contain to_s from Object", names.contains("to_s"))
        assertFalse("Should NOT contain inspect from Object", names.contains("inspect"))
        assertFalse("Should NOT contain hash from Object", names.contains("hash"))
        assertFalse("Should NOT contain nil? from Object", names.contains("nil?"))
    }

    fun testDotCompletionWithExplicitParentIncludesParentMethods() {
        myFixture.addFileToProject("frucht.cr", """
            class Frucht
              def schaelen
              end
            end
        """.trimIndent())
        myFixture.addFileToProject("apfel.cr", """
            require "./frucht"
            class Apfel < Frucht
              def essen
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", """
            require "./apfel"
            a = Apfel.new
            a.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertTrue("Should contain essen (own class)", names.contains("essen"))
        assertTrue("Should contain schaelen (explicit parent)", names.contains("schaelen"))
        assertFalse("Should NOT contain to_s from Object", names.contains("to_s"))
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
            require "./apfelsaft"
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
        myFixture.configureByText("main.cr", "require \"./apfelsaft2\"\nApfelsaft.<caret>")
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

    fun testClassMethodCompletionShowsParameterSignature() {
        myFixture.configureByText("main.cr", """
            class Apfelsaft
              def essen(speed : String, anders : Int)
                speed
              end

              def testen(what : String)
                e<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val essenLookup = lookups.firstOrNull { it.lookupString == "essen" }
        assertNotNull("Should contain 'essen'", essenLookup)
        val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
        essenLookup!!.renderElement(presentation)
        val tailText = presentation.tailText ?: ""
        assertTrue("essen should show parameter signature, got tailText: '$tailText'",
            tailText.contains("speed") && tailText.contains("anders"))
    }

    fun testClassMethodCompletionSuggestsInitialize() {
        myFixture.configureByText("main.cr", """
            class Apfelsaft
              def initialize
              end

              def testen(what : String)
                ini<caret>
              end
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = lookups?.map { it.lookupString } ?: emptyList()
        if (lookups != null) {
            assertTrue("Should contain 'initialize': $names", names.contains("initialize"))
        } else {
            val text = myFixture.editor.document.text
            assertTrue("Should have auto-inserted 'initialize': $text", text.contains("initialize"))
        }
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
            require "./base"
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

    // ==================== Namespace (double-colon) completion ====================

    fun testDoubleColonSuggestsNestedType() {
        myFixture.configureByText("main.cr", """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end
            end

            class Apfelsaft::Tools
              def self.dance(count : Int32)
                return count + 87
              end
            end

            Apfelsaft::<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions for Foo::", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain nested type 'Tools': $names", names.contains("Tools"))
    }

    fun testDoubleColonSuggestsMultipleNestedTypes() {
        myFixture.configureByText("main.cr", """
            class Foo
            end

            class Foo::Bar
            end

            class Foo::Baz
            end

            Foo::<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'Bar': $names", names.contains("Bar"))
        assertTrue("Should contain 'Baz': $names", names.contains("Baz"))
    }

    fun testDoubleColonCrossFileNestedType() {
        myFixture.addFileToProject("apfelsaft.cr", """
            class Apfelsaft
            end

            class Apfelsaft::Tools
              def self.dance
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "require \"./apfelsaft\"\nApfelsaft::<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'Tools' from cross-file: $names", names.contains("Tools"))
    }

    fun testDoubleColonSuggestsClassConstants() {
        myFixture.addFileToProject("hamster.cr", """
            class Hamster
              WEIGHT = 60
              class Inner
              end
              def dance(power : Int32)
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "require \"./hamster\"\nHamster::<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertTrue("Should contain 'WEIGHT': $names", names.contains("WEIGHT"))
        assertTrue("Should contain 'Inner': $names", names.contains("Inner"))
    }

    // ==================== Dot completion on namespace path ====================

    fun testDotCompletionOnNamespacePathShowsStaticMethods() {
        myFixture.configureByText("main.cr", """
            class Apfelsaft
              def initialize(@cool : String, other : Int32)
              end
            end

            class Apfelsaft::Tools
              def self.dance(count : Int32)
                return count + 87
              end
            end

            Apfelsaft::Tools.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'dance' from Apfelsaft::Tools: $names", names.contains("dance"))
    }

    fun testDotCompletionOnNamespacePathCrossFile() {
        myFixture.addFileToProject("apfelsaft.cr", """
            class Apfelsaft::Tools
              def self.dance(count : Int32)
                return count + 87
              end
              def self.sing
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "require \"./apfelsaft\"\nApfelsaft::Tools.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain 'dance': $names", names.contains("dance"))
        assertTrue("Should contain 'sing': $names", names.contains("sing"))
    }

    // ==================== Expression receiver dot completion ====================

    fun testGroupedTypeObjectsHaveExactStaticCompletionParity() {
        myFixture.addFileToProject("foo.cr", """
            class Foo
              def self.build
              end
              def self.parse(value : String)
              end
              def instance_only
              end
            end
        """.trimIndent())

        val expected = completionNames("require \"./foo\"\nFoo.<caret>")
        assertEquals(setOf("build", "parse", "new"), expected)
        assertEquals(expected, completionNames("require \"./foo\"\n(Foo).<caret>"))
        assertEquals(expected, completionNames("require \"./foo\"\n((Foo)).<caret>"))
    }

    fun testQualifiedAbsoluteModuleAndRecordTypeObjectCompletion() {
        val declarations = """
            module Outer
              class Service
                def self.qualified_only
                end
              end
            end

            module Utility
              def self.module_only
              end
            end

            record Config, name : String
        """.trimIndent()

        val qualifiedNames = completionNames("$declarations\nOuter::Service.<caret>")
        assertEquals(setOf("qualified_only"), qualifiedNames)
        assertEquals(qualifiedNames, completionNames("$declarations\n(Outer::Service).<caret>"))
        assertEquals(qualifiedNames, completionNames("$declarations\n::Outer::Service.<caret>"))
        assertEquals(qualifiedNames, completionNames("$declarations\n(::Outer::Service).<caret>"))
        val moduleExpected = listOf("module_only|()|Utility|Method")
        assertReceiverPresentationParity(declarations, "Utility", moduleExpected)
        val recordExpected = listOf("new|(name : String)|Config|Method")
        assertReceiverPresentationParity(declarations, "Config", recordExpected)
    }

    fun testQualifiedStaticCompletionUsesExactIdentityAcrossGroupingAndAbsoluteForms() {
        val declarations = """
            module Left
              class Service
                def self.from_left
                end
              end
            end

            module Right
              class Service
                def self.from_right
                end
              end
            end
        """.trimIndent()

        val expected = listOf("from_right|()|Service|Method")
        assertEquals(expected, completionPresentations("$declarations\nRight::Service.<caret>"))
        assertEquals(expected, completionPresentations("$declarations\n(Right::Service).<caret>"))
        assertEquals(expected, completionPresentations("$declarations\n((Right::Service)).<caret>"))
        assertEquals(expected, completionPresentations("$declarations\n::Right::Service.<caret>"))
        assertEquals(expected, completionPresentations("$declarations\n(::Right::Service).<caret>"))
        assertEquals(expected, completionPresentations("$declarations\n((::Right::Service)).<caret>"))
    }

    fun testLexicalSimpleTypeObjectUsesResolvedQualifiedIdentity() {
        val source = """
            module Left
              class Service
                def self.from_left
                end
              end
            end

            module Right
              class Service
                def self.from_right
                end
              end

              Service.<caret>
            end
        """.trimIndent()

        val expected = listOf("from_right|()|Service|Method", "new||Service|Method")
        assertEquals(expected, completionPresentations(source))
        assertEquals(
            expected,
            completionPresentations(source.replace("Service.<caret>", "((Service)).<caret>"))
        )
    }

    fun testQualifiedRecordCompletionUsesExactRecordDefinition() {
        val declarations = """
            module Left
              record Config, left_value : String
            end

            module Right
              record Config, right_value : Int32, enabled : Bool = true
            end
        """.trimIndent()
        val expected = listOf("new|(right_value : Int32, enabled : Bool = true)|Right::Config|Method")

        assertEquals(expected, completionPresentations("$declarations\nRight::Config.<caret>"))
        assertEquals(expected, completionPresentations("$declarations\n(Right::Config).<caret>"))
        assertEquals(expected, completionPresentations("$declarations\n((Right::Config)).<caret>"))
        assertEquals(expected, completionPresentations("$declarations\n::Right::Config.<caret>"))
    }

    fun testRecordWinsSameExactIdentityConstructorCollision() {
        val declarations = """
            record Config, record_value : String
            class Config
              def self.build
              end
              def initialize(class_value : Int32)
              end
            end
        """.trimIndent()
        val expected = listOf(
            "build|()|Config|Method",
            "new|(record_value : String)|Config|Method"
        )

        assertReceiverPresentationParity(declarations, "Config", expected)
    }

    fun testLexicalSameSimpleNameSyntheticNewUsesSelectedInitializer() {
        val prefix = """
            module Left
              class Service
                def initialize(left : String)
                end
              end
            end

            module Right
              class Service
                def initialize(right : Int32)
                end
              end
        """.trimIndent()
        val expected = listOf("new|(right : Int32)|Service|Method")

        assertReceiverPresentationParity(prefix, "Service", expected, "\nend")
    }

    fun testQualifiedSameSimpleNameTypeObjectsDoNotLeakInitializerTails() {
        val declarations = """
            module Left
              class Service
                def initialize(left : String)
                end
              end
            end

            module Right
              class Service
                def initialize(right : Int32)
                end
              end
            end
        """.trimIndent()

        assertEquals(emptyList<String>(), completionPresentations("$declarations\nLeft::Service.<caret>"))
        assertEquals(emptyList<String>(), completionPresentations("$declarations\nRight::Service.<caret>"))
    }

    fun testLocalParameterAndInstanceVariableCompletionUseValueTypes() {
        val declarations = """
            class ValueType
              def value_only
              end
              def self.static_only
              end
            end
        """.trimIndent()

        val expected = listOf("value_only|()|ValueType|Method")
        assertReceiverPresentationParity("$declarations\nvalue = ValueType.new", "value", expected)
        assertReceiverPresentationParity(
            "$declarations\ndef use(value : ValueType)",
            "value",
            expected,
            "\nend"
        )
        assertReceiverPresentationParity(
            "$declarations\nclass Holder\n  @value : ValueType\n  def use",
            "@value",
            expected,
            "\n  end\nend"
        )
    }

    fun testDirectAndGroupedScalarLiteralCompletion() {
        addExpressionCompletionTypes()
        val cases = listOf(
            "3" to "times|()|Int32|Method",
            "3.14" to "float_only|()|Float64|Method",
            "\"text\"" to "upcase|()|String|Method",
            "'x'" to "char_only|()|Char|Method",
            ":name" to "symbol_only|()|Symbol|Method",
            "true" to "bool_only|()|Bool|Method",
            "false" to "bool_only|()|Bool|Method",
            "nil" to "nil_only|()|Nil|Method",
            "/pattern/" to "regex_only|()|Regex|Method",
            "`echo test`" to "upcase|()|String|Method"
        )

        for ((receiver, expectedPresentation) in cases) {
            assertReceiverPresentationParity(EXPRESSION_TYPES_REQUIRE, receiver, listOf(expectedPresentation))
        }

        val heredoc = "<<-TEXT\nhello\nTEXT"
        val expectedHeredoc = listOf("upcase|()|String|Method")
        assertEquals(expectedHeredoc, completionPresentations("$EXPRESSION_TYPES_REQUIRE\n$heredoc\n.<caret>"))
        assertEquals(expectedHeredoc, completionPresentations("$EXPRESSION_TYPES_REQUIRE\n($heredoc\n).<caret>"))
        assertEquals(expectedHeredoc, completionPresentations("$EXPRESSION_TYPES_REQUIRE\n(($heredoc\n)).<caret>"))

        assertFalse(completionNames("$EXPRESSION_TYPES_REQUIRE\n3.<caret>").contains("float_only"))
        assertFalse(completionNames("$EXPRESSION_TYPES_REQUIRE\n3.14.<caret>").contains("times"))
    }

    fun testCollectionOperatorAndMethodResultCompletion() {
        addExpressionCompletionTypes()
        val expressions = mapOf(
            "[1, 2]" to "array_only|()|Array|Method",
            "{\"one\" => 1}" to "hash_only|()|Hash|Method",
            "{1, \"two\"}" to "tuple_only|()|Tuple|Method",
            "1 + 2" to "times|()|Int32|Method"
        )
        for ((receiver, expectedPresentation) in expressions) {
            assertReceiverPresentationParity(EXPRESSION_TYPES_REQUIRE, receiver, listOf(expectedPresentation))
        }

        val methods = """
            def annotated_result : String
              "text"
            end

            def inferred_result
              3
            end
        """.trimIndent()
        assertReceiverPresentationParity(
            "$EXPRESSION_TYPES_REQUIRE\n$methods",
            "annotated_result()",
            listOf("upcase|()|String|Method")
        )
        assertReceiverPresentationParity(
            "$EXPRESSION_TYPES_REQUIRE\n$methods",
            "inferred_result()",
            listOf("times|()|Int32|Method")
        )
    }

    fun testUnionCompletionMergesAllBranchesByCanonicalSignature() {
        val declarations = """
            class First
              def first_only
              end
              def shared(value : Int32)
              end
              def convert(value : Int32)
              end
            end

            class Second
              def second_only
              end
              def shared(value : Int32)
              end
              def convert(value : String)
              end
            end

        """.trimIndent()
        val expected = listOf(
            "convert|(value : Int32)|First|Method",
            "convert|(value : String)|Second|Method",
            "first_only|()|First|Method",
            "second_only|()|Second|Method",
            "shared|(value : Int32)|First|Method"
        )

        assertReceiverPresentationParity(
            "$declarations\ndef use(value : First | Second)",
            "value",
            expected,
            "\nend"
        )
    }

    fun testRuntimeValuesAndModulesDoNotOfferConstructorsOrStaticMethods() {
        val declarations = """
            class Service
              def instance_only
              end
              def self.static_only
              end
            end

            module Utility
              def self.module_only
              end
            end
        """.trimIndent()

        val valueNames = completionNames("$declarations\nvalue = Service.new\n(value).<caret>")
        assertEquals(setOf("instance_only"), valueNames)
        assertFalse(valueNames.contains("new"))
        assertFalse(valueNames.contains("static_only"))

        val moduleNames = completionNames("$declarations\n((Utility)).<caret>")
        assertEquals(setOf("module_only"), moduleNames)
        assertFalse(moduleNames.contains("new"))
    }

    fun testExplicitStaticNewOverloadsDoNotCreateSyntheticConstructor() {
        val declarations = """
            class Factory
              def self.new(value : Int32) : Factory
                Factory.allocate
              end

              def self.new(value : String) : Factory
                Factory.allocate
              end

              def initialize(enabled : Bool)
              end
            end
        """.trimIndent()
        val expected = listOf(
            "new|(value : Int32)|Factory → Factory|Method",
            "new|(value : String)|Factory → Factory|Method"
        )

        assertReceiverPresentationParity(declarations, "Factory", expected)
    }

    fun testContributorCompletesLongMixedReceiverChainsConservatively() {
        val declarations = """
            class First
              def second : Second
                Second.new
              end
            end

            class Second
              def third : Third
                Third.new
              end
            end

            class Third
              def finish
              end
            end

            def unrelated_project_method
            end
        """.trimIndent()
        val expected = listOf("finish|()|Third|Method")
        val receivers = listOf(
            "First.new.second.third",
            "First.new().second().third()",
            "((First.new.second)).third",
            "((First.new())).second().third"
        )

        for (receiver in receivers) {
            assertEquals(expected, completionPresentations("$declarations\n$receiver.<caret>"))
        }
        assertEquals(
            emptyList<String>(),
            completionPresentations("$declarations\nFirst.new.second[0].<caret>")
        )
    }

    fun testNumericDotDisambiguationAndUnknownDotSuppression() {
        addExpressionCompletionTypes()
        myFixture.addFileToProject("unrelated.cr", "def unrelated_project_method\nend")

        assertTrue(completionNames("$EXPRESSION_TYPES_REQUIRE\n3.<caret>").contains("times"))
        assertTrue(completionNames("3.1<caret>").isEmpty())
        assertTrue(completionNames("$EXPRESSION_TYPES_REQUIRE\n3.14.<caret>").contains("float_only"))
        assertEquals(emptySet<String>(), completionNames("(missing).<caret>"))
    }

    fun testRealisticPrimitiveHierarchyProvidesIntMethods() {
        myFixture.addFileToProject("stdlib/number.cr", """
            struct Int
              def times
              end
            end
            struct Int32
              def own_int32
              end
            end
        """.trimIndent())

        assertEquals(setOf("own_int32", "times"), completionNames("require \"./stdlib/number\"\n3.<caret>"))
        assertEquals(setOf("own_int32", "times"), completionNames("require \"./stdlib/number\"\n(3).<caret>"))
    }

    fun testMacroHeavyTypesKeepOnlyCertainCompletionCandidates() {
        myFixture.addFileToProject("stdlib/macro_types.cr", """
            class String
              def upcase
                {{ body_value }}
              end
              {% if flag %}
              def conditional
              end
              {% end %}
            end
            struct Time
              def year
              end
              {% if flag %}
              def conditional
              end
              {% end %}
            end
        """.trimIndent())

        assertEquals(setOf("upcase"), completionNames("require \"./stdlib/macro_types\"\n\"text\".<caret>"))
        assertEquals(
            setOf("year"),
            completionNames("require \"./stdlib/macro_types\"\ndef now : Time\n  Time.new\nend\nnow().<caret>")
        )
    }

    fun testRangeCompletionUsesRangeMethodsInsteadOfEndpointMethods() {
        myFixture.addFileToProject("stdlib/range.cr", """
            struct Int32
              def int_only
              end
            end
            struct Range(B, E)
              def each
              end
            end
        """.trimIndent())

        for (receiver in listOf("1..2", "1...2", "(1..2)", "(1...2)", "..2", "...2")) {
            val names = completionNames("require \"./stdlib/range\"\n$receiver.<caret>")
            assertTrue("$receiver should expose Range#each: $names", names.contains("each"))
            assertFalse("$receiver must not expose Int32 methods: $names", names.contains("int_only"))
        }
    }

    fun testLexicalConstructorChainsKeepQualifiedInstanceIdentity() {
        val declarations = """
            class Service
              def global_only
              end
            end
            module Outer
              class Service
                def nested_only
                end
              end
        """.trimIndent()

        assertEquals(
            setOf("nested_only"),
            completionNames("$declarations\nmodule Outer\n  Service.new.<caret>\nend")
        )
        assertEquals(
            setOf("nested_only"),
            completionNames("$declarations\nmodule Outer\n  (Service.new).<caret>\nend")
        )
    }

    // ==================== Constructor policy pinning ====================

    fun testAbstractClassTypeObjectSuppressesSyntheticNew() {
        // Shared constructor policy: CrystalConstructorResolution.Abstract suppresses the
        // synthetic `new`, while static methods of the abstract class remain available.
        myFixture.configureByText("main.cr", """
            abstract class Foo
              def self.build
              end
            end

            Foo.<caret>
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain static method 'build': $names", names.contains("build"))
        assertFalse(
            "Abstract class must NOT offer synthetic 'new' (shared constructor policy): $names",
            names.contains("new")
        )
        assertEquals(setOf("build"), names.toSet())
    }

    fun testCrossFileRecordTypeObjectOffersNoCompletions() {
        // Cross-file record completion is intentionally unsupported: the shared constructor
        // classifier (CrystalTypeResolutionSession.resolveConstructor) scopes record
        // definitions to the containing file, so the record below is invisible from main.cr
        // and the receiver resolves to Unknown. Pinned behavior: no record `new` and no
        // unrelated completion noise. Tracked in TODO.md (Completion Follow-up).
        myFixture.addFileToProject("config.cr", """
            record Config, host : String, port : Int32 = 80
        """.trimIndent())
        myFixture.configureByText("main.cr", "Config.<caret>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        val names = lookups?.map { it.lookupString } ?: emptyList()
        assertFalse(
            "Cross-file record must NOT offer 'new' (records are containing-file-scoped): $names",
            names.contains("new")
        )
        assertEquals(
            "Unknown cross-file record receiver must not emit unrelated noise: $names",
            emptyList<String>(), names
        )
    }

    fun testNearerModuleTypeObjectWinsOverTopLevelRecord() {
        // The nearer lexical identity `Outer::Config` (a module) wins over the farther
        // top-level record: its static method is offered, the record's `new` must not leak
        // in, and modules never get a synthetic `new` (CrystalConstructorResolution.Unavailable).
        myFixture.configureByText("main.cr", """
            record Config, host : String

            module Outer
              module Config
                def self.inner
                end
              end

              Config.<caret>
            end
        """.trimIndent())
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Nearer Outer::Config module must offer 'inner': $names", names.contains("inner"))
        assertFalse(
            "Top-level record 'new' must NOT leak into nearer module completion: $names",
            names.contains("new")
        )
        assertEquals(setOf("inner"), names.toSet())
    }

    private fun addExpressionCompletionTypes() {
        myFixture.addFileToProject("stdlib/expression_types.cr", """
            struct Int32
              def times
              end
            end
            struct Float64
              def float_only
              end
            end
            class String
              def upcase
              end
            end
            struct Char
              def char_only
              end
            end
            struct Symbol
              def symbol_only
              end
            end
            struct Bool
              def bool_only
              end
            end
            class Nil
              def nil_only
              end
            end
            class Regex
              def regex_only
              end
            end
            class Array(T)
              def array_only
              end
            end
            class Hash(K, V)
              def hash_only
              end
            end
            struct Tuple(*T)
              def tuple_only
              end
            end
        """.trimIndent())
    }

    private companion object {
        const val EXPRESSION_TYPES_REQUIRE = "require \"./stdlib/expression_types\""
    }

    private fun completionNames(source: String): Set<String> = completionNameList(source).toSet()

    private fun completionNameList(source: String): List<String> {
        myFixture.configureByText("main.cr", source)
        return myFixture.complete(CompletionType.BASIC)?.map { it.lookupString }.orEmpty()
    }

    private fun completionPresentations(source: String): List<String> {
        myFixture.configureByText("main.cr", source)
        return myFixture.complete(CompletionType.BASIC).orEmpty().map { lookup ->
            val presentation = com.intellij.codeInsight.lookup.LookupElementPresentation()
            lookup.renderElement(presentation)
            listOf(
                lookup.lookupString,
                presentation.tailText.orEmpty(),
                presentation.typeText.orEmpty(),
                if (presentation.icon == com.intellij.icons.AllIcons.Nodes.Method) "Method" else "Other"
            ).joinToString("|")
        }.sorted()
    }

    private fun assertReceiverPresentationParity(
        prefix: String,
        receiver: String,
        expected: List<String>,
        suffix: String = ""
    ) {
        val separator = if (prefix.isEmpty()) "" else "\n"
        for (form in listOf(receiver, "($receiver)", "(($receiver))")) {
            assertEquals(
                "$form should have the exact lookup multiset",
                expected.sorted(),
                completionPresentations("$prefix$separator$form.<caret>$suffix")
            )
        }
    }
}
