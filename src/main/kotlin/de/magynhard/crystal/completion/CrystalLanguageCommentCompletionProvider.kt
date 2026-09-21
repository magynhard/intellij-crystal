package de.magynhard.crystal.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import de.magynhard.crystal.injection.CrystalHeredocInjection
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Completion for the language value of a `# language=` injection comment.
 *
 * When the caret sits in the bare value of a whole-line comment's `language`
 * attribute (`# language=…`, before any following whitespace), offers every
 * installed language ID plus the heredoc marker aliases that
 * [CrystalHeredocInjection.resolveLanguage] accepts — so `# language=JS`
 * completes the same way `<<-JS` resolves.
 *
 * Trailing attributes (`prefix=` / `suffix=`) are not completed here: the
 * caret is past the language value once whitespace follows, and this provider
 * declines. Quoted language values are not completed (the insert would have
 * to manage surrounding quotes).
 */
object CrystalLanguageCommentCompletionProvider {

    /**
     * Whole-line comment whose caret is still inside the bare `language=`
     * value: `#` / `#language=` / `#   language=` plus the value typed so far,
     * with no whitespace or quote in the value.
     */
    private val LANGUAGE_VALUE_BEFORE_CARET =
        Regex("""^#[ \t]*language=([^\s"]*)$""", RegexOption.IGNORE_CASE)

    /**
     * Returns the bare language value typed before [offset] when completion
     * runs inside a `# language=` comment value, or null when the caret is
     * elsewhere (including past the value, in `prefix=`/`suffix=`, or in a
     * quoted value).
     */
    fun getLanguageValuePrefix(position: PsiElement, offset: Int): String? {
        val comment = generateSequence(position) { it.parent }
            .firstOrNull { it.node?.elementType == CrystalTypes.LINE_COMMENT }
            ?: return null
        val start = comment.textRange.startOffset
        if (offset < start || offset > comment.textRange.endOffset) return null
        val beforeCaret = comment.text.substring(0, offset - start)
        return LANGUAGE_VALUE_BEFORE_CARET.matchEntire(beforeCaret)?.groupValues?.get(1)
    }

    /**
     * Lookup elements for every installed language ID and heredoc marker
     * alias, sorted case-insensitively for a stable popup order.
     */
    fun getLanguageLookups(): List<LookupElementBuilder> {
        val ids = LinkedHashSet<String>()
        for (language in Language.getRegisteredLanguages()) {
            if (language.id.isNotBlank()) ids.add(language.id)
        }
        ids.addAll(CrystalHeredocInjection.completionAliasKeys())
        return ids
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .map { LookupElementBuilder.create(it) }
    }

    /** Adds [getLanguageLookups] under a matcher for [languageValuePrefix]. */
    fun addCompletions(
        languageValuePrefix: String,
        result: CompletionResultSet,
    ) {
        val languageResult = result.withPrefixMatcher(
            result.prefixMatcher.cloneWithPrefix(languageValuePrefix)
        )
        getLanguageLookups().forEach(languageResult::addElement)
    }
}
