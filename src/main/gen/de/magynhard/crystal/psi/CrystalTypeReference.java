// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalTypeReference extends PsiElement {

  @NotNull
  List<CrystalTypeArguments> getTypeArgumentsList();

  @NotNull
  List<CrystalTypePath> getTypePathList();

  @NotNull
  List<CrystalTypeReference> getTypeReferenceList();

}
