package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import de.magynhard.crystal.inspections.CrystalCallExtractor
import de.magynhard.crystal.stubs.CrystalIndexService

/**
 * Macro-context detection for name resolution and diagnostics.
 *
 * Inside `{{ … }}` macro interpolations and macro definition bodies, Crystal
 * does NOT type-check or method-resolve code the way ordinary code is: calls
 * resolve to macros or to the builtin `Crystal::Macros` macro-method API
 * (`run`, `system`, `puts`, `flag?`, …), and their arguments are ASTs, not
 * typed values. Consumers must therefore skip ordinary method resolution and
 * argument diagnostics inside macro context.
 */
object CrystalMacroContext {

    fun isInMacroContext(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is CrystalMacroInterpolation || current is CrystalMacroDefinition) {
                return true
            }
            current = current.parent
        }
        return false
    }

    /**
     * True when an argument starts with a `{{ … }}` macro splice. Its arity is
     * unknowable before expansion: the splice may expand to zero or more
     * arguments, or to an operator between operands — `to_f32 {{ op.id }} other`
     * (`crystal/compiler_rt.cr`) expands to `to_f32 + other`, so the splice is
     * not an argument at all. Consumers must therefore skip argument-count
     * diagnostics for calls whose argument list contains such a splice, while
     * the target still resolves. Only a splice at the start of the argument
     * counts: a splice nested deeper (`foo(bar({{ x }}))`) leaves the argument
     * count known.
     */
    fun isMacroSplicedArgument(element: PsiElement): Boolean {
        var current: PsiElement = element
        while (true) {
            val child = significantFirstChild(current) ?: return false
            when (child) {
                is CrystalMacroInterpolation -> return true
                is CrystalExpression, is CrystalArgument, is CrystalBareArgument -> current = child
                else -> return false
            }
        }
    }

    private fun significantFirstChild(element: PsiElement): PsiElement? {
        var child: PsiElement? = element.firstChild
        while (child is PsiWhiteSpace || child?.node?.elementType == CrystalTypes.NEWLINE) {
            child = child.nextSibling
        }
        return child
    }

    /**
     * Block bodies of macro invocations hold macro data, not runtime calls. The
     * `properties` DSL (ameba `Config::RuleConfig`) reads its block statements
     * via `prop.named_args`, and `bin_path nil, as: String?` exists solely as
     * the documented form for the type annotation: the named argument `as` has
     * no method parameter anywhere in the corpus and the call is never
     * executed. Ordinary argument diagnostics (unknown/missing arguments) do
     * not apply to such bodies.
     *
     * Only a block whose owner resolves to a macro is gated. A block owned by
     * a regular method (`each do`, `describe`-style runtime bodies) keeps its
     * diagnostics. Real methods and macros live in the same overload table, so
     * a name that has a macro at all is a macro call in Crystal — the macro
     * index is checked against the whole scope because stdlib macros sit
     * outside the project content root.
     */
    fun isInsideMacroCallBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is CrystalBlock) {
                if (isMacroOwnedBlock(current)) return true
            }
            current = current.parent
        }
        return false
    }

    private fun isMacroOwnedBlock(block: CrystalBlock): Boolean {
        val owner = generateSequence(block.parent) { it.parent }
            .takeWhile { it !is PsiFile }
            .firstOrNull {
                it is CrystalMethodCallExpression ||
                    it is CrystalBareMethodCallExpression ||
                    it is CrystalDotCallAccess
            } ?: return false
        val name = CrystalCallExtractor.extractMethodName(owner) ?: return false
        return CrystalIndexService.findMacros(
            name,
            owner.project,
            GlobalSearchScope.allScope(owner.project),
        ).isNotEmpty()
    }
}
