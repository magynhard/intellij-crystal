package de.magynhard.crystal.refactoring

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewShortNameLocation
import de.magynhard.crystal.psi.CrystalClassVarAccess
import de.magynhard.crystal.psi.CrystalInstanceVarAccess
import de.magynhard.crystal.psi.CrystalParameter
import de.magynhard.crystal.psi.parameterNameInfo

/**
 * Rename-dialog hydration for sigil-bearing variables.
 *
 * The rename dialog prefills the name field via
 * `UsageViewUtil.getShortName` → `ElementDescriptionUtil.getElementDescription
 * (element, UsageViewShortNameLocation)`. For instance/class variables the
 * short name used to report the SIGILED token (`@@pooled`, `@in_loop`), so the
 * dialog carried the sigil: every edit grew a second `@@` because setName
 * re-applies the sigil from the token type. The dialog now prefills the BARE
 * name (like the storage-shortcut parameter route, whose composite reports
 * `parameterNameInfo().localName`), while the rename still writes the sigil —
 * `CrystalInstanceVarAccessMixin.setName` / `CrystalClassVarAccessMixin.setName`
 * / `CrystalParameterMixin.setName` strip any typed sigils and re-apply the
 * one dictated by the token type.
 *
 * Only the SHORT-name location is overridden; every other location keeps the
 * platform yields (return null to fall through).
 */
class CrystalElementDescriptionProvider : ElementDescriptionProvider {

    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        if (location !is UsageViewShortNameLocation) return null
        val shortName: String = when (element) {
            is CrystalInstanceVarAccess -> element.text.removePrefix("@@").removePrefix("@")
            is CrystalClassVarAccess -> element.text.removePrefix("@@").removePrefix("@")
            is CrystalParameter -> element.parameterNameInfo().localName ?: return null
            // Method definitions (same-name family members): the platform
            // fallback renders the WHOLE method text including the body in
            // the rename dialog item list — report only the name.
            is de.magynhard.crystal.psi.CrystalMethodDefinition -> element.name ?: return null
            else -> return null
        }
        return shortName
    }
}
