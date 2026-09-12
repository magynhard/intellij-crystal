// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalRescueClause extends PsiElement {

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @NotNull
  CrystalStatementList getStatementList();

  @Nullable
  CrystalTypeReference getTypeReference();

}
