// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalMultiAssignTarget extends PsiElement {

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @NotNull
  List<CrystalMultiAssignTarget> getMultiAssignTargetList();

  @Nullable
  CrystalTypeReference getTypeReference();

}
