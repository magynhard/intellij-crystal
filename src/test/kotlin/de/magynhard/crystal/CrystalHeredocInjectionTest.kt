package de.magynhard.crystal

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import de.magynhard.crystal.injection.CrystalHeredocInjection
import de.magynhard.crystal.injection.CrystalHeredocInjector
import de.magynhard.crystal.psi.CrystalHeredocLiteral

/**
 * Tests for embedded-language injection into heredocs: the marker resolves to
 * an installed language and the body is registered as injection place(s), with
 * interpolation gaps cut out and placeholder prefixes keeping the injected
 * document syntactically valid.
 *
 * The injector is invoked directly against a recording registrar so the place
 * layout (ranges, prefixes) can be asserted deterministically. Registration of
 * the injector itself is covered by the extension-point assertion, and the
 * marker-to-language mapping by [CrystalHeredocInjection.resolveLanguage].
 */
class CrystalHeredocInjectionTest : BasePlatformTestCase() {

    /** Recording [MultiHostRegistrar] capturing the injection layout. */
    private class FakeRegistrar : MultiHostRegistrar {
        var started = false
        var done = false
        lateinit var language: Language
        val places = mutableListOf<Place>()

        class Place(val prefix: String, val suffix: String, val range: TextRange, val content: String)

        override fun startInjecting(language: Language): MultiHostRegistrar {
            started = true
            this.language = language
            return this
        }

        override fun addPlace(
            prefix: String?,
            suffix: String?,
            context: PsiLanguageInjectionHost,
            rangeInsideHost: TextRange
        ): MultiHostRegistrar {
            places.add(Place(prefix ?: "", suffix ?: "", rangeInsideHost, rangeInsideHost.substring(context.text)))
            return this
        }

        override fun doneInjecting() {
            done = true
        }
    }

    private fun inject(code: String): FakeRegistrar? {
        myFixture.configureByText("main.cr", code)
        val host = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalHeredocLiteral::class.java).firstOrNull()
            ?: return null
        val registrar = FakeRegistrar()
        CrystalHeredocInjector().getLanguagesToInject(registrar, host)
        return if (registrar.started && registrar.done) registrar else null
    }

    private fun singlePlaceLanguage(code: String): Pair<String, String>? {
        val registrar = inject(code) ?: return null
        if (registrar.places.size != 1) return null
        return registrar.language.id to registrar.places[0].content
    }

    fun testInjectorIsRegisteredAtExtensionPoint() {
        val registered = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getExtensions(project)
        assertTrue(
            "CrystalHeredocInjector should be registered at the multiHostInjector extension point",
            registered.any { it is CrystalHeredocInjector }
        )
    }

    fun testSqlHeredocInjectsSql() {
        val info = singlePlaceLanguage("a = <<-SQL\nSELECT *\nFROM users\nSQL\n")
        assertNotNull("Expected an injection for <<-SQL", info)
        assertEquals("SQL", info!!.first)
        assertEquals("SELECT *\nFROM users\n", info.second)
    }

    fun testJsAliasInjectsJavaScript() {
        val info = singlePlaceLanguage("a = <<-JS\nlet a = 1\nJS\n")
        assertNotNull(info)
        assertEquals("JavaScript", info!!.first)
        assertEquals("let a = 1\n", info.second)
    }

    fun testJavascriptAliasInjectsJavaScript() {
        val info = singlePlaceLanguage("a = <<-JAVASCRIPT\nlet a = 1\nJAVASCRIPT\n")
        assertNotNull(info)
        assertEquals("JavaScript", info!!.first)
    }

    fun testLowercaseMarkerIsCaseInsensitive() {
        val info = singlePlaceLanguage("a = <<-sql\nSELECT 1\nsql\n")
        assertNotNull("Lowercase markers should resolve case-insensitively", info)
        assertEquals("SQL", info!!.first)
    }

    fun testRawHeredocInjects() {
        val info = singlePlaceLanguage("a = <<-'SQL'\nSELECT 1\nSQL\n")
        assertNotNull("Raw heredocs should be injected", info)
        assertEquals("SQL", info!!.first)
    }

    fun testCrystalSelfInjection() {
        val info = singlePlaceLanguage("a = <<-CR\nclass Foo\nend\nCR\n")
        assertNotNull(info)
        assertEquals("Crystal", info!!.first)
    }

    fun testCrystalLongAlias() {
        val info = singlePlaceLanguage("a = <<-CRYSTAL\n1 + 2\nCRYSTAL\n")
        assertNotNull(info)
        assertEquals("Crystal", info!!.first)
    }

    fun testCssJsonYamlHtmlMarkdownShell() {
        assertEquals("CSS", singlePlaceLanguage("a = <<-CSS\nbody { color: red }\nCSS\n")!!.first)
        assertEquals("JSON", singlePlaceLanguage("a = <<-JSON\n{\"a\": 1}\nJSON\n")!!.first)
        assertEquals("yaml", singlePlaceLanguage("a = <<-YAML\nkey: value\nYAML\n")!!.first)
        assertEquals("yaml", singlePlaceLanguage("a = <<-YML\nkey: value\nYML\n")!!.first)
        assertEquals("HTML", singlePlaceLanguage("a = <<-HTML\n<p>hi</p>\nHTML\n")!!.first)
        assertEquals("Markdown", singlePlaceLanguage("a = <<-MD\n# Title\nMD\n")!!.first)
        assertEquals("Shell Script", singlePlaceLanguage("a = <<-SH\necho hi\nSH\n")!!.first)
    }

    fun testInterpolatedBodySplitsPlacesWithPlaceholder() {
        val registrar = inject("a = <<-SQL\nSELECT * FROM #{table}\nSQL\n")
        assertNotNull(registrar)
        assertEquals("SQL", registrar!!.language.id)
        assertEquals(2, registrar.places.size)
        assertEquals("", registrar.places[0].prefix)
        assertEquals("SELECT * FROM ", registrar.places[0].content)
        assertEquals("NULL", registrar.places[1].prefix)
        assertEquals("\n", registrar.places[1].content)
    }

    fun testInterpolationAtBodyStartGetsPlaceholderPrefix() {
        val registrar = inject("a = <<-SQL\n#{column} FROM users\nSQL\n")
        assertNotNull(registrar)
        assertEquals(1, registrar!!.places.size)
        assertEquals("NULL", registrar.places[0].prefix)
        assertEquals(" FROM users\n", registrar.places[0].content)
    }

    fun testUnknownMarkerIsNotInjected() {
        assertNull(inject("a = <<-NOSUCHLANGUAGE\nwhatever\nNOSUCHLANGUAGE\n"))
    }

    fun testPlainHeredocWithoutLanguageMarkerIsNotInjected() {
        assertNull(inject("a = <<-END\nsome text\nEND\n"))
    }

    fun testGenericLanguageIdFallback() {
        org.junit.Assume.assumeTrue(Language.findLanguageByID("kotlin") != null)
        val info = singlePlaceLanguage("a = <<-KOTLIN\nval a = 1\nKOTLIN\n")
        assertNotNull("Generic language-ID fallback should inject KOTLIN", info)
        assertEquals("kotlin", info!!.first)
    }

    fun testRubyInjectionFollowsPluginAvailability() {
        val ruby = Language.findLanguageByID("ruby") ?: Language.findLanguageByID("Ruby")
        if (ruby == null) {
            assertNull(inject("a = <<-RUBY\ndef foo\nend\nRUBY\n"))
        } else {
            assertEquals(ruby.id, singlePlaceLanguage("a = <<-RUBY\ndef foo\nend\nRUBY\n")!!.first)
        }
    }

    fun testHamlInjectionFollowsPluginAvailability() {
        val haml = Language.findLanguageByID("Haml") ?: Language.findLanguageByID("HAML")
        if (haml == null) {
            assertNull(inject("a = <<-HAML\n%p hi\nHAML\n"))
        } else {
            assertEquals(haml.id, singlePlaceLanguage("a = <<-HAML\n%p hi\nHAML\n")!!.first)
        }
    }

    fun testAnnotatorSkipFollowsResolution() {
        // willInject must agree with resolveLanguage — the annotator uses it to
        // skip the enforced body coloring for injected bodies.
        myFixture.configureByText("main.cr", "a = <<-SQL\nSELECT 1\nSQL\nb = <<-END\nx\nEND\n")
        val literals = PsiTreeUtil.findChildrenOfType(myFixture.file, CrystalHeredocLiteral::class.java).toList()
        assertEquals(2, literals.size)
        assertTrue(CrystalHeredocInjection.willInject(literals[0]))
        assertTrue(!CrystalHeredocInjection.willInject(literals[1]))
    }
}
