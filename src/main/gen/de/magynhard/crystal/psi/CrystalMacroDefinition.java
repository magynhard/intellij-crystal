// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import de.magynhard.crystal.stubs.CrystalMacroDefinitionStub;

public interface CrystalMacroDefinition extends CrystalNamedElement, StubBasedPsiElement<CrystalMacroDefinitionStub> {

  @Nullable
  CrystalMethodBody getMethodBody();

  @Nullable
  CrystalMethodName getMethodName();

  @Nullable
  CrystalParameterList getParameterList();

}
