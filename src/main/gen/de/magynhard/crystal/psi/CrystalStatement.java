// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalStatement extends PsiElement {

  @Nullable
  CrystalAssignment getAssignment();

  @Nullable
  CrystalBeginStatement getBeginStatement();

  @Nullable
  CrystalBreakStatement getBreakStatement();

  @Nullable
  CrystalCaseStatement getCaseStatement();

  @Nullable
  CrystalConstantAssignment getConstantAssignment();

  @Nullable
  CrystalExpression getExpression();

  @Nullable
  CrystalForStatement getForStatement();

  @Nullable
  CrystalIfStatement getIfStatement();

  @Nullable
  CrystalNextStatement getNextStatement();

  @Nullable
  CrystalReturnStatement getReturnStatement();

  @Nullable
  CrystalUnlessStatement getUnlessStatement();

  @Nullable
  CrystalUntilStatement getUntilStatement();

  @Nullable
  CrystalWhileStatement getWhileStatement();

  @Nullable
  CrystalYieldStatement getYieldStatement();

}
