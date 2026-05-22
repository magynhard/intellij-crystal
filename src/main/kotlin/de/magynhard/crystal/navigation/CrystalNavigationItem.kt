package de.magynhard.crystal.navigation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem

class CrystalNavigationItem(private val symbol: CrystalSymbol) : NavigationItem {

    override fun getName(): String = symbol.name

    override fun getPresentation(): ItemPresentation {
        val location = symbol.element.containingFile?.name ?: ""
        return PresentationData(symbol.name, location, symbol.kind.icon, null)
    }

    override fun navigate(requestFocus: Boolean) {
        (symbol.element as? NavigationItem)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean =
        symbol.element is NavigationItem && (symbol.element as NavigationItem).canNavigate()

    override fun canNavigateToSource(): Boolean = canNavigate()
}
