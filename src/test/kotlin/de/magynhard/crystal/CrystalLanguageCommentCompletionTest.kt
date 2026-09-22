package de.magynhard.crystal

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.injection.CrystalHeredocInjector
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.CrystalPercentLiteral
import de.magynhard.crystal.psi.CrystalStringExpression
import de.magynhard.crystal.psi.CrystalSymbolStringExpression

/**
 * Tests for `# language=` comment completion and the platform
 * "Inject language or reference" intention on Crystal injection hosts.
 * Spec: `docs/specs/heredoc-calls.md` (`# language=` comment injection).
 */
class CrystalLanguageCommentCompletionTest : BasePlatformTestCase() {

    // ==================== `# language=` value completion ====================

    fun testEmptyLanguageValueOffersLanguages() {
        myFixture.configureByText("main.cr", "# language=<caret>\n")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Should offer SQL, got: $names", names.contains("SQL"))
        assertTrue("Should offer JavaScript, got: $names", names.contains("JavaScript"))
        assertTrue("Should offer Crystal alias CR, got: $names", names.contains("CR"))
        assertFalse("Must not offer free-text class names", names.contains("Int32"))
    }

    fun testPartialLanguageValueFilters() {
        myFixture.configureByText("main.cr", "# language=SQ<caret>\n")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Should offer SQL for SQ, got: $names", names.contains("SQL"))
        assertFalse("Must not offer JavaScript for SQ, got: $names", names.contains("JavaScript"))
    }

    fun testCompletedLanguageValueReplacesTypedPrefix() {
        myFixture.configureByText("main.cr", "# language=SQ<caret>\n")
        val lookups = myFixture.complete(CompletionType.BASIC).orEmpty()
        val sql = lookups.first { it.lookupString == "SQL" }
        myFixture.lookup.currentItem = sql
        myFixture.finishLookup('\n')
        assertEquals("# language=SQL\n", myFixture.file.text)
    }

    fun testNoSpaceAfterHashStillCompletes() {
        myFixture.configureByText("main.cr", "#language=<caret>\n")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Should offer SQL, got: $names", names.contains("SQL"))
    }

    fun testExtraSpacesAfterHashStillComplete() {
        myFixture.configureByText("main.cr", "#   language=<caret>\n")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Should offer SQL, got: $names", names.contains("SQL"))
    }

    fun testLanguageCompletionDeclinesInsidePrefixAttributeValue() {
        // Caret sits in `prefix=`, not in the language value — the dedicated
        // language provider must decline (regex requires end-of-value after
        // `language=`).
        myFixture.configureByText(
            "main.cr",
            """# language=SQL prefix=<caret>"select """" + "\n"
        )
        val lookups = myFixture.complete(CompletionType.BASIC).orEmpty()
        val names = lookups.map { it.lookupString }
        assertFalse(
            "Language IDs must not complete inside prefix= values, got: $names",
            names.contains("JavaScript") || names.contains("CR")
        )
    }

    fun testPlainCommentDoesNotOfferLanguages() {
        myFixture.configureByText("main.cr", "# just a comm<caret>\n")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertFalse("Plain comments must not offer language IDs", names.contains("SQL"))
    }

    fun testLanguageValueCompletionIsAvailableInTrailingComment() {
        // Completion is intentionally independent of injection adjacency: a
        // trailing `# language=` comment still gets value completion even
        // though the injector would not apply it to the previous line's hosts.
        myFixture.configureByText("main.cr", "x = 1 # language=<caret>\n")
        val names = myFixture.complete(CompletionType.BASIC).orEmpty().map { it.lookupString }
        assertTrue("Should offer SQL, got: $names", names.contains("SQL"))
    }

    // ==================== Platform intention ====================

    fun testInjectLanguageIntentionAvailableOnStringHost() {
        myFixture.configureByText("main.cr", "s = \"SELECT<caret> 1\"")
        val intentions = myFixture.filterAvailableIntentions("Inject language")
        assertTrue(
            "Platform InjectLanguageAction should be available on Crystal string hosts",
            intentions.isNotEmpty()
        )
    }

    fun testInjectLanguageIntentionAvailableOnHeredocHost() {
        myFixture.configureByText("main.cr", "q = <<-QUERY\nSELECT<caret> 1\nQUERY\n")
        val intentions = myFixture.filterAvailableIntentions("Inject language")
        assertTrue(
            "Platform InjectLanguageAction should be available on Crystal heredoc hosts",
            intentions.isNotEmpty()
        )
    }

    fun testHeredocInjectorListsAllHostTypes() {
        // The intention ultimately relies on the injector/support pair to
        // apply and later re-resolve an injection; pin all host types.
        myFixture.configureByText("main.cr", "s = \"SELECT 1\"\n")
        val injector = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getExtensions(project)
            .filterIsInstance<CrystalHeredocInjector>()
            .firstOrNull()
        assertNotNull(injector)
        val classes = injector!!.elementsToInjectIn()
        assertTrue(classes.contains(CrystalStringExpression::class.java))
        assertTrue(classes.contains(CrystalHeredocLiteral::class.java))
        assertTrue(classes.contains(CrystalPercentLiteral::class.java))
        assertTrue(classes.contains(CrystalSymbolStringExpression::class.java))
    }

    fun testInjectLanguageIntentionAvailableOnPercentHost() {
        myFixture.configureByText("main.cr", "s = %Q(SELECT<caret> 1)")
        val intentions = myFixture.filterAvailableIntentions("Inject language")
        assertTrue(
            "Platform InjectLanguageAction should be available on Crystal percent hosts",
            intentions.isNotEmpty()
        )
    }

    fun testInjectLanguageIntentionAvailableOnSymbolHost() {
        myFixture.configureByText("main.cr", "s = :\"SELECT<caret> 1\"")
        val intentions = myFixture.filterAvailableIntentions("Inject language")
        assertTrue(
            "Platform InjectLanguageAction should be available on Crystal symbol hosts",
            intentions.isNotEmpty()
        )
    }
}
