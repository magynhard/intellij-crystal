package de.magynhard.crystal.navigation

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import de.magynhard.crystal.psi.*
import de.magynhard.crystal.stubs.CrystalIndexService

class CrystalGoToClassContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        scope.project ?: return

        if (!CrystalIndexService.processTypeNames(scope, filter, processor)) return
        if (!CrystalIndexService.processAliasNames(scope, filter, processor)) return
        if (!CrystalIndexService.processAnnotationNames(scope, filter, processor)) return
        CrystalIndexService.processLibNames(scope, filter, processor)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters
    ) {
        val project = parameters.project
        val scope = parameters.searchScope

        for (element in CrystalIndexService.findTypes(name, project, scope)) {
            if (!processor.process(CrystalNavigationItem(CrystalSymbol(name, crystalTypeSymbolKind(element), element)))) return
        }
        for (element in CrystalIndexService.findAliases(name, project, scope)) {
            if (!processor.process(CrystalNavigationItem(CrystalSymbol(name, CrystalSymbolKind.ALIAS, element)))) return
        }
        for (element in CrystalIndexService.findAnnotations(name, project, scope)) {
            if (!processor.process(CrystalNavigationItem(CrystalSymbol(name, CrystalSymbolKind.ANNOTATION, element)))) return
        }
        for (element in CrystalIndexService.findLibs(name, project, scope)) {
            if (!processor.process(CrystalNavigationItem(CrystalSymbol(name, CrystalSymbolKind.LIB, element)))) return
        }
    }
}
