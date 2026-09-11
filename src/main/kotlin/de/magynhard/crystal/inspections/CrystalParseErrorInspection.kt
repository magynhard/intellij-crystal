package de.magynhard.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiErrorElement

/**
 * Exports raw parser diagnostics as inspection problems.
 *
 * The regular editor marks `PsiErrorElement` through the error highlighter,
 * but offline Inspect Code runs only execute LocalInspectionTools, so parse
 * failures never surfaced in headless audit reports. This inspection closes
 * that gap: every parse error becomes a discrete, filterable problem entry
 * (`CrystalParseError.xml` in offline output).
 *
 * `enabledByDefault="false"`: in normal editors the error highlighter already
 * renders these problems; double-reporting would be pure noise. The headless
 * audit profile enables this inspection explicitly.
 */
class CrystalParseErrorInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                if (element is PsiErrorElement) {
                    // PsiErrorElement is frequently zero-length; problem
                    // descriptors must not point at empty PSI, so anchor on a
                    // nearby non-empty element (leaves, then enclosing
                    // composite) while keeping the parse error's own message.
                    val message = element.errorDescription.ifBlank { "Unparsable Crystal syntax" }
                    val anchor = nonEmptyAnchor(element) ?: return
                    holder.registerProblem(
                        anchor,
                        "Crystal parse error: $message",
                        ProblemHighlightType.GENERIC_ERROR
                    )
                }
            }
        }
    }

    private fun nonEmptyAnchor(error: PsiErrorElement): com.intellij.psi.PsiElement? {
        var sibling = error.prevSibling
        while (sibling != null) {
            if (sibling.textLength > 0 && sibling !is com.intellij.psi.PsiWhiteSpace) return sibling
            sibling = sibling.prevSibling
        }
        var parent: com.intellij.psi.PsiElement? = error.parent
        while (parent != null && parent !is com.intellij.psi.PsiFile) {
            if (parent.textLength > 0) return parent
            parent = parent.parent
        }
        return null
    }
}
