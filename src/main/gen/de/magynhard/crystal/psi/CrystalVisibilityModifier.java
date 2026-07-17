// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalVisibilityModifier extends PsiElement {

  @Nullable
  CrystalBareArgumentList getBareArgumentList();

  @Nullable
  CrystalBlock getBlock();

  @Nullable
  CrystalCallArgs getCallArgs();

  @Nullable
  CrystalClassDefinition getClassDefinition();

  @Nullable
  CrystalConstantAssignment getConstantAssignment();

  @Nullable
  CrystalEnumDefinition getEnumDefinition();

  @Nullable
  CrystalMacroDefinition getMacroDefinition();

  @Nullable
  CrystalMethodDefinition getMethodDefinition();

  @Nullable
  CrystalModuleDefinition getModuleDefinition();

  @Nullable
  CrystalPropertyMacro getPropertyMacro();

  @Nullable
  CrystalRecordDefinition getRecordDefinition();

  @Nullable
  CrystalStatement getStatement();

  @Nullable
  CrystalStructDefinition getStructDefinition();

}
