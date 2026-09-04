// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalBreakStatement extends CrystalAbruptStatement {

  @NotNull
  List<CrystalAbruptAssignment> getAbruptAssignmentList();

  @NotNull
  List<CrystalExpression> getExpressionList();

  @Nullable
  CrystalHeredocBodies getHeredocBodies();

  @Nullable
  CrystalPostfixModifier getPostfixModifier();

}
