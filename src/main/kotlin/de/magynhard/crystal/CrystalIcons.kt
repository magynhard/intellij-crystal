package de.magynhard.crystal

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.LayeredIcon

object CrystalIcons {
    @JvmField
    val FILE = IconLoader.getIcon("/icons/crystal.svg", CrystalIcons::class.java)
    @JvmField
    val SPEC_FILE_BASE = IconLoader.getIcon("/icons/crystal_spec.svg", CrystalIcons::class.java)
    @JvmField
    val SPEC_FILE: javax.swing.Icon = LayeredIcon(2).apply {
        setIcon(SPEC_FILE_BASE, 0)
    }
}
