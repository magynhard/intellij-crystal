package de.magynhard.crystal.ecr.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiFile

class CrystalInstanceVariablesGroupElement(
    private val file: PsiFile
) : StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = file

    override fun getPresentation(): ItemPresentation {
        return PresentationData("@instance_variables", null, AllIcons.Nodes.Variable, null)
    }

    override fun getChildren(): Array<TreeElement> {
        return CrystalInstanceVariableExtractor.extractAll(file)
            .map { CrystalInstanceVariablesStructureViewElement(it) }
            .toTypedArray()
    }

    override fun getAlphaSortKey(): String = "@instance_variables"

    override fun navigate(requestFocus: Boolean) {
        val first = CrystalInstanceVariableExtractor.extractAll(file).firstOrNull() ?: return
        CrystalInstanceVariablesStructureViewElement(first).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean =
        CrystalInstanceVariableExtractor.extractAll(file).isNotEmpty()

    override fun canNavigateToSource(): Boolean = canNavigate()
}
