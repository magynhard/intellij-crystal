package de.magynhard.crystal.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.analysis.CrystalRequireGraphService
import de.magynhard.crystal.analysis.CrystalRequireVisibility
import de.magynhard.crystal.psi.CrystalMacroDefinition
import de.magynhard.crystal.psi.CrystalPsiUtils
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Joint method+macro overload pool for call-arity diagnostics.
 *
 * Verified against the compiler (`crystal eval`): a macro and a same-named
 * `def` form one overload pool selected by applicability — `bar(1)` calls
 * `def bar(a)`, `bar(1, 2)` calls `macro bar(a, b)` — and only when nothing
 * applies does the compiler blame the `def` (`wrong number of arguments for
 * 'bar' (given 3, expected 1)`). Measuring such calls against the `def`s
 * alone therefore reports false excess, and macro-only calls report nothing
 * at all. A macro whose arity the call satisfies suppresses the `def`-based
 * diagnostics; the call's argument types stay unchecked everywhere because
 * macro arguments are ASTs, not typed values.
 *
 * Visibility follows the method rules (probed): top-level macros are
 * callable anywhere once required, type-owned macros only from their owner
 * or its nesters (`Foo::Nested` sees neither `Foo`'s defs nor its macros),
 * and an explicit receiver never reaches a top-level macro (`Foo.bar`
 * against a top-level `macro bar` is `undefined method 'bar' for
 * Foo.class`). Macros cannot have receivers (`macro can't have a receiver`),
 * so no `self`-method exclusion applies.
 */
internal fun callableMacros(name: String, context: PsiElement): List<CrystalMacroDefinition> {
    val candidates = findVisibleMacros(name, context) ?: return emptyList()
    val callSiteType = CrystalPsiUtils.callSiteOwnerQualifiedName(context)
    return candidates.filter { macro ->
        val owner = macroOwnerQualifiedName(macro) ?: return@filter true
        callSiteType != null && (callSiteType == owner || callSiteType.startsWith("$owner::"))
    }
}

/**
 * Same-named macros owned by exactly [receiverQualifiedName] and visible
 * from [context]: the only macros an explicit-receiver call can reach.
 * Inherited or included macros are deliberately excluded — fewer
 * suppressions, never wrong ones.
 */
internal fun receiverMacros(
    name: String,
    receiverQualifiedName: String,
    context: PsiElement,
): List<CrystalMacroDefinition> {
    val candidates = findVisibleMacros(name, context) ?: return emptyList()
    return candidates.filter { macroOwnerQualifiedName(it) == receiverQualifiedName }
}

/** Qualified name of the macro's enclosing type, or null for top-level macros. */
internal fun macroOwnerQualifiedName(macro: CrystalMacroDefinition): String? =
    CrystalPsiUtils.getEnclosingType(macro)?.let(CrystalPsiUtils::buildQualifiedName)

/**
 * Same-named macros whose defining file is part of [context]'s effective
 * source set, or null when no visibility can be established (then every
 * consumer stays silent instead of guessing).
 */
private fun findVisibleMacros(name: String, context: PsiElement): List<CrystalMacroDefinition>? {
    val project = context.project
    val candidates = CrystalIndexService.findMacros(name, project, GlobalSearchScope.allScope(project))
    if (candidates.isEmpty()) return emptyList()
    val sources = CrystalRequireGraphService.getInstance(project).effectiveSources(context)
    if (sources.files.isEmpty()) return null
    return candidates.filter { CrystalRequireVisibility.isVisible(it, sources) }
}
