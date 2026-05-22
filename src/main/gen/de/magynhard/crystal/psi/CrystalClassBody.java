// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalClassBody extends PsiElement {

  @NotNull
  List<CrystalAliasDefinition> getAliasDefinitionList();

  @NotNull
  List<CrystalClassDefinition> getClassDefinitionList();

  @NotNull
  List<CrystalEnumDefinition> getEnumDefinitionList();

  @NotNull
  List<CrystalExtendStatement> getExtendStatementList();

  @NotNull
  List<CrystalIncludeStatement> getIncludeStatementList();

  @NotNull
  List<CrystalMacroDefinition> getMacroDefinitionList();

  @NotNull
  List<CrystalMethodDefinition> getMethodDefinitionList();

  @NotNull
  List<CrystalModuleDefinition> getModuleDefinitionList();

  @NotNull
  List<CrystalPropertyDeclaration> getPropertyDeclarationList();

  @NotNull
  List<CrystalStatement> getStatementList();

  @NotNull
  List<CrystalStructDefinition> getStructDefinitionList();

  @NotNull
  List<CrystalVisibilityModifier> getVisibilityModifierList();

}
