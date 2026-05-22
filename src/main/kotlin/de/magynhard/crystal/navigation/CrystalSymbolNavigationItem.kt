package de.magynhard.crystal.navigation

import com.intellij.ide.projectView.PresentationData
import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import de.magynhard.crystal.structure.StructureKind
import javax.swing.Icon

data class CrystalSymbol(
    val name: String,
    val kind: StructureKind,
    val element: PsiElement
)

class CrystalSymbolNavigationItem(private val symbol: CrystalSymbol) : NavigationItem {

    override fun getName(): String = symbol.name

    override fun getPresentation(): ItemPresentation {
        val icon: Icon = when (symbol.kind) {
            StructureKind.CLASS -> AllIcons.Nodes.Class
            StructureKind.MODULE -> AllIcons.Nodes.Module
            StructureKind.STRUCT -> AllIcons.Nodes.Record
            StructureKind.ENUM -> AllIcons.Nodes.Enum
            StructureKind.METHOD -> AllIcons.Nodes.Method
            StructureKind.MACRO -> AllIcons.Nodes.Template
            StructureKind.LIB -> AllIcons.Nodes.PpLib
            StructureKind.ANNOTATION -> AllIcons.Nodes.Annotationtype
        }
        val location = symbol.element.containingFile?.name ?: ""
        return PresentationData(symbol.name, location, icon, null)
    }

    override fun navigate(requestFocus: Boolean) {
        (symbol.element as? NavigationItem)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean =
        symbol.element is NavigationItem && (symbol.element as NavigationItem).canNavigate()

    override fun canNavigateToSource(): Boolean = canNavigate()
}
