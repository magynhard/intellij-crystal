// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalArgument extends PsiElement {

  @Nullable
  CrystalAssignment getAssignment();

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalIfStatement getIfStatement();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @Nullable
  CrystalTypePath getTypePath();

  @Nullable
  CrystalTypeReference getTypeReference();

  @Nullable
  CrystalUnlessStatement getUnlessStatement();

}
