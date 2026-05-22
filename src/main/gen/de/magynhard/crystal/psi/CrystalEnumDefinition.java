// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import de.magynhard.crystal.stubs.CrystalEnumDefinitionStub;

public interface CrystalEnumDefinition extends PsiElement, StubBasedPsiElement<CrystalEnumDefinitionStub> {

  @Nullable
  CrystalEnumBody getEnumBody();

  @Nullable
  CrystalTypeName getTypeName();

  @Nullable
  CrystalTypeReference getTypeReference();

}
