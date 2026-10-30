package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.*
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
import de.magynhard.crystal.inspections.*
import de.magynhard.crystal.psi.CrystalDotCallAccess

class CrystalRecordReaderTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.addFileToProject("token.cr", """
            class Object
            end
            struct Value
            end
            struct Struct < Value
            end
            struct Enum < Value
            end
            struct Bool < Value
            end
            module Crystal
              class Token
                enum DelimiterKind
                  STRING
                  STRING_ARRAY
                  HEREDOC
                  def array? : Bool
                    false
                  end
                end
                record DelimiterState, kind : DelimiterKind, end : Char, payload
                struct DelimiterState
                  def marker : Int32
                    0
                  end
                end
                property delimiter_state : DelimiterState
              end
              record Header, name : String
            end
        """.trimIndent())
    }

    private fun configure(expression: String, required: Boolean = true) {
        val file = myFixture.configureByText("signature_help.cr", """
            ${if (required) "require \"./token\"" else ""}
            def consume(token : Crystal::Token)
              $expression
            end
        """.trimIndent())
        if (!expression.contains(".<caret>")) {
            assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
        }
    }

    fun testRecordReaderResolvesThroughReopeningAndCallChain() {
        configure("token.delimiter_state.kind.heredoc?")
        val accesses = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
        val kind = accesses.single { CrystalCallExtractor.extractMethodName(it) == "kind" }
        assertTrue(CrystalDotCallTargetResolver.resolve(kind) is DotCallResolution.Accessor)
        val heredoc = accesses.single { CrystalCallExtractor.extractMethodName(it) == "heredoc?" }
        assertTrue(CrystalDotCallTargetResolver.resolve(heredoc) is DotCallResolution.Accessor)
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
        assertFalse(myFixture.doHighlighting().any { it.description == "Cannot find 'kind'" })
    }

    fun testCompletionIncludesRecordReadersAndWrittenReopeningMethods() {
        configure("token.delimiter_state.<caret>")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Expected record fields and reopening method among $names", names.containsAll(listOf("kind", "end", "payload", "marker")))
        assertFalse("kind=" in names)
    }

    fun testCompletionAfterKindOffersGeneratedEnumPredicates() {
        configure("token.delimiter_state.kind.<caret>")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Expected enum predicates among $names", names.containsAll(listOf("heredoc?", "string?", "string_array?", "array?")))
    }

    fun testEnumCompletionInConditionAndPostfixUnless() {
        for (expression in listOf(
            "if token.delimiter_state.kind.<caret>\ntrue\nend",
            "consume_literal_body(token.delimiter_state) unless token.delimiter_state.kind.<caret>",
        )) {
            configure(expression)
            val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
            assertTrue("Expected heredoc? in $expression, got $names", "heredoc?" in names)
        }
    }

    fun testEnumPredicateHoverAndNavigation() {
        configure("token.delimiter_state.kind.he<caret>redoc?")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = CrystalDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        val doc = provider.generateDoc(target, leaf).orEmpty()
        assertTrue(doc, doc.contains("heredoc?") && doc.contains("Bool"))
        val access = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "heredoc?" }
        val navigation = access.reference!!.resolve()!!.navigationElement
        assertEquals("token.cr", navigation.containingFile.name)
        assertEquals("HEREDOC", navigation.text)
        assertEquals(listOf("Bool"), (CrystalTypeSetResolver.session(access).resolveCall(
            listOf("Crystal::Token::DelimiterKind"), "heredoc?", false, access,
        ) as CrystalTypeResolution.Known).types.map { it.name })
    }

    fun testHoverUsesRecordFieldTypeAndNavigationUsesOriginalField() {
        configure("token.delimiter_state.ki<caret>nd")
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = CrystalDocumentationProvider()
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        val doc = provider.generateDoc(target, leaf).orEmpty()
        assertTrue(doc, doc.contains("kind") && doc.contains("DelimiterKind"))
        assertFalse(doc, doc.contains("Any") || doc.contains("Cannot find"))
        val access = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "kind" }
        val navigation = access.reference!!.resolve()!!.navigationElement
        assertEquals("token.cr", navigation.containingFile.name)
        assertTrue(navigation.text.startsWith("kind"))
    }

    fun testRecordWithoutReopeningSuppliesCompletion() {
        configure("end\ndef header(value : Crystal::Header)\nvalue.<caret>")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Expected name among $names", "name" in names)
    }

    fun testUnrequiredRecordCannotSupplyMembers() {
        configure("token.delimiter_state.<caret>", required = false)
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertFalse("kind" in names)
    }

    fun testUntypedRecordReaderDoesNotInventNilReturnType() {
        configure("token.delimiter_state.payload")
        val access = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "payload" }
        assertTrue(CrystalDotCallTargetResolver.resolve(access) is DotCallResolution.Accessor)
        assertEquals(CrystalTypeResolution.Unknown, CrystalTypeSetResolver.session(access).resolveCall(
            listOf("Crystal::Token::DelimiterState"), "payload", false, access,
        ))
    }

    fun testUnrelatedSameNamedRecordCannotSupplyField() {
        myFixture.addFileToProject("unrelated.cr", "module Other\nrecord DelimiterState, missing : String\nend")
        configure("token.delimiter_state.missing")
        val access = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "missing" }
        assertEquals(DotCallResolution.Suppressed, CrystalDotCallTargetResolver.resolve(access))
    }

    fun testRecordReadersAreInstanceOnly() {
        configure("Crystal::Token::DelimiterState.kind")
        val access = PsiTreeUtil.findChildOfType(myFixture.file, CrystalDotCallAccess::class.java)!!
        assertEquals(DotCallResolution.Unresolved, CrystalDotCallTargetResolver.resolve(access))
    }

    fun testDuplicateRecordDeclarationsSuppressFieldLookup() {
        myFixture.addFileToProject("duplicate.cr", "module Crystal\nclass Token\nrecord DelimiterState, kind : String\nend\nend")
        configure("token.delimiter_state.kind")
        // The duplicate must be part of this program before it can make the identity ambiguous.
        myFixture.configureByText("signature_help.cr", myFixture.file.text.replace(
            "require \"./token\"", "require \"./token\"\nrequire \"./duplicate\"",
        ))
        val access = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "kind" }
        assertEquals(DotCallResolution.Suppressed, CrystalDotCallTargetResolver.resolve(access))
    }

    fun testSameFileRecordCompletion() {
        myFixture.configureByText("local.cr", "record LocalState, kind : String\ndef consume(value : LocalState)\nvalue.<caret>\nend")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Expected kind among $names", "kind" in names)
    }

    fun testUnmodeledRecordMacroMethodsRemainSuppressed() {
        configure("token.delimiter_state.copy_with(kind: nil)")
        val access = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "copy_with" }
        assertEquals(DotCallResolution.Suppressed, CrystalDotCallTargetResolver.resolve(access))
    }

    fun testMacroControlledRecordReadersRemainSuppressed() {
        myFixture.addFileToProject("conditional.cr", "{% if flag %}\nrecord ConditionalState, kind : String\n{% end %}")
        myFixture.configureByText("conditional_use.cr", "require \"./conditional\"\ndef consume(value : ConditionalState)\nvalue.kind\nend")
        val access = PsiTreeUtil.findChildOfType(myFixture.file, CrystalDotCallAccess::class.java)!!
        assertEquals(DotCallResolution.Suppressed, CrystalDotCallTargetResolver.resolve(access))
    }
}
