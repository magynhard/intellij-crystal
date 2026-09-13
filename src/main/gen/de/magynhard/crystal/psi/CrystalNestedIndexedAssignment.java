// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalNestedIndexedAssignment extends PsiElement {

  @NotNull
  List<CrystalArgumentList> getArgumentListList();

  @NotNull
  List<CrystalAssignment> getAssignmentList();

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @NotNull
  List<CrystalDotCallAccess> getDotCallAccessList();

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalIndexedAssignment getIndexedAssignment();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @Nullable
  CrystalMacroFreshVariable getMacroFreshVariable();

  @Nullable
  CrystalNestedAssignment getNestedAssignment();

}
