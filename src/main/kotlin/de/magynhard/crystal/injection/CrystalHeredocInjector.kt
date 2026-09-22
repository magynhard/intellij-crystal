package de.magynhard.crystal.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.CrystalPercentLiteral
import de.magynhard.crystal.psi.CrystalStringExpression
import de.magynhard.crystal.psi.CrystalSymbolStringExpression
import de.magynhard.crystal.psi.CrystalRequireStatement
import de.magynhard.crystal.psi.impl.CrystalPercentLiteralMixin
import de.magynhard.crystal.psi.impl.CrystalSymbolStringExpressionMixin

/**
 * Injects an embedded language into Crystal string contexts, giving the
 * embedded code full syntax highlighting — mirroring RubyMine's injection
 * behavior.
 *
 * Two host kinds, two trigger paths:
 * - **Heredoc bodies** (`a = <<-SQL … SQL`): the marker resolves through
 *   [CrystalHeredocInjection] (curated aliases + generic language-ID
 *   fallback). A `# language=<id>` comment on the adjacent line wins over the
 *   marker and can carry `prefix=` / `suffix=`; an unresolvable comment
 *   language falls back to the marker.
 * - **String-like literals** (`sql = "SELECT …"`, `%Q(…)`, `%q(…)`,
 *   `:"…"`): driven exclusively by a `# language=<id>` comment on the
 *   adjacent line ([CrystalLanguageComment]). `%r`/`%x` content and
 *   `%w`/`%W`/`%i`/`%I` arrays are not hosts, nor are macro-generated
 *   `:{{…}}` symbols.
 *
 * Crystal interpolations (`#{…}`) are cut out of the injected places; each
 * place after a gap gets a language-specific synthetic placeholder prefix so
 * the injected document stays syntactically valid, while the interpolation
 * itself keeps its Crystal highlighting. The comment prefix/suffix apply to
 * the injected document as a whole (first/last place).
 */
class CrystalHeredocInjector : MultiHostInjector {

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        when (context) {
            is CrystalHeredocLiteral -> injectHeredoc(registrar, context)
            is CrystalStringExpression -> injectString(registrar, context)
            is CrystalPercentLiteral -> injectPercent(registrar, context)
            is CrystalSymbolStringExpression -> injectSymbol(registrar, context)
        }
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(
            CrystalHeredocLiteral::class.java,
            CrystalStringExpression::class.java,
            CrystalPercentLiteral::class.java,
            CrystalSymbolStringExpression::class.java
        )

    private fun injectHeredoc(registrar: MultiHostRegistrar, literal: CrystalHeredocLiteral) {
        val plan = CrystalHeredocInjection.resolveInjectionPlan(literal) ?: return
        val segments = CrystalHeredocInjection.computeBodySegments(literal)
        if (segments.isEmpty()) return
        val placeholder = CrystalHeredocInjection.placeholderFor(plan.language)

        registrar.startInjecting(plan.language)
        segments.forEachIndexed { index, segment ->
            val prefix = when {
                index == 0 -> plan.prefix + if (segment.gapsBefore > 0) placeholder else ""
                else -> placeholder
            }
            val suffix = if (index == segments.size - 1) plan.suffix else ""
            registrar.addPlace(prefix, suffix, literal, segment.range)
        }
        registrar.doneInjecting()
    }

    private fun injectString(registrar: MultiHostRegistrar, stringExpression: CrystalStringExpression) {
        // require "…" strings are dependency paths, not embedded code.
        if (PsiTreeUtil.getParentOfType(stringExpression, CrystalRequireStatement::class.java, false) != null) return

        injectCommentDriven(
            registrar,
            stringExpression,
            CrystalStringExpressionInjection.computeContentSegments(stringExpression)
                .map { CommentPlace(it.range, it.gapsBefore) }
        )
    }

    private fun injectPercent(registrar: MultiHostRegistrar, literal: CrystalPercentLiteral) {
        // require %q(…) paths are dependency paths, not embedded code.
        if (PsiTreeUtil.getParentOfType(literal, CrystalRequireStatement::class.java, false) != null) return

        injectCommentDriven(
            registrar,
            literal,
            CrystalPercentLiteralMixin.computeContentSegments(literal)
                .map { CommentPlace(it.range, it.gapsBefore) }
        )
    }

    private fun injectSymbol(registrar: MultiHostRegistrar, symbol: CrystalSymbolStringExpression) {
        // require :"…" paths are dependency paths, not embedded code.
        if (PsiTreeUtil.getParentOfType(symbol, CrystalRequireStatement::class.java, false) != null) return

        injectCommentDriven(
            registrar,
            symbol,
            CrystalSymbolStringExpressionMixin.computeContentSegments(symbol)
                .map { CommentPlace(it.range, it.gapsBefore) }
        )
    }

    private data class CommentPlace(val range: TextRange, val gapsBefore: Int)

    /**
     * Comment-driven injection shared by string literals, string-like
     * percent literals, and quoted symbol strings: the `# language=<id>`
     * comment on the adjacent line decides the language; interpolation gaps
     * get the language-specific placeholder prefix so the injected document
     * stays syntactically valid.
     */
    private fun injectCommentDriven(
        registrar: MultiHostRegistrar,
        host: PsiLanguageInjectionHost,
        places: List<CommentPlace>
    ) {
        if (places.isEmpty()) return
        val plan = CrystalStringExpressionInjection.resolveCommentPlan(
            host.containingFile,
            host.textRange.startOffset
        ) ?: return
        val placeholder = CrystalHeredocInjection.placeholderFor(plan.language)

        registrar.startInjecting(plan.language)
        places.forEachIndexed { index, place ->
            val prefix = when {
                index == 0 -> plan.prefix + if (place.gapsBefore > 0) placeholder else ""
                else -> placeholder
            }
            val suffix = if (index == places.size - 1) plan.suffix else ""
            registrar.addPlace(prefix, suffix, host, place.range)
        }
        registrar.doneInjecting()
    }
}
