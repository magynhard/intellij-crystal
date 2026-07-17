// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalCaseStatement extends PsiElement {

  @Nullable
  CrystalAssignment getAssignment();

  @Nullable
  CrystalElseClause getElseClause();

  @Nullable
  CrystalExpression getExpression();

  @NotNull
  List<CrystalInClause> getInClauseList();

  @NotNull
  List<CrystalWhenClause> getWhenClauseList();

}
