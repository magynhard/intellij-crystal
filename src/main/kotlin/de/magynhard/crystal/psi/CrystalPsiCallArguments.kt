package de.magynhard.crystal.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

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

    /**
     * The argument PSI elements of a call. A parenthesized list yields its
     * arguments; a bare list that follows a leading array literal — the
     * `obj [a], b` array-comma shape, where the array is a sibling before the
     * comma — yields the array first, then the list's arguments. The grammar's
     * `array_literal COMMA bare_argument_list` alternative keeps the array
     * outside the list (so bracket-without-comma still binds as an index
     * postfix); consumers must not lose it.
     */
    fun argumentElements(argsElement: PsiElement?): List<PsiElement> {
        if (argsElement == null) return emptyList()
        if (argsElement is CrystalCallArgs) {
            val argList = argsElement.argumentList ?: return emptyList()
            return argList.argumentList.map { it as PsiElement }
        }
        if (argsElement is CrystalBareArgumentList) {
            val elements = mutableListOf<PsiElement>()
            leadingArrayLiteralOf(argsElement)?.let(elements::add)
            elements.addAll(argsElement.bareArgumentList)
            return elements
        }
        return emptyList()
    }

    /** The leading array literal of the array-comma bare shape, or null. */
    private fun leadingArrayLiteralOf(bareList: CrystalBareArgumentList): CrystalArrayLiteral? {
        val comma = previousSignificantSibling(bareList)
            ?.takeIf { it.node?.elementType == CrystalTypes.COMMA } ?: return null
        return previousSignificantSibling(comma) as? CrystalArrayLiteral
    }

    private fun previousSignificantSibling(element: PsiElement): PsiElement? {
        var current = element.prevSibling
        while (current != null && (current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE)) {
            current = current.prevSibling
        }
        return current
    }

    /**
     * Returns a named argument's label text. Identifier labels come back
     * verbatim; string labels (`with_env("FOO": "bar")`) arrive as plain
     * STRING_LITERAL/STRING_ESCAPE leaves whose quotes are stripped, so the
     * label matches parameter names instead of flagging every string-keyed
     * call as an unknown argument.
     */
    fun getNamedLabel(argument: PsiElement): String? {
        if (argument !is CrystalArgument && argument !is CrystalBareArgument) return null
        val children = argument.node.getChildren(null)
        val colonIndex = children.indexOfFirst { it.elementType == CrystalTypes.COLON }
        if (colonIndex <= 0) return null
        val labelChild = children[colonIndex - 1]
        if (labelChild.elementType != CrystalTypes.STRING_LITERAL &&
            labelChild.elementType != CrystalTypes.STRING_ESCAPE
        ) {
            return labelChild.text
        }
        val parts = mutableListOf<String>()
        var index = colonIndex - 1
        while (index >= 0 &&
            (children[index].elementType == CrystalTypes.STRING_LITERAL ||
                children[index].elementType == CrystalTypes.STRING_ESCAPE)
        ) {
            parts.add(children[index].text)
            index--
        }
        return parts.reversed().joinToString("").removeSurrounding("\"")
    }
}
