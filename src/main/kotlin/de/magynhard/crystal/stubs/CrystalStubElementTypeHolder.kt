package de.magynhard.crystal.stubs

/**
 * Holds all stub element type constants for the Crystal plugin.
 *
 * Required by IntelliJ 2026.1+ via the "stubElementTypeHolder" extension.
 * All IStubElementType instances must be created as static fields in this class
 * before index initialization completes.
 */
object CrystalStubElementTypeHolder {
    @JvmField val CLASS_DEFINITION = CrystalClassDefinitionElementType("CLASS_DEFINITION")
    @JvmField val MODULE_DEFINITION = CrystalModuleDefinitionElementType("MODULE_DEFINITION")
    @JvmField val STRUCT_DEFINITION = CrystalStructDefinitionElementType("STRUCT_DEFINITION")
    @JvmField val ENUM_DEFINITION = CrystalEnumDefinitionElementType("ENUM_DEFINITION")
    @JvmField val METHOD_DEFINITION = CrystalMethodDefinitionElementType("METHOD_DEFINITION")
    @JvmField val MACRO_DEFINITION = CrystalMacroDefinitionElementType("MACRO_DEFINITION")
}
