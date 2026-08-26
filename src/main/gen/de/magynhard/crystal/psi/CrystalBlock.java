// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalBlock extends PsiElement {

  @Nullable
  CrystalElseClause getElseClause();

  @Nullable
  CrystalEnsureClause getEnsureClause();

  @Nullable
  CrystalParameterList getParameterList();

  @NotNull
  List<CrystalRescueClause> getRescueClauseList();

  @Nullable
  CrystalStatementList getStatementList();

}
