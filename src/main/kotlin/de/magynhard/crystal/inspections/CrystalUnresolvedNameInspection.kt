package de.magynhard.crystal.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import de.magynhard.crystal.psi.*

/**
 * Inspection that reports names resolving to nothing as `Cannot find 'name'`:
 * bare identifiers and call callees, DOT method names with exact receivers,
 * constants, and type paths. See `docs/specs/unresolved-names.md` for the
 * known-vs-silent contract.
 *
 * Severity splits by cause: truly unknown names warn, names indexed only
 * outside the current require closure warn weakly. Macro-uncertain,
 * incomplete, and macro-context names stay silent (suppression-first, like
 * the call-argument inspections).
 *
 * Conventions:
 * - DOT receivers are never flagged, only DOT method names with exact
 *   receivers; unknown receivers never fall back to name-only matches.
 * - Global variables (`$x`) are out of scope until globals get resolution.
 */
class CrystalUnresolvedNameInspection : LocalInspectionTool() {

    // Wires the inspectionDescriptions/<shortName>.html resource into the
    // platform's description loading; without it the Inspect Code results
    // view crashes when a result node is selected.
    override fun getDescriptionFileName(): String = "$shortName.html"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!CrystalInspectionScope.isProjectSource(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                // Macro context: {{ … }} interpolations, macro bodies, and
                // macro-invocation block bodies hold macro data, not runtime
                // names — ordinary name diagnostics do not apply.
                if (CrystalMacroContext.isInMacroContext(element) ||
                    CrystalMacroContext.isInsideMacroCallBlock(element)
                ) {
                    return
                }
                when (element) {
                    is CrystalVariableReference -> checkVariableReference(element, holder)
                    is CrystalMethodCallExpression,
                    is CrystalBareMethodCallExpression,
                    -> checkCallCallee(element, holder)
                    is CrystalDotCallAccess -> checkDotCall(element, holder)
                    is CrystalNamespaceAccess -> checkNamespace(element, holder)
                    is CrystalTypePath -> checkTypePath(element, holder)
                }
            }
        }
    }

    private fun checkVariableReference(reference: CrystalVariableReference, holder: ProblemsHolder) {
        val (leaf, isConstant) = CrystalUnresolvedName.variableReferenceLeaf(reference) ?: return
        val kind = CrystalUnresolvedName.isUnresolvedLeaf(leaf, isConstant, reference) ?: return
        holder.registerProblem(
            leaf,
            CrystalUnresolvedName.messageFor(leaf.text),
            highlightFor(kind)
        )
    }

    private fun checkCallCallee(callExpr: PsiElement, holder: ProblemsHolder) {
        val (callee, isConstant) = CrystalUnresolvedName.callCalleeLeaf(callExpr) ?: return
        val kind = CrystalUnresolvedName.isUnresolvedLeaf(callee, isConstant, callExpr) ?: return
        holder.registerProblem(
            callee,
            CrystalUnresolvedName.messageFor(callee.text),
            highlightFor(kind)
        )
    }

    private fun checkDotCall(access: CrystalDotCallAccess, holder: ProblemsHolder) {
        val (flag, kind) = CrystalUnresolvedName.dotCallFlagElement(access) ?: return
        if (CrystalUnresolvedName.isKeywordSpelling(flag)) return
        val name = flag.text
        if (name.isBlank()) return
        holder.registerProblem(
            flag,
            CrystalUnresolvedName.messageFor(name),
            highlightFor(kind)
        )
    }

    private fun checkNamespace(access: CrystalNamespaceAccess, holder: ProblemsHolder) {
        val (flag, kind) = CrystalUnresolvedName.namespaceFlagElement(access) ?: return
        val name = flag.text
        if (name.isBlank()) return
        holder.registerProblem(
            flag,
            CrystalUnresolvedName.messageFor(name),
            highlightFor(kind)
        )
    }

    private fun checkTypePath(path: CrystalTypePath, holder: ProblemsHolder) {
        val (flag, kind) = CrystalUnresolvedName.typePathFlagElement(path) ?: return
        val name = flag.text
        if (name.isBlank()) return
        holder.registerProblem(
            flag,
            CrystalUnresolvedName.messageFor(name),
            highlightFor(kind)
        )
    }

    private fun highlightFor(kind: CrystalUnresolvedName.UnresolvedKind): ProblemHighlightType =
        if (kind == CrystalUnresolvedName.UnresolvedKind.UNREQUIRED) {
            ProblemHighlightType.WEAK_WARNING
        } else {
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        }
}
