// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import de.magynhard.crystal.stubs.CrystalStructDefinitionStub;

public interface CrystalStructDefinition extends CrystalNamedElement, StubBasedPsiElement<CrystalStructDefinitionStub> {

  @Nullable
  CrystalClassBody getClassBody();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @Nullable
  CrystalSuperclassClause getSuperclassClause();

  @Nullable
  CrystalTypeParameters getTypeParameters();

}
