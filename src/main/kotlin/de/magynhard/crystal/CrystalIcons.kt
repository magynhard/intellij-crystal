package de.magynhard.crystal

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon

object CrystalIcons {
    @JvmField
    val FILE = IconLoader.getIcon("/icons/crystal.svg", CrystalIcons::class.java)
    @JvmField
    val SPEC_FILE: javax.swing.Icon = LayeredIcon(2).apply {
        setIcon(FILE, 0)
        setIcon(AllIcons.Nodes.JunitTestMark, 1, 0, FILE.iconHeight / 2)
    }
}
