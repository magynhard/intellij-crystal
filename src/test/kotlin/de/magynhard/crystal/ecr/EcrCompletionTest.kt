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
}
