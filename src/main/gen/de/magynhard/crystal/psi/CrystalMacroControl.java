// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalMacroControl extends PsiElement {

  @NotNull
  List<CrystalAnnotationUsage> getAnnotationUsageList();

  @NotNull
  List<CrystalConstantAssignment> getConstantAssignmentList();

  @NotNull
  List<CrystalEnumDefinition> getEnumDefinitionList();

  @NotNull
  List<CrystalFunDefinition> getFunDefinitionList();

  @NotNull
  List<CrystalLibExternalVar> getLibExternalVarList();

  @NotNull
  List<CrystalLibField> getLibFieldList();

  @NotNull
  List<CrystalLibStructDefinition> getLibStructDefinitionList();

  @NotNull
  List<CrystalLibTypeAlias> getLibTypeAliasList();

  @NotNull
  List<CrystalLibUnionDefinition> getLibUnionDefinitionList();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @NotNull
  List<CrystalTypeAliasLib> getTypeAliasLibList();

}
