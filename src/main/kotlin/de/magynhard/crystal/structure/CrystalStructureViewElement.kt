package de.magynhard.crystal.structure

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.magynhard.crystal.psi.*
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
        val (name, icon, location) = getElementInfo(element)
        return PresentationData(name, location, icon, null)
    }

    override fun getChildren(): Array<TreeElement> {
        val childElements = when (element) {
            is PsiFile -> collectTopLevelDefinitions(element)
            is CrystalClassDefinition -> collectClassMembers(element.classBody)
            is CrystalModuleDefinition -> collectClassMembers(element.classBody)
            is CrystalStructDefinition -> collectClassMembers(element.classBody)
            is CrystalEnumDefinition -> collectEnumMembers(element.enumBody)
            else -> emptyList()
        }
        return childElements.map { CrystalStructureViewElement(it) }.toTypedArray()
    }

    private fun collectTopLevelDefinitions(file: PsiFile): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        file.children.forEach { collectDefinitions(it, result) }
        return result
    }

    private fun collectDefinitions(element: PsiElement, result: MutableList<PsiElement>) {
        when (element) {
            is CrystalClassDefinition,
            is CrystalModuleDefinition,
            is CrystalStructDefinition,
            is CrystalEnumDefinition,
            is CrystalLibDefinition,
            is CrystalAnnotationDefinition,
            is CrystalMethodDefinition,
            is CrystalMacroDefinition,
            is CrystalAliasDefinition,
            is CrystalConstantAssignment -> result.add(element)
            is CrystalVisibilityModifier -> {
                // Look inside visibility modifier for the actual definition
                element.children.forEach { collectDefinitions(it, result) }
            }
            else -> element.children.forEach { collectDefinitions(it, result) }
        }
    }

    private fun collectClassMembers(classBody: CrystalClassBody?): List<PsiElement> {
        if (classBody == null) return emptyList()
        val result = mutableListOf<PsiElement>()
        classBody.children.forEach { collectDefinitions(it, result) }
        return result
    }

    private fun collectEnumMembers(enumBody: CrystalEnumBody?): List<PsiElement> {
        if (enumBody == null) return emptyList()
        val result = mutableListOf<PsiElement>()
        PsiTreeUtil.findChildrenOfAnyType(
            enumBody,
            CrystalEnumConstant::class.java,
            CrystalMethodDefinition::class.java
        ).forEach { result.add(it) }
        return result
    }

    private fun getElementInfo(element: PsiElement): Triple<String, Icon?, String?> {
        return when (element) {
            is CrystalClassDefinition -> Triple(
                element.name ?: "<anonymous>",
                AllIcons.Nodes.Class,
                "class"
            )
            is CrystalModuleDefinition -> Triple(
                element.name ?: "<anonymous>",
                AllIcons.Nodes.Module,
                "module"
            )
            is CrystalStructDefinition -> Triple(
                element.name ?: "<anonymous>",
                AllIcons.Nodes.Record,
                "struct"
            )
            is CrystalEnumDefinition -> Triple(
                element.name ?: "<anonymous>",
                AllIcons.Nodes.Enum,
                "enum"
            )
            is CrystalLibDefinition -> Triple(
                element.text.substringAfter("lib").trim().substringBefore("\n").trim(),
                AllIcons.Nodes.PpLib,
                "lib"
            )
            is CrystalAnnotationDefinition -> Triple(
                element.node.findChildByType(CrystalTypes.CONSTANT)?.text ?: "<anonymous>",
                AllIcons.Nodes.Annotationtype,
                "annotation"
            )
            is CrystalMethodDefinition -> {
                val name = element.name ?: "<anonymous>"
                val params = element.parameterList?.text ?: ""
                val returnType = element.typeReference?.text?.let { " : $it" } ?: ""
                Triple("$name($params)$returnType", AllIcons.Nodes.Method, "def")
            }
            is CrystalMacroDefinition -> {
                val name = element.name ?: "<anonymous>"
                val params = element.parameterList?.text ?: ""
                Triple("$name($params)", AllIcons.Nodes.Template, "macro")
            }
            is CrystalAliasDefinition -> Triple(
                element.node.findChildByType(CrystalTypes.CONSTANT)?.text ?: "<anonymous>",
                AllIcons.Nodes.Type,
                "alias"
            )
            is CrystalConstantAssignment -> Triple(
                element.firstChild?.text ?: "<anonymous>",
                AllIcons.Nodes.Constant,
                "constant"
            )
            is CrystalEnumConstant -> Triple(
                element.firstChild?.text ?: "<anonymous>",
                AllIcons.Nodes.Constant,
                null
            )
            else -> Triple(element.text.take(30), null, null)
        }
    }
}
