// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalClassDefinition extends PsiElement {

  @Nullable
  CrystalClassBody getClassBody();

  @Nullable
  CrystalSuperclassClause getSuperclassClause();

  @Nullable
  CrystalTypeName getTypeName();

  @Nullable
  CrystalTypeParameters getTypeParameters();

}
