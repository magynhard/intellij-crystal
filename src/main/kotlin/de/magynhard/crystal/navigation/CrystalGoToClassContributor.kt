package de.magynhard.crystal.navigation

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import de.magynhard.crystal.stubs.*
import de.magynhard.crystal.psi.*

class CrystalGoToClassContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return

        val index = StubIndex.getInstance()
        index.getAllKeys(CrystalClassIndex.KEY, project).forEach { processor.process(it) }
        index.getAllKeys(CrystalAliasIndex.KEY, project).forEach { processor.process(it) }
        index.getAllKeys(CrystalAnnotationIndex.KEY, project).forEach { processor.process(it) }
        index.getAllKeys(CrystalLibIndex.KEY, project).forEach { processor.process(it) }
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters
    ) {
        val project = parameters.project
        val scope = parameters.searchScope

        StubIndex.getElements(CrystalClassIndex.KEY, name, project, scope, CrystalNamedElement::class.java).forEach { element ->
            processor.process(CrystalNavigationItem(CrystalSymbol(name, CrystalSymbolKind.CLASS, element)))
        }
        StubIndex.getElements(CrystalAliasIndex.KEY, name, project, scope, CrystalAliasDefinition::class.java).forEach { element ->
            processor.process(CrystalNavigationItem(CrystalSymbol(name, CrystalSymbolKind.ALIAS, element)))
        }
        StubIndex.getElements(CrystalAnnotationIndex.KEY, name, project, scope, CrystalAnnotationDefinition::class.java).forEach { element ->
            processor.process(CrystalNavigationItem(CrystalSymbol(name, CrystalSymbolKind.ANNOTATION, element)))
        }
        StubIndex.getElements(CrystalLibIndex.KEY, name, project, scope, CrystalLibDefinition::class.java).forEach { element ->
            processor.process(CrystalNavigationItem(CrystalSymbol(name, CrystalSymbolKind.LIB, element)))
        }
    }
}
