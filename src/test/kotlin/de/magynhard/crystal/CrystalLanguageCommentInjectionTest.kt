package de.magynhard.crystal

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.injection.CrystalHeredocInjector
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.CrystalStringExpression

/**
 * Tests for `# language=<id>` comment-driven injection into heredocs and
 * string literals: comment precedence over markers, marker fallback for
 * unresolvable comment languages, adjacency rules, and prefix/suffix wiring.
 */
class CrystalLanguageCommentInjectionTest : BasePlatformTestCase() {

    private fun injectHost(code: String, hostClass: Class<out PsiElement>): FakeInjectionRegistrar? {
        myFixture.configureByText("main.cr", code)
        val host = PsiTreeUtil.findChildrenOfType(myFixture.file, hostClass).firstOrNull() ?: return null
        val registrar = FakeInjectionRegistrar()
        CrystalHeredocInjector().getLanguagesToInject(registrar, host)
        return if (registrar.started && registrar.done) registrar else null
    }

    private fun injectHeredoc(code: String): FakeInjectionRegistrar? =
        injectHost(code, CrystalHeredocLiteral::class.java)

    private fun injectString(code: String): FakeInjectionRegistrar? =
        injectHost(code, CrystalStringExpression::class.java)

    // ==================== Heredoc: comment drives the injection ====================

    fun testCommentInjectsHeredocWithUnresolvableMarker() {
        val registrar = injectHeredoc("# language=SQL\nq = <<-QUERY\nuser_id\nQUERY\n")
        assertNotNull(registrar)
        assertEquals("SQL", registrar!!.language.id)
        assertEquals("user_id\n", registrar.places.single().content)
    }

    fun testCommentWinsOverResolvableMarker() {
        val registrar = injectHeredoc("# language=HTML\nq = <<-SQL\n<b>hi</b>\nSQL\n")
        assertNotNull(registrar)
        assertEquals("HTML", registrar!!.language.id)
    }

    fun testUnresolvableCommentLanguageFallsBackToMarker() {
        val registrar = injectHeredoc("# language=NOSUCHLANGUAGE\nq = <<-SQL\nSELECT 1\nSQL\n")
        assertNotNull(registrar)
        assertEquals("SQL", registrar!!.language.id)
    }

    fun testCommentLanguageAliasResolution() {
        val registrar = injectHeredoc("# language=JS\nq = <<-RUNNER\nlet a = 1\nRUNNER\n")
        assertNotNull(registrar)
        assertEquals("JavaScript", registrar!!.language.id)
    }

    // ==================== Prefix / suffix wiring ====================

    fun testCommentPrefixReachesFirstPlaceAndSuffixLastPlace() {
        val registrar = injectHeredoc("""# language=SQL prefix="select " suffix=" limit 1"""" + "\nq = <<-Q\nuser_id\nQ\n")
        assertNotNull(registrar)
        assertEquals("select ", registrar!!.places.first().prefix)
        assertEquals(" limit 1", registrar.places.last().suffix)
    }

    fun testCommentPrefixCombinedWithPlaceholderAfterInterpolationGap() {
        val registrar = injectHeredoc("""# language=SQL prefix="select """" + "\nq = <<-SQL\n* FROM #{table}\nSQL\n")
        assertNotNull(registrar)
        assertEquals(2, registrar!!.places.size)
        // Place 1 sits before the gap: comment prefix only (the document-level
        // prefix appears exactly once, at the very start).
        assertEquals("select ", registrar.places[0].prefix)
        // Place 2 follows the gap: SQL placeholder keeps the fragment valid.
        assertEquals("NULL", registrar.places[1].prefix)
        assertEquals("", registrar.places[0].suffix)
        assertEquals("", registrar.places[1].suffix)
    }

    // ==================== Adjacency rules ====================

    fun testCommentTwoLinesAboveDoesNotApply() {
        val registrar = injectHeredoc("# language=SQL\n\nq = <<-NOSUCHLANGUAGE\nx\nNOSUCHLANGUAGE\n")
        assertNull("The comment must sit on the adjacent line", registrar)
    }

    fun testStatementBetweenCommentAndHeredocBlocksComment() {
        val registrar = injectHeredoc("# language=SQL\ndo_something\nq = <<-NOSUCHLANGUAGE\nx\nNOSUCHLANGUAGE\n")
        assertNull("A statement between comment and target must break adjacency", registrar)
    }

    fun testTrailingCommentOnOtherStatementDoesNotApply() {
        // The comment is not a whole-line comment above the target line.
        val registrar = injectHeredoc("x = 1 # language=SQL\nq = <<-NOSUCHLANGUAGE\nx\nNOSUCHLANGUAGE\n")
        assertNull(registrar)
    }

    // ==================== String literals ====================

    fun testCommentInjectsStringLiteral() {
        val registrar = injectString("# language=SQL\nsql = \"SELECT 1\"\n")
        assertNotNull(registrar)
        assertEquals("SQL", registrar!!.language.id)
        assertEquals("SELECT 1", registrar.places.single().content)
    }

    fun testStringContentExcludesQuotesAndEscapesAreDecodedByEscaperNotPlaces() {
        val registrar = injectString("# language=SQL\nsql = \"SELECT 1\"\n")
        assertNotNull(registrar)
        val range = registrar!!.places.single().range
        val host = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalStringExpression::class.java).first()
        assertEquals("\"", host.text.substring(0, range.startOffset))
        assertEquals("\"", host.text.substring(range.endOffset))
    }

    fun testStringWithoutCommentIsNotInjected() {
        assertNull(injectString("sql = \"SELECT 1\"\n"))
    }

    fun testUnknownCommentLanguageOverStringIsNotInjected() {
        assertNull(injectString("# language=NOSUCHLANGUAGE\nsql = \"SELECT 1\"\n"))
    }

    fun testStringInterpolationSplitsPlaces() {
        val registrar = injectString("# language=HTML\ns = \"<p>#{name}</p>\"\n")
        assertNotNull(registrar)
        assertEquals(2, registrar!!.places.size)
        assertEquals("<p>", registrar.places[0].content)
        assertEquals("</p>", registrar.places[1].content)
        assertEquals("<!-- -->", registrar.places[1].prefix)
    }

    fun testRequireStringsAreNotInjected() {
        myFixture.addFileToProject("apfel.cr", "class Apfel\nend\n")
        assertNull(injectString("# language=SQL\nrequire \"./apfel\"\n"))
    }

    // ==================== Injector registration ====================

    fun testInjectorListsBothHostTypes() {
        val injector = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getExtensions(project)
            .filterIsInstance<CrystalHeredocInjector>()
            .firstOrNull()
        assertNotNull(injector)
        val elementClasses = injector!!.elementsToInjectIn()
        assertTrue(elementClasses.contains(CrystalHeredocLiteral::class.java))
        assertTrue(elementClasses.contains(CrystalStringExpression::class.java))
    }
}
