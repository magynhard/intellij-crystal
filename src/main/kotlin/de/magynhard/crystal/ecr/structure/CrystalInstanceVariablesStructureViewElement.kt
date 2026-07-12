package de.magynhard.crystal.ecr.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor

class CrystalInstanceVariablesStructureViewElement(
    private val info: InstanceVariableInfo
) : StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = info

    override fun getPresentation(): ItemPresentation {
        return PresentationData(info.name, null, AllIcons.Nodes.Variable, null)
    }

    override fun getChildren(): Array<TreeElement> = emptyArray()

    override fun getAlphaSortKey(): String = info.name

    override fun navigate(requestFocus: Boolean) {
        val file = info.element.containingFile
        val editor = FileEditorManager.getInstance(file.project).selectedEditor

        if (editor is TextEditor) {
            val textEditor = editor.editor
            textEditor.caretModel.moveToOffset(info.firstOffset)
            textEditor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }

    override fun canNavigate(): Boolean = true

    override fun canNavigateToSource(): Boolean = true
}