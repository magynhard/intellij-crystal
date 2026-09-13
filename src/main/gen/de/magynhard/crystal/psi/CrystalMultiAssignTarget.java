// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalMultiAssignTarget extends PsiElement {

  @NotNull
  List<CrystalArgumentList> getArgumentListList();

  @NotNull
  List<CrystalAssignment> getAssignmentList();

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @NotNull
  List<CrystalDotCallAccess> getDotCallAccessList();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @Nullable
  CrystalMacroFreshVariable getMacroFreshVariable();

  @NotNull
  List<CrystalMultiAssignTarget> getMultiAssignTargetList();

  @Nullable
  CrystalTypeReference getTypeReference();

}
