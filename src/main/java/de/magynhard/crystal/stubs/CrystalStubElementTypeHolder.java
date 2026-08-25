package de.magynhard.crystal.stubs;

import com.intellij.psi.tree.IElementType;

/**
 * Holds all stub element type constants for the Crystal plugin.
 *
 * Required by the "stubElementTypeHolder" extension. Must be an interface whose fields
 * are {@code public static final} {@link IElementType} constants so the platform can
 * enumerate them without class initialization (see StubElementTypeHolderEP contract):
 * each field name equals the debug name of its element type, and every external ID
 * equals {@code "crystal." + fieldName}.
 */
public interface CrystalStubElementTypeHolder {
  IElementType CLASS_DEFINITION = new CrystalClassDefinitionElementType("CLASS_DEFINITION");
  IElementType MODULE_DEFINITION = new CrystalModuleDefinitionElementType("MODULE_DEFINITION");
  IElementType STRUCT_DEFINITION = new CrystalStructDefinitionElementType("STRUCT_DEFINITION");
  IElementType ENUM_DEFINITION = new CrystalEnumDefinitionElementType("ENUM_DEFINITION");
  IElementType METHOD_DEFINITION = new CrystalMethodDefinitionElementType("METHOD_DEFINITION");
  IElementType MACRO_DEFINITION = new CrystalMacroDefinitionElementType("MACRO_DEFINITION");
  IElementType LIB_DEFINITION = new CrystalLibDefinitionElementType("LIB_DEFINITION");
  IElementType ANNOTATION_DEFINITION = new CrystalAnnotationDefinitionElementType("ANNOTATION_DEFINITION");
  IElementType ALIAS_DEFINITION = new CrystalAliasDefinitionElementType("ALIAS_DEFINITION");
}
