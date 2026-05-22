// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalIfStatement extends PsiElement {

  @Nullable
  CrystalElseClause getElseClause();

  @NotNull
  List<CrystalElsifClause> getElsifClauseList();

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalStatementList getStatementList();

}
