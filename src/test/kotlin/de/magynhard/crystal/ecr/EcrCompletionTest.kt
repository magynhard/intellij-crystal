package de.magynhard.crystal.ecr

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests that Crystal code completion works inside `<% %>` tags in ECR templates.
 *
 * Verifies that [CrystalEcrInjector] successfully injects `CrystalLanguage` into
 * `ecrBody` PSI elements, enabling full Crystal code intelligence (completion,
 * navigation, etc.) within ECR template tags.
 */
class EcrCompletionTest : BasePlatformTestCase() {

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

    fun testDotCompletesTypeObjectInsideInjectedCrystal() {
        myFixture.addFileToProject("service.cr", """
            class Service
              def self.build
              end
            end
        """.trimIndent())

        assertEquals(setOf("build", "new"), completionNames("<% Service.<caret> %>"))
    }

    fun testDotCompletesGroupedLiteralInsideInjectedCrystal() {
        myFixture.addFileToProject("int.cr", """
            struct Int32
              def times
              end
            end
        """.trimIndent())

        assertEquals(setOf("times"), completionNames("<% ((3)).<caret> %>"))
    }

    fun testUnknownDotReceiverIsSuppressedInsideInjectedCrystal() {
        myFixture.addFileToProject("helpers.cr", "def unrelated_project_method\nend")

        assertEquals(emptySet<String>(), completionNames("<% (missing).<caret> %>"))
    }

    private fun completionNames(source: String): Set<String> {
        myFixture.configureByText("dot.ecr", source)
        return myFixture.complete(CompletionType.BASIC)?.map { it.lookupString }.orEmpty().toSet()
    }
}
