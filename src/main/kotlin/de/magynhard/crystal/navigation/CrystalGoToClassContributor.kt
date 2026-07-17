package de.magynhard.crystal.navigation

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalClassIndex

class CrystalGoToClassContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        for (key in StubIndex.getInstance().getAllKeys(CrystalClassIndex.KEY, project)) {
            if (!processor.process(key)) break
        }
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        val project = parameters.project
        val scope = parameters.searchScope
        val elements = StubIndex.getElements(CrystalClassIndex.KEY, name, project, scope, CrystalNamedElement::class.java)
        for (element in elements) {
            val symbol = CrystalSymbol(element.name ?: name, symbolKindFromElement(element), element)
            processor.process(CrystalNavigationItem(symbol))
        }
    }

    private fun symbolKindFromElement(element: PsiElement): CrystalSymbolKind = when (element) {
        is CrystalClassDefinition -> CrystalSymbolKind.CLASS
        is CrystalModuleDefinition -> CrystalSymbolKind.MODULE
        is CrystalStructDefinition -> CrystalSymbolKind.STRUCT
        is CrystalEnumDefinition -> CrystalSymbolKind.ENUM
        else -> CrystalSymbolKind.CLASS
    }
}
