// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import de.magynhard.crystal.stubs.CrystalFunDefinitionStub;

public interface CrystalFunDefinition extends PsiElement, StubBasedPsiElement<CrystalFunDefinitionStub> {

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @Nullable
  CrystalParameterList getParameterList();

  @Nullable
  CrystalStringExpression getStringExpression();

  @Nullable
  CrystalTypeReference getTypeReference();

}
