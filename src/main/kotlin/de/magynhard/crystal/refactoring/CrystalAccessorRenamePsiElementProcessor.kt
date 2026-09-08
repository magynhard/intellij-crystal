package de.magynhard.crystal.refactoring

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import de.magynhard.crystal.psi.CrystalClassVarAccess
import de.magynhard.crystal.psi.CrystalInstanceVarAccess
import de.magynhard.crystal.navigation.CrystalAccessorCoupling
import de.magynhard.crystal.psi.CrystalBareMethodCallExpression
import de.magynhard.crystal.psi.CrystalMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil

/**
 * Rename coupling between accessor-macro declarations and their implicit
 * chain. Renames are name-identical symbols: `property foo` and the variable
 * `@foo` (plus `@@foo` for the class_* variants) denote the SAME declaration,
 * and a rename of either side without the other would leave the code broken —
 * the accessor name simply loses its backing variable (or vice versa). Crystal
 * macro generates the reader and setter only through the paired names.
 *
 * Registered for both targets:
 * - instance/class variable elements (`@foo`, `@@foo`, incl. storage
 *   parameters named `initialize(@foo : T)`): the coupled accessor argument
 *   joins the rename with the SAME bare name (sigil is added when writing),
 * - accessor-macro name arguments (`foo` of `property foo`): only the matching
 *   same-name variable enters via the ReferencesSearcher (no flag prompting —
 *   the coupling is deterministic).
 */
class CrystalAccessorRenamePsiElementProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        if (element is CrystalInstanceVarAccess || element is CrystalClassVarAccess) return true
        return isAccessorNamedArg(element)
    }

    /**
     * The reverse rename direction: renaming `@foo`/`@@foo` pulls the coupled
     * accessor argument with the SAME bare name (the sigil is re-applied when
     * the variable side writes). No prompt is shown — the name itself dictates
     * the coupling.
     */
    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val accessorArg = when (element) {
            is CrystalInstanceVarAccess, is CrystalClassVarAccess ->
                CrystalAccessorCoupling.findAccessorArgForVar(element)
            else -> null
        } ?: return
        if (accessorArg in allRenames) return
        val elementNewName = allRenames[element] ?: newName
        if (elementNewName.isEmpty()) return
        allRenames[accessorArg] = accessorBareName(elementNewName)
    }

    /**
     * Memberinplace rename cannot learn about additional renames: it applies
     * only the references it resolvels itself, so an inplace @foo rename would
     * rename the variable occurrences but NOT the coupled accessor argument
     * and the call sites — a half-rename turns the code invalid (unbound
     * accessor). The dialog flow (prepareRenaming + ReferencesSearcher)
     * applies the full union, so inplace rename is OFF for the coupled
     * symbol family.
     */
    override fun isInplaceRenameSupported(): Boolean = false

    private fun isAccessorNamedArg(element: PsiElement): Boolean {
        val call = PsiTreeUtil.getParentOfType(
            element,
            CrystalMethodCallExpression::class.java,
            CrystalBareMethodCallExpression::class.java,
        ) ?: return false
        return CrystalAccessorCoupling.isAccessorMacroCall(call) &&
            CrystalAccessorCoupling.accessorNameIdentifier(element) != null
    }

    /** `bar` or `@bar` both mean attribute `bar` — the accessor name has no sigil. */
    private fun accessorBareName(newName: String): String =
        newName.removePrefix("@@").removePrefix("@")
}
