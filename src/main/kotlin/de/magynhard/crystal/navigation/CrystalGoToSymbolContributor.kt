package de.magynhard.crystal.navigation

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import de.magynhard.crystal.CrystalFileType
import de.magynhard.crystal.psi.*

class CrystalGoToSymbolContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        val psiManager = PsiManager.getInstance(project)

        FileTypeIndex.processFiles(CrystalFileType, { virtualFile ->
            val psiFile = psiManager.findFile(virtualFile) ?: return@processFiles true
            extractSymbols(psiFile).forEach { processor.process(it.name) }
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
            extractSymbols(psiFile).filter { it.name == name }.forEach { symbol ->
                processor.process(CrystalNavigationItem(symbol))
            }
            true
        }, parameters.searchScope)
    }

    companion object {
        fun extractSymbols(file: PsiElement): List<CrystalSymbol> {
            val symbols = mutableListOf<CrystalSymbol>()
            collectSymbols(file, symbols, allKinds = true)
            return symbols
        }

        fun extractTypes(file: PsiElement): List<CrystalSymbol> {
            val symbols = mutableListOf<CrystalSymbol>()
            collectSymbols(file, symbols, allKinds = false)
            return symbols
        }

        private fun collectSymbols(element: PsiElement, symbols: MutableList<CrystalSymbol>, allKinds: Boolean) {
            when (element) {
                is CrystalClassDefinition -> {
                    val name = element.name ?: return
                    symbols.add(CrystalSymbol(name, CrystalSymbolKind.CLASS, element))
                    element.classBody?.let { collectSymbols(it, symbols, allKinds) }
                }
                is CrystalModuleDefinition -> {
                    val name = element.name ?: return
                    symbols.add(CrystalSymbol(name, CrystalSymbolKind.MODULE, element))
                    element.classBody?.let { collectSymbols(it, symbols, allKinds) }
                }
                is CrystalStructDefinition -> {
                    val name = element.name ?: return
                    symbols.add(CrystalSymbol(name, CrystalSymbolKind.STRUCT, element))
                    element.classBody?.let { collectSymbols(it, symbols, allKinds) }
                }
                is CrystalEnumDefinition -> {
                    val name = element.name ?: return
                    symbols.add(CrystalSymbol(name, CrystalSymbolKind.ENUM, element))
                }
                is CrystalLibDefinition -> {
                    val name = element.text.substringAfter("lib").trim().substringBefore("\n").trim()
                    symbols.add(CrystalSymbol(name, CrystalSymbolKind.LIB, element))
                }
                is CrystalAnnotationDefinition -> {
                    val name = element.node.findChildByType(CrystalTypes.CONSTANT)?.text ?: return
                    symbols.add(CrystalSymbol(name, CrystalSymbolKind.ANNOTATION, element))
                }
                is CrystalMethodDefinition -> {
                    if (allKinds) {
                        val name = element.name ?: return
                        symbols.add(CrystalSymbol(name, CrystalSymbolKind.METHOD, element))
                    }
                }
                is CrystalMacroDefinition -> {
                    if (allKinds) {
                        val name = element.name ?: return
                        symbols.add(CrystalSymbol(name, CrystalSymbolKind.MACRO, element))
                    }
                }
                is CrystalAliasDefinition -> {
                    val name = element.node.findChildByType(CrystalTypes.CONSTANT)?.text ?: return
                    symbols.add(CrystalSymbol(name, CrystalSymbolKind.ALIAS, element))
                }
                is CrystalConstantAssignment -> {
                    if (allKinds) {
                        val name = element.firstChild?.text ?: return
                        symbols.add(CrystalSymbol(name, CrystalSymbolKind.CONSTANT, element))
                    }
                }
                else -> element.children.forEach { collectSymbols(it, symbols, allKinds) }
            }
        }
    }
}
