package de.magynhard.crystal.psi

import com.intellij.psi.tree.IElementType
import de.magynhard.crystal.CrystalLanguage

class CrystalElementType(debugName: String) : IElementType(debugName, CrystalLanguage) {
    override fun toString(): String = "CrystalElementType.${super.toString()}"
}
