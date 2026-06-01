// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalProcLiteral extends PsiElement {

  @Nullable
  CrystalParameterList getParameterList();

  @Nullable
  CrystalStatementList getStatementList();

  @NotNull
  List<CrystalTypeReference> getTypeReferenceList();

}
