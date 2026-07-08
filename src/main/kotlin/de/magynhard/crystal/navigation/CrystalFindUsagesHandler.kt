package de.magynhard.crystal.navigation

import com.intellij.find.findUsages.AbstractFindUsagesDialog
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import de.magynhard.crystal.completion.CrystalCompletionHelper
import de.magynhard.crystal.psi.CrystalDotCallAccess
import de.magynhard.crystal.psi.CrystalMethodDefinition
import de.magynhard.crystal.psi.CrystalNamedElement
import de.magynhard.crystal.stubs.CrystalClassIndex

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

            // 2. For initialize: also find .new call sites on the same class.
            //    Instead of searching for "new" in ALL files (slow), we search for
            //    references to the ENCLOSING CLASS (fast, specific name) and check
            //    if the next sibling is a .new call that resolves to initialize.
            if (element is CrystalMethodDefinition && element.name == "initialize") {
                val project = element.project
                val scope = GlobalSearchScope.projectScope(project)
                val targetElement = element
                val enclosingClassName = CrystalCompletionHelper.getEnclosingClassName(element)
                    ?: return@runBlocking

                // Find the class definition via CrystalClassIndex
                val classElements = StubIndex.getElements(
                    CrystalClassIndex.KEY, enclosingClassName, project, scope,
                    CrystalNamedElement::class.java
                )
                if (classElements.isEmpty()) return@runBlocking

                val classDefinition = classElements.first()
                val classRefs = ReferencesSearch.search(classDefinition, scope, false).findAll()
                for (ref in classRefs) {
                    val refElement = ref.element
                    // Find the .new call: walk nextSibling (skipping whitespace/NLS)
                    val nextSibling = findNextNonWhitespace(refElement)
                    if (nextSibling is CrystalDotCallAccess) {
                        // Check that the dot-call is "new" and resolves to our initialize
                        val methodName = nextSibling.node?.findChildByType(
                            de.magynhard.crystal.psi.CrystalTypes.IDENTIFIER
                        )?.text
                        if (methodName == "new") {
                            val dotRef = nextSibling.reference
                            val resolved = dotRef?.resolve()
                            if (resolved === targetElement) {
                                if (!processor.process(UsageInfo(dotRef))) {
                                    result = false
                                    return@runBlocking
                                }
                            }
                        }
                    }
                }
            }
        }
        return result
    }

    /**
     * Finds the next sibling that is not whitespace or NLS (newline).
     * Used to find CrystalDotCallAccess after a class reference.
     */
    private fun findNextNonWhitespace(element: PsiElement): PsiElement? {
        var sibling = element.nextSibling
        while (sibling != null && (sibling is PsiWhiteSpace
                    || sibling.node?.elementType.toString() == "WHITE_SPACE"
                    || sibling.node?.elementType.toString() == "NLS")) {
            sibling = sibling.nextSibling
        }
        return sibling
    }

    override fun getFindUsagesDialog(
        isSingleFile: Boolean,
        toShowInNewTab: Boolean,
        mustOpenInNewTab: Boolean
    ): AbstractFindUsagesDialog {
        return super.getFindUsagesDialog(isSingleFile, toShowInNewTab, mustOpenInNewTab)
    }
}
