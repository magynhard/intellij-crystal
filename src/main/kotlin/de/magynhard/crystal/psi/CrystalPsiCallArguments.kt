package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement

/**
 * Single source of truth for reading call arguments from a [CrystalCallArgs].
 *
 * Heredoc calls participate in the SAME shape as every other parenthesized
 * call: each heredoc header (`<<-ID`) is a marker argument whose `argument`
 * node contains only the `HEREDOC_START` token, and the matching string bodies
 * (`CrystalHerdDocLiteral`, one per marker in order) are attached to the owning
 * statement via the `heredoc_bodies` composite.
 */
object CrystalPsiCallArguments {

    /**
     * Returns every positional/named/splat/block-pass argument of a parenthesized
     * call in source order, heredoc marker arguments included. Empty for calls
     * without parentheses or macro-only lists.
     */
    fun getArguments(callArgs: CrystalCallArgs): List<CrystalArgument> {
        val argList = callArgs.argumentList ?: return emptyList()
        return argList.argumentList.toList()
    }

    /** Returns the direct token before a named argument's colon. */
    fun getNamedLabel(argument: PsiElement): String? {
        if (argument !is CrystalArgument && argument !is CrystalBareArgument) return null
        val children = argument.node.getChildren(null)
        val colonIndex = children.indexOfFirst { it.elementType == CrystalTypes.COLON }
        return children.getOrNull(colonIndex - 1)?.text
    }
}
