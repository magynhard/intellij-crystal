package de.magynhard.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.CrystalArgument
import de.magynhard.crystal.psi.CrystalAssignment
import de.magynhard.crystal.psi.CrystalBareArgument
import de.magynhard.crystal.psi.CrystalBareArgumentList
import de.magynhard.crystal.psi.CrystalCallArgs
import de.magynhard.crystal.psi.CrystalMacroControl
import de.magynhard.crystal.psi.CrystalMacroIfEnvelope
import de.magynhard.crystal.psi.CrystalTypes

/**
 * Reports a positional, splat, double-splat, `out`, or heredoc argument after
 * a named argument (`f(a: 1, 2)`), which Crystal rejects with "expected named
 * argument, not ...". Block passes (`&blk`) and `do` blocks stay allowed (the
 * block is not part of the argument list), as do macro-controlled tails whose
 * expansion is unknown. Spaced-colon type shapes (`x : Type`) never open the
 * named phase, mirroring the compiler.
 */
class CrystalArgumentOrderInspection : LocalInspectionTool() {

    override fun getDescriptionFileName(): String = "$shortName.html"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!CrystalInspectionScope.isProjectSource(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                val list = when (element) {
                    is CrystalCallArgs -> element.argumentList
                    is CrystalBareArgumentList -> element
                    else -> null
                } ?: return
                checkOrdering(list, holder)
            }
        }
    }

    private fun checkOrdering(list: PsiElement, holder: ProblemsHolder) {
        var sawNamed = false
        for (child in list.node.getChildren(null)) {
            val argument = child.psi ?: continue
            // Macro-controlled tails expand to unknown shapes; stop reporting
            // after them to avoid false positives.
            if (argument is CrystalMacroControl || argument is CrystalMacroIfEnvelope) return
            if (argument !is CrystalArgument && argument !is CrystalBareArgument &&
                argument !is CrystalAssignment
            ) {
                continue
            }
            // Macro-generated labels are dynamic; skip the argument entirely.
            if (hasMacroLabel(argument)) continue
            // Block passes stay allowed after named arguments.
            if (isBlockPass(argument)) continue
            if (isCompactNamedArgument(argument)) {
                sawNamed = true
                continue
            }
            if (sawNamed) {
                holder.registerProblem(
                    argument,
                    "expected named argument, not ${offenderText(argument)}",
                    ProblemHighlightType.GENERIC_ERROR
                )
            }
        }
    }

    private fun hasMacroLabel(argument: PsiElement): Boolean {
        val children = argument.node.getChildren(null)
        val colonIndex = children.indexOfFirst { it.elementType == CrystalTypes.COLON }
        val end = if (colonIndex >= 0) colonIndex else children.size
        return (0 until end).any {
            children[it].elementType == CrystalTypes.MACRO_INTERPOLATION_BEGIN
        }
    }

    private fun isBlockPass(argument: PsiElement): Boolean {
        return firstLeaf(argument)?.node?.elementType == CrystalTypes.AMPERSAND
    }

    /**
     * True for `name:`-shaped arguments. A spaced colon (`x : Type`) is a
     * type shape, never a named argument — the compiler accepts positional
     * arguments after it (`f(x : Int32, 2)`).
     */
    private fun isCompactNamedArgument(argument: PsiElement): Boolean {
        val children = argument.node.getChildren(null)
        val colonIndex = children.indexOfFirst { it.elementType == CrystalTypes.COLON }
        if (colonIndex <= 0) return false
        return !children[colonIndex - 1].text.isBlank()
    }

    private fun offenderText(argument: PsiElement): String {
        return firstLeaf(argument)?.text ?: "?"
    }

    private fun firstLeaf(argument: PsiElement): PsiElement? {
        var current: PsiElement? = PsiTreeUtil.getDeepestFirst(argument)
        while (current != null &&
            PsiTreeUtil.isAncestor(argument, current, false) &&
            (current is PsiWhiteSpace || current.node?.elementType == CrystalTypes.NEWLINE)
        ) {
            current = PsiTreeUtil.nextLeaf(current)
        }
        return current?.takeIf { PsiTreeUtil.isAncestor(argument, it, false) }
    }
}
