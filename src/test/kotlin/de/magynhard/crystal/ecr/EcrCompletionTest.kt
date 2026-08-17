package de.magynhard.crystal.ecr

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequirePathResolver

/**
 * Tests that Crystal code completion works inside `<% %>` tags in ECR templates.
 *
 * Verifies that [CrystalEcrInjector] successfully injects `CrystalLanguage` into
 * `ecrBody` PSI elements, enabling full Crystal code intelligence (completion,
 * navigation, etc.) within ECR template tags.
 */
class EcrCompletionTest : BasePlatformTestCase() {

    private var fixtureGraphDisposable: Disposable? = null

    override fun setUp() {
        super.setUp()
        installFixtureGraph(null)
    }

    override fun tearDown() {
        try {
            fixtureGraphDisposable?.let(Disposer::dispose)
            fixtureGraphDisposable = null
        } finally {
            super.tearDown()
        }
    }

    fun testCompletesStdlibTypesInsideEcrTags() {
        myFixture.configureByText("test.ecr", "<% x = Int<caret> %>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions inside ECR tags", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Int32, got: $names", names.contains("Int32"))
    }

    fun testCompletesClassNamesInsideEcrTags() {
        myFixture.addFileToProject("models.cr", "class User\nend\nclass Account\nend\n")
        myFixture.configureByText("template.ecr", "<% u = Us<caret> %>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions inside ECR tags", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain User, got: $names", names.contains("User"))
    }

    fun testCompletesInHtmlEcrFile() {
        myFixture.addFileToProject("models.cr", "class User\nend\n")
        myFixture.configureByText("index.html.ecr", "<div><% u = Us<caret> %></div>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions inside ECR tags in .html.ecr", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain User, got: $names", names.contains("User"))
    }

    fun testCompletesOutputTags() {
        myFixture.addFileToProject("models.cr", "class User\n  property name : String\nend\n")
        myFixture.configureByText("show.html.ecr", "<div><%= Us<caret> %></div>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions inside <%= %> output tags", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain User, got: $names", names.contains("User"))
    }

    fun testCompletesAcrossMultipleTags() {
        myFixture.configureByText("multi.ecr", "<% x = 1 %>\n<% y = x<caret> %>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions across multiple tags", lookups)
    }

    fun testCompletesWithTrimModifier() {
        myFixture.configureByText("trim.ecr", "<%- x = Int<caret> -%>")
        val lookups = myFixture.complete(CompletionType.BASIC)
        assertNotNull("Should return completions with trim modifier tags", lookups)
        val names = lookups.map { it.lookupString }
        assertTrue("Should contain Int32, got: $names", names.contains("Int32"))
    }

    fun testDotCompletionWithoutLoadContextIsSuppressedForTypeObjectInsideInjectedCrystal() {
        myFixture.addFileToProject("service.cr", """
            class Service
              def self.build
              end
            end
        """.trimIndent())

        assertEquals(emptySet<String>(), completionNames("<% Service.<caret> %>"))
    }

    fun testInjectedCrystalUsesOnlyPreludeForCoreLiteralCompletion() {
        installFixtureStdlib()

        assertContainsAll(completionNames("<% \"text\".<caret> %>"), "upcase", "downcase")
        assertContainsAll(completionNames("<% ((3)).<caret> %>"), "times")
        assertContainsAll(completionNames("<% [1, 2].<caret> %>"), "each")
    }

    fun testUnknownDotReceiverIsSuppressedInsideInjectedCrystal() {
        myFixture.addFileToProject("helpers.cr", "def unrelated_project_method\nend")

        assertEquals(emptySet<String>(), completionNames("<% (missing).<caret> %>"))
    }

    fun testRecordDeclaredInsideEcrFragmentDoesNotOfferNew() {
        assertEquals(
            "Record declared inside ECR fragment must NOT offer 'new'",
            emptySet<String>(),
            completionNames("<% record Config, host : String\nConfig.<caret> %>"),
        )
    }

    private fun completionNames(source: String): Set<String> {
        myFixture.configureByText("dot.ecr", source)
        return myFixture.complete(CompletionType.BASIC)?.map { it.lookupString }.orEmpty().toSet()
    }

    private fun installFixtureStdlib() {
        myFixture.addFileToProject("fixture-stdlib/prelude.cr", """
            require "string"
            require "int"
            require "array"
            require "indexable"
            require "enumerable"
        """.trimIndent())
        myFixture.addFileToProject("fixture-stdlib/string.cr", "class String\n  def upcase; end\n  def downcase; end\nend")
        myFixture.addFileToProject("fixture-stdlib/int.cr", "struct Int\n  def times; end\nend\nstruct Int32\nend")
        myFixture.addFileToProject("fixture-stdlib/array.cr", "class Array(T)\n  include Indexable(T)\nend")
        myFixture.addFileToProject("fixture-stdlib/indexable.cr", "module Indexable(T)\n  include Enumerable(T)\nend")
        val enumerable = myFixture.addFileToProject(
            "fixture-stdlib/enumerable.cr",
            "module Enumerable(T)\n  def each; end\nend",
        ).virtualFile
        installFixtureGraph(requireNotNull(enumerable.parent))
    }

    private fun installFixtureGraph(stdlibRoot: VirtualFile?) {
        fixtureGraphDisposable?.let(Disposer::dispose)
        val graphDisposable = Disposer.newDisposable("ECR completion fixture require graph")
        fixtureGraphDisposable = graphDisposable
        CrystalRequireGraphService.installForTests(
            project,
            CrystalRequirePathResolver(project, { stdlibRoot }),
            graphDisposable,
        )
    }

    private fun assertContainsAll(actual: Set<String>, vararg expected: String) {
        assertTrue("Expected ${expected.toSet()} in $actual", actual.containsAll(expected.toSet()))
    }
}
