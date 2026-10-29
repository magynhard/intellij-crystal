package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import de.magynhard.crystal.analysis.*
import de.magynhard.crystal.documentation.CrystalDocumentationProvider
import de.magynhard.crystal.inspections.CrystalCallExtractor
import de.magynhard.crystal.inspections.CrystalDotCallTargetResolver
import de.magynhard.crystal.inspections.CrystalUnresolvedNameInspection
import de.magynhard.crystal.inspections.DotCallResolution
import de.magynhard.crystal.psi.CrystalDotCallAccess
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.function.BooleanSupplier

class CrystalFinishedDeclarationsTest : BasePlatformTestCase() {
    private val declarations = """
        class Object
        end
        module LSP
          class RequestMessage(Result) < Object
            getter id : Int32
            getter payload : Result
          end
          class DocumentFormattingParams < Object
            property text_document : String
          end
          macro finished
            class DocumentFormattingRequest < RequestMessage(Nil)
              @method = "textDocument/formatting"
              property params : DocumentFormattingParams
              getter optional_params : DocumentFormattingParams?
              class_getter default_params : DocumentFormattingParams
            end
          end
        end
    """.trimIndent()

    override fun setUp() {
        super.setUp()
        CrystalRequireGraphService.installForTests(project, CrystalRequirePathResolver(project) { null }, testRootDisposable)
        myFixture.addFileToProject("requests.cr", declarations)
    }

    private fun controller(expression: String, required: Boolean = true) {
        myFixture.configureByText("controller.cr", """
            ${if (required) "require \"./requests\"" else ""}
            def on_request(message : LSP::RequestMessage)
              case message
              when LSP::DocumentFormattingRequest
                $expression
              end
            end
        """.trimIndent())
    }

    private fun paramsCall(): CrystalDotCallAccess =
        PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "params" }

    fun testTypedPropertyResolvesAndNavigatesToOriginalMacroBody() {
        controller("message.par<caret>ams")
        val call = paramsCall()
        val resolution = CrystalDotCallTargetResolver.resolve(call)
        assertTrue("Expected a real accessor, got $resolution", resolution is DotCallResolution.Accessor)
        val target = call.reference!!.resolve()!!
        assertFalse("A generated PSI view must not pretend to be an editable declaration", target.isWritable)
        val navigation = target.navigationElement
        assertEquals("requests.cr", navigation.containingFile.name)
        assertEquals(declarations.indexOf("params : DocumentFormattingParams"), navigation.textOffset)
        val result = CrystalTypeSetResolver.session(call).resolveCall(
            listOf("LSP::DocumentFormattingRequest"), "params", false, call,
        )
        assertEquals("LSP::DocumentFormattingParams", result.render())
    }

    fun testCompletionOffersTypedParams() {
        controller("message.pa<caret>")
        val variants = myFixture.complete(CompletionType.BASIC).orEmpty()
        assertTrue("Expected params among ${variants.map { it.lookupString }}", variants.any { it.lookupString == "params" })
    }

    fun testGeneratedSubclassKeepsInheritedAccessorBindings() {
        controller("message.id")
        val call = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java).single()
        val result = CrystalDotCallTargetResolver.resolve(call)
        assertTrue("Inherited generic-base accessor must remain known, got $result", result is DotCallResolution.Accessor)
        val target = call.reference!!.resolve()!!
        assertEquals("requests.cr", target.containingFile.name)
        assertEquals(declarations.indexOf("id : Int32"), target.textOffset)
        assertTrue("Ordinary physical accessor rename behavior must remain available", target.isWritable)
    }

    fun testGenericAccessorBindingDoesNotResolveThroughUnrelatedTypeParameterName() {
        myFixture.addFileToProject("result.cr", "class Result < Object; end")
        controller("message.payload")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(0, "require \"./result\"\n")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        val call = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java).single()
        assertTrue(CrystalDotCallTargetResolver.resolve(call) is DotCallResolution.Accessor)
        assertTrue(CrystalTypeSetResolver.session(call).resolveCall(
            listOf("LSP::DocumentFormattingRequest"), "payload", false, call,
        ) is CrystalTypeResolution.Unknown)
    }

    fun testHoverUsesAccessorSignatureInsteadOfAnyVariable() {
        controller("message.par<caret>ams")
        val provider = CrystalDocumentationProvider()
        val leaf = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val target = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, leaf, myFixture.caretOffset)
        val doc = provider.generateDoc(target, leaf).orEmpty()
        assertTrue("Expected accessor signature, got $doc", doc.contains("params") && doc.contains("DocumentFormattingParams"))
        assertFalse(doc.contains("Any"))
    }

    fun testAccessorResultSupportsChainedTypeResolutionAndCompletion() {
        controller("message.params.text_document")
        val outer = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "text_document" }
        assertTrue("Chained navigation must retain the params return type",
            CrystalDotCallTargetResolver.resolve(outer) is DotCallResolution.Accessor)
        // The fixture has no indexed String declaration; the first accessor's
        // return still has its exact LSP identity for the subsequent lookup.
        assertEquals("LSP::DocumentFormattingParams", CrystalTypeSetResolver.session(outer).resolveCall(
            listOf("LSP::DocumentFormattingRequest"), "params", false, outer,
        ).render())
        controller("message.params.te<caret>")
        val variants = myFixture.complete(CompletionType.BASIC).orEmpty()
        assertTrue("Expected text_document among ${variants.map { it.lookupString }}", variants.any { it.lookupString == "text_document" })
    }

    fun testClassAccessorChainKeepsStaticModeThroughGrouping() {
        myFixture.configureByText("main.cr", "require \"./requests\"\n(LSP::DocumentFormattingRequest).default_params.text_document")
        val outer = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
            .single { CrystalCallExtractor.extractMethodName(it) == "text_document" }
        assertTrue(CrystalDotCallTargetResolver.resolve(outer) is DotCallResolution.Accessor)
    }

    fun testNilableOrArgumentBearingAccessorCannotProveChainedReceiver() {
        for (access in listOf("params(1)", "optional_params")) {
            controller("message.$access.text_document")
            val outer = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalDotCallAccess::class.java)
                .single { CrystalCallExtractor.extractMethodName(it) == "text_document" }
            assertTrue("Uncertain predecessor $access must stay suppressed", CrystalDotCallTargetResolver.resolve(outer) is DotCallResolution.Suppressed)
        }
    }

    fun testUnrequiredHookCannotProvideTypeOrCompletion() {
        controller("message.params", required = false)
        assertNull(CrystalTypeSetResolver.session(myFixture.file).resolveType("LSP::DocumentFormattingRequest", myFixture.file))
        assertEmpty(de.magynhard.crystal.completion.CrystalCompletionHelper.getMethodsAsLookups(
            "LSP::DocumentFormattingRequest", myFixture.file,
        ))
    }

    fun testIndependentFinishedHooksContributeTheirOwnClasses() {
        myFixture.addFileToProject("second.cr", """
            module LSP
              macro finished
                class HoverRequest < RequestMessage(Nil)
                  getter params : DocumentFormattingParams
                end
              end
            end
        """.trimIndent())
        myFixture.configureByText("main.cr", "require \"./requests\"\nrequire \"./second\"\n")
        val session = CrystalTypeSetResolver.session(myFixture.file)
        for (type in listOf("LSP::DocumentFormattingRequest", "LSP::HoverRequest")) {
            assertNotNull(session.resolveType(type, myFixture.file))
            assertEquals("LSP::DocumentFormattingParams", session.resolveCall(listOf(type), "params", false, myFixture.file).render())
        }
    }

    fun testConditionalAndGenericModuleHooksRemainUnresolved() {
        val source = myFixture.psiManager.findFile(myFixture.findFileInTempDir("requests.cr")!!)!!
        for (unsupported in listOf(
            declarations.replace("macro finished", "{% if true %}\nmacro finished").replace("\n  end\nend", "\n  end\n{% end %}\nend"),
            declarations.replace("module LSP", "module LSP(T)"),
        )) {
            WriteCommandAction.runWriteCommandAction(project) {
                PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(unsupported)
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
            controller("message.params")
            assertNull(CrystalTypeSetResolver.session(myFixture.file).resolveType("LSP::DocumentFormattingRequest", myFixture.file))
        }
    }

    fun testInterpolationAndMacroControlRejectTheWholeHook() {
        for (dynamic in listOf("{{ 1 }}", "{% if true %}\n{% end %}")) {
            val file = myFixture.findFileInTempDir("requests.cr")!!
            val psi = myFixture.psiManager.findFile(file)!!
            WriteCommandAction.runWriteCommandAction(project) {
                PsiDocumentManager.getInstance(project).getDocument(psi)!!.setText(
                    declarations.replace("@method =", "$dynamic\n@method ="),
                )
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
            controller("message.params")
            assertNull(CrystalTypeSetResolver.session(myFixture.file).resolveType("LSP::DocumentFormattingRequest", myFixture.file))
        }
    }

    fun testEditingPropertyInvalidatesTheStaticView() {
        controller("message.params")
        assertTrue(CrystalDotCallTargetResolver.resolve(paramsCall()) is DotCallResolution.Accessor)
        val oldNavigation = paramsCall().reference!!.resolve()!!.navigationElement
        val source = myFixture.psiManager.findFile(myFixture.findFileInTempDir("requests.cr")!!)!!
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(declarations.replace("property params", "property options"))
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        assertTrue(CrystalDotCallTargetResolver.resolve(paramsCall()) is DotCallResolution.Unresolved)
        assertFalse("A stale view must not navigate to a renamed source token", oldNavigation.isValid)
    }

    fun testParallelStaticViewsRemainEquivalent() {
        controller("message.params")
        val context = myFixture.file
        val sources = CrystalRequireGraphService.getInstance(project).effectiveSources(context)
        // Warm StubIndex separately; the race under test is cached view creation.
        de.magynhard.crystal.stubs.CrystalIndexService.findMacros(
            "finished", project, com.intellij.psi.search.GlobalSearchScope.allScope(project),
        )
        val pool = Executors.newFixedThreadPool(6)
        val start = CountDownLatch(1)
        try {
            val futures = (1..12).map {
                pool.submit<List<String>> {
                    start.await()
                    ReadAction.computeBlocking<List<String>, RuntimeException> {
                        CrystalStaticDeclarations.finishedTypes(context, sources).flatMap { type ->
                            CrystalStaticDeclarations.accessorMethods(type).mapNotNull { method -> method.name }
                        }.sorted()
                    }
                }
            }
            start.countDown()
            PlatformTestUtil.waitWithEventsDispatching("parallel finished/accessor views", BooleanSupplier { futures.all { it.isDone } }, 60)
            val results = futures.map { it.get() }
            assertTrue(results.first().contains("params"))
            assertEquals(1, results.distinct().size)
        } finally {
            pool.shutdownNow()
        }
    }

    fun testMissingMembersStillWarnOnTheResolvedGeneratedClass() {
        myFixture.enableInspections(CrystalUnresolvedNameInspection::class.java)
        controller("message.<warning descr=\"Cannot find 'missing'\">missing</warning>")
        myFixture.checkHighlighting()
    }
}
