package de.magynhard.crystal.navigation

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import de.magynhard.crystal.CrystalFileType

class CrystalGoToClassContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        val psiManager = PsiManager.getInstance(project)

        FileTypeIndex.processFiles(CrystalFileType, { virtualFile ->
            val psiFile = psiManager.findFile(virtualFile) ?: return@processFiles true
            CrystalGoToSymbolContributor.extractTypes(psiFile).forEach { processor.process(it.name) }
            true
        }, scope)
    }

    override fun processElementsWithName(
        name: String,
        processor: Processor<in NavigationItem>,
        parameters: FindSymbolParameters
    ) {
        val project = parameters.project
        val psiManager = PsiManager.getInstance(project)

        FileTypeIndex.processFiles(CrystalFileType, { virtualFile ->
            val psiFile = psiManager.findFile(virtualFile) ?: return@processFiles true
            CrystalGoToSymbolContributor.extractTypes(psiFile).filter { it.name == name }.forEach { symbol ->
                processor.process(CrystalNavigationItem(symbol))
            }
            true
        }, parameters.searchScope)
    }
}
