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
import de.magynhard.crystal.lexer.CrystalTokenTypes
import de.magynhard.crystal.structure.StructureKind

class CrystalGoToClassContributor : ChooseByNameContributorEx {

    override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
        val project = scope.project ?: return
        val psiManager = PsiManager.getInstance(project)

        FileTypeIndex.processFiles(CrystalFileType, { virtualFile ->
            val psiFile = psiManager.findFile(virtualFile) ?: return@processFiles true
            extractTypes(psiFile).forEach { processor.process(it.name) }
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
            extractTypes(psiFile).filter { it.name == name }.forEach { symbol ->
                processor.process(CrystalSymbolNavigationItem(symbol))
            }
            true
        }, parameters.searchScope)
    }

    private fun extractTypes(file: PsiElement): List<CrystalSymbol> {
        val symbols = mutableListOf<CrystalSymbol>()
        val elements = PsiTreeUtil.collectElements(file) { true }

        var i = 0
        while (i < elements.size) {
            val tokenType = elements[i].node?.elementType
            val kind = when (tokenType) {
                CrystalTokenTypes.CLASS -> StructureKind.CLASS
                CrystalTokenTypes.MODULE -> StructureKind.MODULE
                CrystalTokenTypes.STRUCT -> StructureKind.STRUCT
                CrystalTokenTypes.ENUM -> StructureKind.ENUM
                CrystalTokenTypes.LIB -> StructureKind.LIB
                CrystalTokenTypes.ANNOTATION -> StructureKind.ANNOTATION
                else -> { i++; continue }
            }

            val name = findNextName(elements, i)
            if (name != "<anonymous>") {
                symbols.add(CrystalSymbol(name, kind, elements[i]))
            }
            i++
        }
        return symbols
    }

    private fun findNextName(elements: Array<PsiElement>, startIndex: Int): String {
        for (j in (startIndex + 1) until minOf(startIndex + 6, elements.size)) {
            val type = elements[j].node?.elementType
            if (type == CrystalTokenTypes.IDENTIFIER || type == CrystalTokenTypes.CONSTANT) {
                return elements[j].text
            }
            if (type == CrystalTokenTypes.SELF || type == CrystalTokenTypes.DOT) continue
            if (type == CrystalTokenTypes.WHITE_SPACE || type == CrystalTokenTypes.NEWLINE) continue
        }
        return "<anonymous>"
    }
}
