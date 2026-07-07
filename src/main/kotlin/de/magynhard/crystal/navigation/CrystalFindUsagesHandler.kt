package de.magynhard.crystal.navigation

import com.intellij.find.findUsages.AbstractFindUsagesDialog
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.TextOccurenceProcessor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition

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
        var result = true
        ReadAction.runBlocking<RuntimeException> {
            val scope = options.searchScope

            // 1. Direct references (standard path)
            val refs = ReferencesSearch.search(element, scope, false).findAll()
            for (ref in refs) {
                if (!processor.process(UsageInfo(ref))) {
                    result = false
                    return@runBlocking
                }
            }

            // 2. For initialize: also find .new call sites on the same class
            //    ReferencesSearch.search(initialize) only finds references containing
            //    the word "initialize", but .new call sites contain the word "new".
            //    Use PsiSearchHelper to find "new" in the word index, then check
            //    if .new references resolve to our initialize.
            if (element is CrystalMethodDefinition && element.name == "initialize") {
                val project = element.project
                val searchScope = GlobalSearchScope.projectScope(project)
                val targetElement = element
                PsiSearchHelper.getInstance(project).processElementsWithWord(
                    object : TextOccurenceProcessor {
                        override fun execute(psiElement: PsiElement, offsetInElement: Int): Boolean {
                            if (psiElement.text != "new") return true
                            val dotCallAccess = psiElement.parent
                            if (dotCallAccess !is CrystalDotCallAccess) return true
                            val ref = dotCallAccess.reference ?: return true
                            val resolved = ref.resolve() ?: return true
                            if (resolved === targetElement) {
                                if (!processor.process(UsageInfo(ref))) return false
                            }
                            return true
                        }
                    },
                    searchScope,
                    "new",
                    1.toShort(),  // normal priority
                    true    // caseSensitive
                )
            }
        }
        return result
    }

    override fun getFindUsagesDialog(
        isSingleFile: Boolean,
        toShowInNewTab: Boolean,
        mustOpenInNewTab: Boolean
    ): AbstractFindUsagesDialog {
        return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab)
    }
}
