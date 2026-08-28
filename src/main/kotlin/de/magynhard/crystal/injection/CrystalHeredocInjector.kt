package de.magynhard.crystal.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalHeredocLiteral

/**
 * Injects the language named by a heredoc marker into the heredoc body
 * (`a = <<-SQL … SQL`), giving the embedded code full syntax highlighting
 * (and, where the target plugin provides it, completion) — mirroring
 * RubyMine's heredoc injection.
 *
 * The marker is resolved through [CrystalHeredocInjection]: a curated alias
 * table plus a generic case-insensitive language-ID fallback. Bodies with
 * Crystal interpolations (`#{…}`) are injected as multiple places with the
 * interpolations cut out; each place after a gap gets a language-specific
 * synthetic placeholder prefix so the injected document stays syntactically
 * valid, while the interpolation itself keeps its Crystal highlighting.
 */
class CrystalHeredocInjector : MultiHostInjector {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context !is CrystalHeredocLiteral) return
        val marker = CrystalHeredocInjection.resolveBodyMarker(context) ?: return
        val language = CrystalHeredocInjection.resolveLanguage(marker) ?: return

        val segments = CrystalHeredocInjection.computeBodySegments(context)
        if (segments.isEmpty()) return
        val placeholder = CrystalHeredocInjection.placeholderFor(language)

        registrar.startInjecting(language)
        for (segment in segments) {
            val prefix = if (segment.gapsBefore == 0) "" else placeholder
            registrar.addPlace(prefix, "", context, segment.range)
        }
        registrar.doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(CrystalHeredocLiteral::class.java)
}
