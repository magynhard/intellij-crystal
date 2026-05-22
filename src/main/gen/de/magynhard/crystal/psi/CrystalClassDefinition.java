// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import de.magynhard.crystal.stubs.CrystalClassDefinitionStub;

public interface CrystalClassDefinition extends PsiElement, StubBasedPsiElement<CrystalClassDefinitionStub> {

  @Nullable
  CrystalClassBody getClassBody();

  @Nullable
  CrystalSuperclassClause getSuperclassClause();

  @Nullable
  CrystalTypeName getTypeName();

  @Nullable
  CrystalTypeParameters getTypeParameters();

}
