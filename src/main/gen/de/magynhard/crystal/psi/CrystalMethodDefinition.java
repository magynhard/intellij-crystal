// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalMethodDefinition extends PsiElement {

  @Nullable
  CrystalMethodBody getMethodBody();

  @Nullable
  CrystalMethodName getMethodName();

  @Nullable
  CrystalParameterList getParameterList();

  @Nullable
  CrystalTypeReference getTypeReference();

}
