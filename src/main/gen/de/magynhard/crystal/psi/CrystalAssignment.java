// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalAssignment extends PsiElement {

  @Nullable
  CrystalAssignment getAssignment();

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @Nullable
  CrystalPostfixModifier getPostfixModifier();

}
