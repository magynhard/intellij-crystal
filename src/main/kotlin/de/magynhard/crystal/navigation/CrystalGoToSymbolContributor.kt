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
import de.magynhard.crystal.stubs.CrystalMacroIndex
import de.magynhard.crystal.stubs.CrystalMethodIndex

class CrystalGoToSymbolContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        val stubIndex = StubIndex.getInstance()
        val seen = HashSet<String>()

        for (key in stubIndex.getAllKeys(CrystalClassIndex.KEY, project)) {
            if (!seen.add(key)) continue
            if (!processor.process(key)) return
        }
        for (key in stubIndex.getAllKeys(CrystalMethodIndex.KEY, project)) {
            if (!seen.add(key)) continue
            if (!processor.process(key)) return
        }
        for (key in stubIndex.getAllKeys(CrystalMacroIndex.KEY, project)) {
            if (!seen.add(key)) continue
            if (!processor.process(key)) return
        }
    }

    override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
        val project = parameters.project
        val scope = parameters.searchScope

        for (element in StubIndex.getElements(CrystalClassIndex.KEY, name, project, scope, CrystalNamedElement::class.java)) {
            processor.process(CrystalNavigationItem(CrystalSymbol(element.name ?: name, symbolKindFromElement(element), element)))
        }
        for (element in StubIndex.getElements(CrystalMethodIndex.KEY, name, project, scope, CrystalMethodDefinition::class.java)) {
            processor.process(CrystalNavigationItem(CrystalSymbol(element.name ?: name, CrystalSymbolKind.METHOD, element)))
        }
        for (element in StubIndex.getElements(CrystalMacroIndex.KEY, name, project, scope, CrystalMacroDefinition::class.java)) {
            processor.process(CrystalNavigationItem(CrystalSymbol(element.name ?: name, CrystalSymbolKind.MACRO, element)))
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
