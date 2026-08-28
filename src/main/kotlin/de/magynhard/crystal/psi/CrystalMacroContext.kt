package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

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
}
