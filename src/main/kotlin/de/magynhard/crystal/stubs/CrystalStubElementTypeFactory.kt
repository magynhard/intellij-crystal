package de.magynhard.crystal.stubs

import com.intellij.psi.tree.IElementType

object CrystalStubElementTypeFactory {
    @JvmStatic
    fun create(name: String): IElementType {
        return when (name) {
            "CLASS_DEFINITION" -> CrystalClassDefinitionElementType(name)
            "MODULE_DEFINITION" -> CrystalModuleDefinitionElementType(name)
            "STRUCT_DEFINITION" -> CrystalStructDefinitionElementType(name)
            "ENUM_DEFINITION" -> CrystalEnumDefinitionElementType(name)
            "METHOD_DEFINITION" -> CrystalMethodDefinitionElementType(name)
            "MACRO_DEFINITION" -> CrystalMacroDefinitionElementType(name)
            else -> throw IllegalArgumentException("Unknown stub element type: $name")
        }
    }
}
