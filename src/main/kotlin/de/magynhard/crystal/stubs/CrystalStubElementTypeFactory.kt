package de.magynhard.crystal.stubs

import com.intellij.psi.tree.IElementType

object CrystalStubElementTypeFactory {
    @JvmStatic
    fun create(name: String): IElementType {
        return when (name) {
            "CLASS_DEFINITION" -> CrystalStubElementTypeHolder.CLASS_DEFINITION
            "MODULE_DEFINITION" -> CrystalStubElementTypeHolder.MODULE_DEFINITION
            "STRUCT_DEFINITION" -> CrystalStubElementTypeHolder.STRUCT_DEFINITION
            "ENUM_DEFINITION" -> CrystalStubElementTypeHolder.ENUM_DEFINITION
            "METHOD_DEFINITION" -> CrystalStubElementTypeHolder.METHOD_DEFINITION
            "MACRO_DEFINITION" -> CrystalStubElementTypeHolder.MACRO_DEFINITION
            "CONSTANT_ASSIGNMENT" -> CrystalStubElementTypeHolder.CONSTANT_ASSIGNMENT
            else -> throw IllegalArgumentException("Unknown stub element type: $name")
        }
    }
}
