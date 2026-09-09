package de.magynhard.crystal.navigation

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import de.magynhard.crystal.psi.*

/**
 * Custom ReferencesSearcher for instance variables (@name) and class variables (@@name).
 * The standard word-index-based search doesn't work because PsiReference lives on the
 * composite element while processElementsWithWord() finds only the leaf token.
 * This searcher manually finds all matching instance/class var accesses in the enclosing class.
 */
class CrystalInstanceVarReferencesSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val target = queryParameters.elementToSearch
        if (target !is CrystalInstanceVarAccess && target !is CrystalClassVarAccess &&
            !(target is CrystalParameter && target.parameterNameInfo()?.storageName != null)
        ) {
            return
        }

        val access = target ?: return
        val varName = (target as? CrystalParameter)?.parameterNameInfo()?.storageName ?: access.text  // "@name" or "@@name"
        val enclosingClass = findEnclosingClass(access) ?: return
        val classBody = getClassBody(enclosingClass) ?: return

        // Find all matching accesses in the class
        val allAccesses = mutableListOf<PsiElement>()
        collectVarAccesses(classBody, varName, allAccesses)

        // Report each one's reference (except the target itself, if it resolves to itself)
        for (item in allAccesses) {
            if (item === access) continue
            val ref = item.reference ?: continue
            consumer.process(ref)
        }
    }

    private fun collectVarAccesses(element: PsiElement, varName: String, results: MutableList<PsiElement>) {
        if ((element is CrystalInstanceVarAccess || element is CrystalClassVarAccess) &&
            element.text == varName) {
            results.add(element)
            return
        }
        for (child in element.children) {
            // Don't cross into nested types (classes, structs, modules, enums,
            // or body-bearing record declarations)
            if (CrystalPsiUtils.isTypeDefinition(child)) {
                continue
            }
            collectVarAccesses(child, varName, results)
        }
    }

    private fun findEnclosingClass(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(
            element,
            CrystalClassDefinition::class.java,
            CrystalStructDefinition::class.java,
            CrystalModuleDefinition::class.java
        )
    }

    private fun getClassBody(classDef: PsiElement): PsiElement? {
        return when (classDef) {
            is CrystalClassDefinition -> classDef.classBody
            is CrystalStructDefinition -> classDef.classBody
            is CrystalModuleDefinition -> classDef.classBody
            else -> null
        }
    }
}
