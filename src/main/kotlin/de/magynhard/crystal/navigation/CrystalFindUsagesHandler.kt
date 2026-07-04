package de.magynhard.crystal.navigation

import com.intellij.find.findUsages.AbstractFindUsagesDialog
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

/**
 * Find Usages handler for Crystal definition elements (class, module, struct,
 * enum, method, macro).
 *
 * Finds all PsiReferences pointing to the definition element and converts them
 * to UsageInfo for the Find Usages dialog and Rename processor.
 */
class CrystalFindUsagesHandler(element: PsiElement) : FindUsagesHandler(element) {

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean {
        val scope = options.searchScope
        val refs = ReferencesSearch.search(element, scope, false).findAll()
        for (ref in refs) {
            if (!processor.process(UsageInfo(ref))) return false
        }
        return true
    }

    override fun getFindUsagesDialog(
        isSingleFile: Boolean,
        toShowInNewTab: Boolean,
        mustOpenInNewTab: Boolean
    ): AbstractFindUsagesDialog {
        return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab)
    }
}
