package de.magynhard.crystal.ecr.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class CrystalInstanceVariablesStructureViewModel(
    psiFile: PsiFile,
    editor: Editor?
) : StructureViewModelBase(psiFile, editor, CrystalInstanceVariablesRootElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean = false
}

class CrystalInstanceVariablesRootElement(
    private val file: PsiFile
) : StructureViewTreeElement {

    override fun getValue(): Any = file

    override fun getPresentation(): ItemPresentation {
        return file.presentation ?: PresentationData(file.name, null, null, null)
    }

    override fun getChildren(): Array<TreeElement> {
        return CrystalInstanceVariableExtractor.extractAll(file)
            .map { CrystalInstanceVariablesStructureViewElement(it) }
            .toTypedArray()
    }

    override fun navigate(requestFocus: Boolean) {
        if (file is NavigationItem) file.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = file is NavigationItem && file.canNavigate()

    override fun canNavigateToSource(): Boolean = file is NavigationItem && file.canNavigateToSource()
}