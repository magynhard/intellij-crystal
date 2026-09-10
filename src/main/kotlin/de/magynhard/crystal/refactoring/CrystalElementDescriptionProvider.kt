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
        if (element is de.magynhard.crystal.psi.CrystalMethodDefinition) {
            return when (location) {
                is UsageViewShortNameLocation -> element.name
                // The rename dialog's target display hydrates the WHOLE
                // method text (including the body) from the node-text
                // location — report the header only, up to the parameter
                // list (`private def in_call_args(value = true, &)`).
                is com.intellij.usageView.UsageViewNodeTextLocation -> methodHeaderText(element)
                else -> null
            }
        }
        if (location !is UsageViewShortNameLocation) return null
        val shortName: String = when (element) {
            is CrystalInstanceVarAccess -> element.text.removePrefix("@@").removePrefix("@")
            is CrystalClassVarAccess -> element.text.removePrefix("@@").removePrefix("@")
            is CrystalParameter -> element.parameterNameInfo().localName ?: return null
            else -> return null
        }
        return shortName
    }

    /** The method header line: from the line start through the parameter list. */
    private fun methodHeaderText(def: de.magynhard.crystal.psi.CrystalMethodDefinition): String? {
        val fileText = def.containingFile?.text ?: return null
        var endOffset = def.parameterList?.textRange?.endOffset
            ?: (def.textRange.startOffset + def.text.substringBefore('\n').length)
        // The closing `)` of the parameter list belongs to the def node, not
        // to the parameterList element — include it and its block params.
        while (endOffset < fileText.length && fileText[endOffset].isWhitespace()) endOffset++
        if (endOffset < fileText.length && fileText[endOffset] == ')') endOffset++
        while (endOffset < fileText.length && fileText[endOffset].isWhitespace()) endOffset++
        if (endOffset < fileText.length && fileText[endOffset] == '&') {
            // Trailing block param (`&,` may follow without a param list).
            while (endOffset < fileText.length && fileText[endOffset] != '\n') endOffset++
        }
        var start = def.textRange.startOffset
        while (start > 0 && fileText[start - 1] != '\n') start--
        return fileText.substring(start, endOffset.coerceAtMost(fileText.length))
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { null }
    }
}
