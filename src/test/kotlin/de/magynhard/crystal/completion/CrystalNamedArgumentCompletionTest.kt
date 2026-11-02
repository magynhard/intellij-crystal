package de.magynhard.crystal.completion

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver
import java.io.File

class CrystalNamedArgumentCompletionTest : BasePlatformTestCase() {
    private var autoComplete = false
    override fun getTestDataPath(): String = "src/test/testData"

    override fun setUp() {
        super.setUp()
        autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.copyFileToProject("analysis/constructor_initializer.cr", "initializer.cr")
        myFixture.addFileToProject("items.cr", """
            require "./initializer"
            module LSP
              struct CompletionItem
                include Initializer
                property label : String
                property kind : CompletionItemKind?
                property detail : String?
                property documentation : (String | MarkupContent)?
                property rank : Int32 = 0
                getter enabled : Bool = true
                @internal : String
                class_property global : String
                struct Nested
                  property unrelated : String
                end
              end
            end
        """.trimIndent())
    }

    override fun tearDown() {
        try {
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
        } finally {
            super.tearDown()
        }
    }

    private fun complete(source: String): List<LookupElement> {
        myFixture.configureByText("test.cr", source.trimIndent())
        return myFixture.complete(CompletionType.BASIC).orEmpty().toList()
    }

    private fun keys(source: String): Set<String> = complete(source).map { it.lookupString }.filter { it.endsWith(':') }.toSet()

    fun testNormalConstructorUsesParametersNotProperties() {
        val names = keys("""
            struct Person
              property name : String
              property active : Bool = true
              def initialize(@name : String)
              end
            end
            Person.new(<caret>)
        """)
        assertEquals(setOf("name:"), names)
    }

    fun testExternalNamesAndNamedOnlyParameters() {
        assertEquals(setOf("display_name:", "active:"), keys("""
            struct Person
              def initialize(display_name @name : String, *, active : Bool = true, **options, &block)
              end
            end
            Person.new(<caret>)
        """))
    }

    fun testExplicitFactoryUsesItsOwnSignature() {
        assertEquals(setOf("source:"), keys("""
            class Factory
              def initialize(label : String)
              end
              def self.new(source : String) : String
                source
              end
            end
            Factory.new(<caret>)
        """))
    }

    fun testRecordFields() {
        assertEquals(setOf("label:", "kind:"), keys("""
            record Item, label : String, kind : Int32 = 0
            Item.new(<caret>)
        """))
    }

    fun testTopLevelMethodLabels() {
        assertEquals(setOf("label:", "detail:"), keys("""
            def render(label : String, detail : String? = nil)
            end
            render(<caret>)
        """))
    }

    fun testInstanceMethodLabels() {
        assertEquals(setOf("label:", "detail:"), keys("""
            class Renderer
              def render(label : String, detail : String? = nil)
              end
            end
            def run(renderer : Renderer)
              renderer.render(<caret>)
            end
        """))
    }

    fun testImplicitSelfMethodLabels() {
        assertEquals(setOf("label:", "detail:"), keys("""
            class Renderer
              def render(label : String, detail : String? = nil)
              end
              def run
                render(<caret>)
              end
            end
        """))
    }

    fun testBareCallLabels() {
        assertEquals(setOf("label:", "detail:"), keys("""
            def render(label : String, detail : String? = nil)
            end
            render <caret>
        """))
    }

    fun testMacroInitializerOffersTypedFieldsIncludingNonProperties() {
        val items = complete("require \"./items\"\nLSP::CompletionItem.new(\n  <caret>\n)")
        val names = items.map { it.lookupString }.filter { it.endsWith(':') }.toSet()
        assertEquals(setOf("label:", "kind:", "detail:", "documentation:", "rank:", "enabled:", "internal:"), names)
        val label = LookupElementPresentation().also { items.single { it.lookupString == "label:" }.renderElement(it) }
        val kind = LookupElementPresentation().also { items.single { it.lookupString == "kind:" }.renderElement(it) }
        assertEquals("String", label.typeText)
        assertEquals("CompletionItemKind?", kind.typeText)
        assertTrue(label.tailText.orEmpty().contains("required"))
        assertFalse(kind.tailText.orEmpty().contains("required"))
        assertTrue(items.indexOfFirst { it.lookupString == "label:" } < items.indexOfFirst { it.lookupString == "kind:" })
    }

    fun testPrefixCompletionAndInsertion() {
        val items = complete("require \"./items\"\nLSP::CompletionItem.new(det<caret>)")
        val detail = items.single { it.lookupString == "detail:" }
        myFixture.lookup.currentItem = detail
        myFixture.finishLookup('\n')
        myFixture.checkResult("require \"./items\"\nLSP::CompletionItem.new(detail: <caret>)")
    }

    fun testInsertionDoesNotDuplicateExistingColon() {
        val items = complete("require \"./items\"\nLSP::CompletionItem.new(det<caret>: nil)")
        myFixture.lookup.currentItem = items.single { it.lookupString == "detail:" }
        myFixture.finishLookup('\n')
        myFixture.checkResult("require \"./items\"\nLSP::CompletionItem.new(detail: <caret>nil)")
    }

    fun testUsedKeysBeforeAndAfterCaretAreExcluded() {
        assertEquals(setOf("kind:", "documentation:", "rank:", "enabled:", "internal:"), keys(
            "require \"./items\"\nLSP::CompletionItem.new(label: \"x\", <caret>, detail: nil)"
        ))
    }

    fun testPositionalArgumentsConsumeParameters() {
        assertEquals(setOf("kind:"), keys("""
            record Item, label : String, kind : Int32 = 0
            Item.new("x", <caret>)
        """))
    }

    fun testIncompatibleOverloadDoesNotLeakLabels() {
        assertEquals(setOf("detail:"), keys("""
            def render(label : String, detail : String? = nil)
            end
            def render(count : Int32, active : Bool = true)
            end
            render(label: "x", <caret>)
        """))
    }

    fun testNoKeysInsideArgumentValuesOrNestedExpressions() {
        for (value in listOf("label: <caret>", "label: foo + <caret>", "[<caret>]", "{key: <caret>}")) {
            assertEquals(value, emptySet<String>(), keys("require \"./items\"\nLSP::CompletionItem.new($value)"))
        }
    }

    fun testUnknownAndUnrequiredReceiversDoNotOfferKeys() {
        assertEquals(emptySet<String>(), keys("LSP::CompletionItem.new(<caret>)"))
        assertEquals(emptySet<String>(), keys("require \"./items\"\nunknown.new(<caret>)"))
    }

    fun testSameSimpleNameInDifferentNamespaceDoesNotLeakFields() {
        assertEquals(setOf("own:"), keys("""
            require "./items"
            module Other
              struct CompletionItem
                def initialize(own : String)
                end
              end
            end
            Other::CompletionItem.new(<caret>)
        """))
    }

    fun testPropertiesAndDoubleSplatAloneDoNotDefineKeys() {
        assertEquals(emptySet<String>(), keys("""
            struct Item
              property label : String
              def self.new(**args)
                nil
              end
            end
            Item.new(<caret>)
        """))
    }

    fun testDifferentInitializerBodyDoesNotGainFieldKeys() {
        val source = myFixture.findFileInTempDir("initializer.cr").contentsToByteArray().toString(Charsets.UTF_8)
        myFixture.addFileToProject("other_initializer.cr", source.replace("module LSP", "module Other")
            .replace("args[\"{{ivar.id}}\"]", "args[\"fixed\"]"))
        assertEquals(emptySet<String>(), keys("""
            require "./other_initializer"
            struct Item
              include Other::Initializer
              property label : String
            end
            Item.new(<caret>)
        """))
    }

    fun testOverriddenInitializerDoesNotGainFieldKeys() {
        assertEquals(setOf("args:"), keys("""
            require "./items"
            struct LSP::CompletionItem
              private def initialize(args : NamedTuple)
              end
            end
            LSP::CompletionItem.new(<caret>)
        """))
    }

    fun testPositionalArgumentConsumesMethodParameterButNotNamedOnlyParameter() {
        assertEquals(setOf("active:"), keys("""
            def render(label : String, *, active : Bool)
            end
            render("x", <caret>)
        """))
    }

    fun testQuotedExternalParameterNameIsInsertedAsQuotedKey() {
        val items = complete("""
            def render("http-header" value : String, detail : String? = nil)
            end
            render(<caret>)
        """)
        myFixture.lookup.currentItem = items.single { it.lookupString == "\"http-header\":" }
        myFixture.finishLookup('\n')
        assertTrue(myFixture.editor.document.text.endsWith("render(\"http-header\": )"))
    }

    fun testUnderscoreAndVariadicParametersAreNotLabels() {
        assertEquals(setOf("label:"), keys("""
            def render(_ hidden : String, *values, label : String, **options, &block)
            end
            render(<caret>)
        """))
    }

    fun testUnknownSplatDoesNotGuessWhichParametersRemain() {
        assertEquals(emptySet<String>(), keys("""
            def render(label : String, detail : String? = nil)
            end
            render(**options, <caret>)
        """))
    }

    fun testUnrequiredTopLevelMethodDoesNotContributeLabels() {
        myFixture.addFileToProject("unrequired.cr", "def render(unrelated : String)\nend")
        assertEquals(emptySet<String>(), keys("render(<caret>)"))
    }

    fun testLocalShadowsTopLevelMethod() {
        assertEquals(emptySet<String>(), keys("""
            def render(label : String)
            end
            render = ->(value : String) { value }
            render(<caret>)
        """))
    }

    fun testMacroOverloadPoolDoesNotBorrowMethodLabels() {
        assertEquals(emptySet<String>(), keys("""
            macro render(**args)
            end
            def render(label : String)
            end
            render(<caret>)
        """))
    }

    fun testNestedCallUsesItsOwnLabelsInsteadOfOuterKeys() {
        assertEquals(setOf("value:"), keys("""
            require "./items"
            def render(value : String)
            end
            LSP::CompletionItem.new(label: render(<caret>))
        """))
    }

    fun testRequiredAndOptionalFieldRankingUsesTopLevelNilabilityAndDefaults() {
        val items = complete("""
            require "./initializer"
            struct Item
              include LSP::Initializer
              property required : Array(String | Nil)
              property optional : (String | Nil)
              property defaulted : Bool = false
            end
            Item.new(<caret>)
        """)
        for (name in listOf("required:", "optional:", "defaulted:")) {
            val presentation = LookupElementPresentation().also { items.single { it.lookupString == name }.renderElement(it) }
            assertEquals(name, name == "required:", presentation.tailText.orEmpty().contains("required"))
        }
    }

    fun testGenericMacroConstructorKeepsKnownFieldNames() {
        assertEquals(setOf("value:"), keys("""
            require "./initializer"
            struct Box(T)
              include LSP::Initializer
              property value : T
            end
            Box(Int32).new(<caret>)
        """))
    }

    fun testOverriddenAllocatorDoesNotGainFieldKeys() {
        assertEquals(emptySet<String>(), keys("""
            require "./items"
            struct LSP::CompletionItem
              def self.allocate : String
                "other"
              end
            end
            LSP::CompletionItem.new(<caret>)
        """))
    }

    fun testRequiredReopeningContributesFieldsButUnrequiredReopeningDoesNot() {
        myFixture.addFileToProject("visible.cr", "require \"./items\"\nstruct LSP::CompletionItem\nproperty visible : String?\nend")
        myFixture.addFileToProject("unrequired.cr", "struct LSP::CompletionItem\nproperty hidden : String?\nend")
        val names = keys("require \"./visible\"\nLSP::CompletionItem.new(<caret>)")
        assertTrue(names.toString(), "visible:" in names)
        assertFalse(names.toString(), "hidden:" in names)
    }

    fun testAdditionalJsonShapedConstructorDoesNotHideFieldKeys() {
        val names = keys("""
            require "./initializer"
            module JsonFactory
              macro included
                def self.new(pull : String)
                  instance = allocate
                  instance
                end
              end
            end
            struct Item
              include LSP::Initializer
              include JsonFactory
              property label : String
              property kind : Int32?
              property detail : String?
            end
            Item.new(<caret>)
        """)
        assertTrue(names.toString(), names.containsAll(setOf("label:", "kind:", "detail:", "pull:")))
    }

    fun testRealJsonSerializableHookAndCompleteInitializerOfferFieldKeys() {
        val root = File("/usr/lib/crystal")
        val paths = listOf("object.cr", "value.cr", "struct.cr", "reference.cr", "string.cr", "json/serialization.cr")
        if (paths.any { !File(root, it).isFile }) return
        for (path in paths) myFixture.addFileToProject("sdk/$path", File(root, path).readText())
        val requires = paths.joinToString("\n") { "require \"./sdk/${it.removeSuffix(".cr")}\"" }
        val names = keys("""
            $requires
            require "./initializer"
            struct Item
              include LSP::Initializer
              include JSON::Serializable
              property label : String
              property kind : Int32?
              property detail : String?
            end
            Item.new(<caret>)
        """)
        assertTrue(names.toString(), names.containsAll(setOf("label:", "kind:", "detail:")))
    }

    fun testCompetingOpaqueGeneratedInitializerDoesNotGainFieldKeys() {
        assertEquals(emptySet<String>(), keys("""
            require "./initializer"
            module OtherInitializer
              macro included
                private def initialize(args : NamedTuple)
                  @label = "fixed"
                end
              end
            end
            struct Item
              include LSP::Initializer
              include OtherInitializer
              property label : String
            end
            Item.new(<caret>)
        """))
    }

    fun testEditingInitializerInvalidatesKnownFieldKeys() {
        val source = "require \"./items\"\nLSP::CompletionItem.new(<caret>)"
        assertTrue("label:" in keys(source))
        val file = myFixture.findFileInTempDir("initializer.cr")
        val psi = com.intellij.psi.PsiManager.getInstance(project).findFile(file)!!
        WriteCommandAction.runWriteCommandAction(project) {
            val manager = PsiDocumentManager.getInstance(project)
            val document = manager.getDocument(psi)!!
            document.setText(document.text.replace("@type.instance_vars", "@type.methods"))
            manager.commitDocument(document)
        }
        assertEquals(emptySet<String>(), keys(source))
    }

    fun testColonAcceptCharacterInsertsExactlyOneSeparator() {
        val items = complete("require \"./items\"\nLSP::CompletionItem.new(det<caret>)")
        myFixture.lookup.currentItem = items.single { it.lookupString == "detail:" }
        myFixture.finishLookup(':')
        myFixture.checkResult("require \"./items\"\nLSP::CompletionItem.new(detail: <caret>)")
    }
}
