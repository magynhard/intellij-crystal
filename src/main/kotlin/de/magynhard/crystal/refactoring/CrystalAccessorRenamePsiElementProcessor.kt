package de.magynhard.crystal.refactoring

import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import de.magynhard.crystal.psi.CrystalClassVarAccess
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.parameterNameInfo
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
        // The IDE resolves the caret on a storage shortcut (`initialize(@foo)`)
        // to the PARAMETER composite, not the instance-var access — without
        // this acceptance the default processor would handle the rename and
        // the coupled accessor argument would never join (ameba
        // flow_expression.cr:35 rename left `getter? in_loop` untouched).
        if (element is CrystalParameter && element.parameterNameInfo().storageName != null) return true
        // Same-name family members: a `def in_call_args` beside
        // `getter? in_call_args` renames together with the accessor family —
        // taking the element here forces our processor (non-inplace), so the
        // family runs the dialog flow from the first keystroke instead of an
        // inplace templating that resets and re-prompts afterwards.
        if (element is de.magynhard.crystal.psi.CrystalMethodDefinition &&
            isSameNameFamilyMember(element)
        ) {
            return true
        }
        return isAccessorNamedArg(element)
    }

    private fun isSameNameFamilyMember(def: de.magynhard.crystal.psi.CrystalMethodDefinition): Boolean {
        val name = def.name ?: return false
        val typeDef = PsiTreeUtil.getParentOfType(
            def,
            de.magynhard.crystal.psi.CrystalClassDefinition::class.java,
            de.magynhard.crystal.psi.CrystalStructDefinition::class.java,
            de.magynhard.crystal.psi.CrystalModuleDefinition::class.java,
        ) ?: return false
        return CrystalAccessorCoupling.findAccessorArg(name, typeDef) != null
    }

    /**
     * The reverse rename direction: renaming `@foo`/`@@foo` pulls the coupled
     * accessor argument with the SAME bare name (the sigil is re-applied when
     * the variable side writes). No prompt is shown — the name itself dictates
     * the coupling.
     */
    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val varElement: PsiElement = when {
            element is CrystalInstanceVarAccess || element is CrystalClassVarAccess -> element
            // Storage-shortcut parameter (`initialize(@foo)`): the coupled
            // variable is the parameter's wrapped access composite — the
            // parameter's own text includes the type annotation, so the
            // var-name lookup must run on the access element.
            element is CrystalParameter && element.parameterNameInfo().storageName != null ->
                element.instanceVarAccess ?: element.classVarAccess ?: return
            else -> return
        }
        val accessorArg = CrystalAccessorCoupling.findAccessorArgForVar(varElement) ?: return
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
