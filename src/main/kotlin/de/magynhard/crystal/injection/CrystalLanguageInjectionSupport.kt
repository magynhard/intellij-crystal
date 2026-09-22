package de.magynhard.crystal.injection

import com.intellij.psi.PsiLanguageInjectionHost
import org.intellij.plugins.intelliLang.inject.AbstractLanguageInjectionSupport
import de.magynhard.crystal.psi.CrystalHeredocLiteral
import de.magynhard.crystal.psi.CrystalPercentLiteral
import de.magynhard.crystal.psi.CrystalStringExpression
import de.magynhard.crystal.psi.CrystalSymbolStringExpression
import de.magynhard.crystal.psi.impl.CrystalPercentLiteralMixin
import de.magynhard.crystal.psi.impl.CrystalSymbolStringExpressionMixin

/**
 * Registers Crystal with the IntelliLang injection machinery.
 *
 * Purpose: the generic IntelliLang `CommentLanguageInjector` contributes
 * comment-driven injections for any valid host whose language has no
 * applicable injection support. Crystal string injection is fully owned by
 * [CrystalHeredocInjector] (alias-aware language resolution, interpolation
 * splitting, placeholder prefixes), so this support declares Crystal hosts as
 * applicable and opts OUT of the default comment injector — the generic
 * contributor stays silent and double injections are impossible.
 *
 * Percent literals and quoted symbol strings participate only in their
 * string-like forms (`%q`/`%Q`/bare `%()`, `:"…"`); regex/command content,
 * word/symbol arrays, and macro-generated symbols are not applicable hosts.
 */
class CrystalLanguageInjectionSupport : AbstractLanguageInjectionSupport() {

    override fun getId(): String = "crystal"

    override fun getPatternClasses(): Array<Class<*>> = arrayOf(
        CrystalHeredocLiteral::class.java,
        CrystalStringExpression::class.java,
        CrystalPercentLiteral::class.java,
        CrystalSymbolStringExpression::class.java,
    )

    override fun isApplicableTo(host: PsiLanguageInjectionHost): Boolean =
        host is CrystalHeredocLiteral ||
            host is CrystalStringExpression ||
            (host is CrystalPercentLiteral && CrystalPercentLiteralMixin.isStringLike(host)) ||
            (host is CrystalSymbolStringExpression && CrystalSymbolStringExpressionMixin.isStringForm(host))

    override fun useDefaultCommentInjector(): Boolean = false
}
