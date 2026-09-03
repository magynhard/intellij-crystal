// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalParameter extends PsiElement {

  @NotNull
  List<CrystalAnnotationUsage> getAnnotationUsageList();

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @NotNull
  List<CrystalTypeArguments> getTypeArgumentsList();

  @NotNull
  List<CrystalTypePath> getTypePathList();

  @Nullable
  CrystalTypeReference getTypeReference();

}
