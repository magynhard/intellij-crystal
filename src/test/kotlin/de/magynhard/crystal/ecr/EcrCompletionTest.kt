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
    private lateinit var fixtureRoot: VirtualFile

    override fun setUp() {
        super.setUp()
        fixtureRoot = myFixture.tempDirFixture.findOrCreateDir("")
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
        myFixture.addFileToProject("project_reopening.cr", "class String\n  def project_string_marker; end\nend")
        myFixture.addFileToProject("lib/kemal/src/kemal.cr", "struct Int\n  def shard_int_marker; end\nend")
        myFixture.addFileToProject(
            "reverse_reopening.cr",
            "require \"./dot\"\nclass Array(T)\n  def reverse_array_marker; end\nend",
        )
        myFixture.addFileToProject(
            "sibling_reopening.cr",
            "module Indexable(T)\n  def sibling_indexable_marker; end\nend",
        )
        myFixture.addFileToProject(
            "host_reopening.cr",
            "module Enumerable(T)\n  def host_enumerable_marker; end\nend",
        )

        assertEquals(
            setOf("upcase", "downcase"),
            completionNames("<% class String; def injected_host_marker; end; end\n\"text\".<caret> %>"),
        )
        assertEquals(setOf("times"), completionNames("<% ((3)).<caret> %>"))
        assertTrue(completionNames("<% [1, 2].<caret> %>").containsAll(setOf("each", "max", "sum")))
        assertTrue(completionNames("<% aaa = [1, 2, 3, 4]; aaa.<caret> %>").containsAll(setOf("each", "max")))
    }

    fun testProjectLibCannotShadowPreludeInsideInjectedCrystal() {
        installFixtureStdlib()
        addPreludeCollisions()

        assertEquals(setOf("upcase", "downcase"), completionNames("<% \"text\".<caret> %>"))
        assertEquals(setOf("times"), completionNames("<% 3.<caret> %>"))
        assertTrue(completionNames("<% [1, 2].<caret> %>").containsAll(setOf("each", "max", "sum")))
    }

    fun testEscapedPreludeDependencyRetainsStdlibOnlyResolutionInsideInjectedCrystal() {
        installEscapedFixtureStdlib()
        addPreludeCollisions()
        myFixture.addFileToProject(
            "lib/extensions/project.cr",
            "class String\n  def project_wildcard_shadow; end\nend",
        )

        assertEquals(
            setOf("upcase", "downcase", "stdlib_wildcard_method"),
            completionNames("<% \"text\".<caret> %>"),
        )
        assertEquals(setOf("times"), completionNames("<% 3.<caret> %>"))
        assertTrue(completionNames("<% [1, 2].<caret> %>").containsAll(setOf("each", "max", "sum")))
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
            """
            module Enumerable(T)
              abstract def each(& : T ->)
              def each; end
              def map(& : T -> U) forall U; end
              def select(& : T ->); end
              def min; end
              def max; end
              def sum; end
              def includes?(value); end
              def reduce(initial, &); end
              def any?(& : T ->); end
              def flat_map(& : T -> U) forall U; end
            end
            """.trimIndent(),
        ).virtualFile
        installFixtureGraph(requireNotNull(enumerable.parent))
    }

    private fun installEscapedFixtureStdlib() {
        myFixture.addFileToProject("fixture-stdlib/prelude.cr", "require \"../shared/core\"")
        myFixture.addFileToProject("shared/core.cr", """
            require "string"
            require "int"
            require "array"
            require "indexable"
            require "enumerable"
            require "extensions/*"
        """.trimIndent())
        myFixture.addFileToProject(
            "fixture-stdlib/string.cr",
            "class String\n  def upcase; end\n  def downcase; end\nend",
        )
        myFixture.addFileToProject("fixture-stdlib/int.cr", "struct Int\n  def times; end\nend\nstruct Int32\nend")
        myFixture.addFileToProject("fixture-stdlib/array.cr", "class Array(T)\n  include Indexable(T)\nend")
        myFixture.addFileToProject(
            "fixture-stdlib/indexable.cr",
            "module Indexable(T)\n  include Enumerable(T)\nend",
        )
        myFixture.addFileToProject("fixture-stdlib/enumerable.cr", "module Enumerable(T)\n  def each; end\n  def max; end\n  def sum; end\nend")
        val wildcard = myFixture.addFileToProject(
            "fixture-stdlib/extensions/core.cr",
            "class String\n  def stdlib_wildcard_method; end\nend",
        ).virtualFile
        installFixtureGraph(requireNotNull(wildcard.parent.parent))
    }

    private fun addPreludeCollisions() {
        myFixture.addFileToProject("lib/string.cr", "class String\n  def project_string_shadow; end\nend")
        myFixture.addFileToProject("lib/int.cr", "struct Int\n  def project_int_shadow; end\nend")
        myFixture.addFileToProject(
            "lib/indexable.cr",
            "module Indexable(T)\n  def project_indexable_shadow; end\nend",
        )
    }

    private fun installFixtureGraph(stdlibRoot: VirtualFile?) {
        val previous = fixtureGraphDisposable
        val graphDisposable = Disposer.newDisposable("ECR completion fixture require graph")
        CrystalRequireGraphService.installForTests(
            project,
            CrystalRequirePathResolver(
                project,
                { stdlibRoot },
                { fixtureRoot },
            ),
            graphDisposable,
        )
        fixtureGraphDisposable = graphDisposable
        previous?.let(Disposer::dispose)
    }
}
