package de.magynhard.crystal.ecr.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import de.magynhard.crystal.ecr.EmbeddedCrystalFile
import de.magynhard.crystal.ecr.psi.CrystalEcrEcrPart
import de.magynhard.crystal.ecr.psi.CrystalEcrEcrTag

class EcrStructureViewElement(private val element: PsiElement) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = element

    override fun getPresentation(): ItemPresentation {
        return when (element) {
            is EmbeddedCrystalFile -> element.presentation ?: PresentationData(element.name, null, null, null)
            is CrystalEcrEcrTag -> {
                val content = element.ecrBody.text
                val preview = content.take(50).replace("\n", " ").trim()
                PresentationData("<% $preview %>", null, AllIcons.Nodes.Tag, null)
            }
            else -> PresentationData(element.text.take(30), null, null, null)
        }
    }

    override fun getChildren(): Array<TreeElement> {
        return when (element) {
            is EmbeddedCrystalFile -> {
                element.children
                    .filterIsInstance<CrystalEcrEcrPart>()
                    .mapNotNull { it.ecrTag }
                    .map { EcrStructureViewElement(it) }
                    .toTypedArray()
            }
            else -> emptyArray()
        }
    }

    override fun getAlphaSortKey(): String = presentation.presentableText ?: ""

    override fun navigate(requestFocus: Boolean) {
        if (element is NavigationItem) element.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = element is NavigationItem && element.canNavigate()

    override fun canNavigateToSource(): Boolean = element is NavigationItem && element.canNavigateToSource()
}