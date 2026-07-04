package de.magynhard.crystal.navigation

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.psi.PsiElement
import de.magynhard.crystal.psi.CrystalNamedElement

/**
 * Factory for Crystal's Find Usages and Rename support for definition names.
 *
 * When the user invokes Find Usages (Alt+F7) or Rename (Shift+F6) on a
 * definition element (class, module, struct, enum, method, macro), this factory
 * creates a handler that finds all references to the element.
 *
 * Without this factory, the platform falls back to the default handler which
 * can't resolve references from definition names because the CONSTANT/IDENTIFIER
 * leaf inside the definition has no PsiReference.
 */
class CrystalFindUsagesHandlerFactory : FindUsagesHandlerFactory() {

    override fun canFindUsages(element: PsiElement): Boolean {
        return element is CrystalNamedElement
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        return CrystalFindUsagesHandler(element)
    }
}
