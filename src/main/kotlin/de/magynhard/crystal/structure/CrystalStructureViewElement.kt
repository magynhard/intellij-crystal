package de.magynhard.crystal.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.navigation.NavigationItem
import com.intellij.icons.AllIcons
import de.magynhard.crystal.lexer.CrystalTokenTypes
import javax.swing.Icon

class CrystalStructureViewElement(private val element: PsiElement) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) {
        if (element is NavigationItem) {
            element.navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean =
        element is NavigationItem && element.canNavigate()

    override fun canNavigateToSource(): Boolean =
        element is NavigationItem && element.canNavigateToSource()

    override fun getAlphaSortKey(): String = presentation.presentableText ?: ""

    override fun getPresentation(): ItemPresentation {
        if (element is PsiFile) {
            return element.presentation ?: PresentationData(element.name, null, null, null)
        }
        // For structure entries we create custom presentations
        return PresentationData(element.text, null, null, null)
    }

    override fun getChildren(): Array<TreeElement> {
        if (element !is PsiFile) return TreeElement.EMPTY_ARRAY

        val file = element
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile) ?: return TreeElement.EMPTY_ARRAY
        val text = document.text

        val entries = mutableListOf<StructureEntry>()
        val stack = mutableListOf<StructureEntry>()

        // Scan through PSI elements to find structure-defining keywords
        val elements = PsiTreeUtil.collectElements(file) { true }

        var i = 0
        while (i < elements.size) {
            val psiElement = elements[i]
            val tokenType = psiElement.node?.elementType

            when (tokenType) {
                CrystalTokenTypes.CLASS, CrystalTokenTypes.MODULE, CrystalTokenTypes.STRUCT,
                CrystalTokenTypes.ENUM, CrystalTokenTypes.LIB, CrystalTokenTypes.ANNOTATION -> {
                    val name = findNextName(elements, i)
                    val kind = when (tokenType) {
                        CrystalTokenTypes.CLASS -> StructureKind.CLASS
                        CrystalTokenTypes.MODULE -> StructureKind.MODULE
                        CrystalTokenTypes.STRUCT -> StructureKind.STRUCT
                        CrystalTokenTypes.ENUM -> StructureKind.ENUM
                        CrystalTokenTypes.LIB -> StructureKind.LIB
                        CrystalTokenTypes.ANNOTATION -> StructureKind.ANNOTATION
                        else -> StructureKind.CLASS
                    }
                    val entry = StructureEntry(name, kind, psiElement, mutableListOf())
                    if (stack.isNotEmpty()) {
                        stack.last().children.add(entry)
                    } else {
                        entries.add(entry)
                    }
                    stack.add(entry)
                }
                CrystalTokenTypes.DEF -> {
                    val name = findNextName(elements, i)
                    val entry = StructureEntry(name, StructureKind.METHOD, psiElement, mutableListOf())
                    if (stack.isNotEmpty()) {
                        stack.last().children.add(entry)
                    } else {
                        entries.add(entry)
                    }
                    // Methods don't push to stack (they have 'end' but don't nest structure)
                    // We still need to track their 'end' though - simplified approach:
                    // don't push, their end will be consumed by the parent or the counter
                }
                CrystalTokenTypes.MACRO -> {
                    val name = findNextName(elements, i)
                    val entry = StructureEntry(name, StructureKind.MACRO, psiElement, mutableListOf())
                    if (stack.isNotEmpty()) {
                        stack.last().children.add(entry)
                    } else {
                        entries.add(entry)
                    }
                }
                CrystalTokenTypes.END -> {
                    if (stack.isNotEmpty()) {
                        stack.removeAt(stack.lastIndex)
                    }
                }
                else -> {}
            }
            i++
        }

        return entries.map { CrystalStructureTreeElement(it) }.toTypedArray()
    }

    private fun findNextName(elements: Array<PsiElement>, startIndex: Int): String {
        // Look ahead for the next IDENTIFIER or CONSTANT after the keyword
        for (j in (startIndex + 1) until minOf(startIndex + 6, elements.size)) {
            val type = elements[j].node?.elementType
            if (type == CrystalTokenTypes.IDENTIFIER || type == CrystalTokenTypes.CONSTANT) {
                return elements[j].text
            }
            // Skip whitespace and self/dot (for self.method)
            if (type == CrystalTokenTypes.SELF) {
                // Could be def self.method_name
                continue
            }
            if (type == CrystalTokenTypes.DOT) continue
            if (type == CrystalTokenTypes.WHITE_SPACE || type == CrystalTokenTypes.NEWLINE) continue
        }
        return "<anonymous>"
    }
}

enum class StructureKind {
    CLASS, MODULE, STRUCT, ENUM, METHOD, MACRO, LIB, ANNOTATION
}

data class StructureEntry(
    val name: String,
    val kind: StructureKind,
    val element: PsiElement,
    val children: MutableList<StructureEntry>
)

class CrystalStructureTreeElement(private val entry: StructureEntry) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = entry.element

    override fun navigate(requestFocus: Boolean) {
        if (entry.element is NavigationItem) {
            (entry.element as NavigationItem).navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean =
        entry.element is NavigationItem

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun getAlphaSortKey(): String = entry.name

    override fun getPresentation(): ItemPresentation {
        val icon: Icon = when (entry.kind) {
            StructureKind.CLASS -> AllIcons.Nodes.Class
            StructureKind.MODULE -> AllIcons.Nodes.Module
            StructureKind.STRUCT -> AllIcons.Nodes.Record
            StructureKind.ENUM -> AllIcons.Nodes.Enum
            StructureKind.METHOD -> AllIcons.Nodes.Method
            StructureKind.MACRO -> AllIcons.Nodes.Template
            StructureKind.LIB -> AllIcons.Nodes.PpLib
            StructureKind.ANNOTATION -> AllIcons.Nodes.Annotationtype
        }
        return PresentationData(entry.name, entry.kind.name.lowercase(), icon, null)
    }

    override fun getChildren(): Array<TreeElement> {
        return entry.children.map { CrystalStructureTreeElement(it) }.toTypedArray()
    }
}
