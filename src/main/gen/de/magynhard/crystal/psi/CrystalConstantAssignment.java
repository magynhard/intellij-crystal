// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import de.magynhard.crystal.stubs.CrystalConstantAssignmentStub;

public interface CrystalConstantAssignment extends CrystalNamedElement, StubBasedPsiElement<CrystalConstantAssignmentStub> {

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalPostfixModifier getPostfixModifier();

}
