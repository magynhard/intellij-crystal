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
  CrystalExpressionStatement getExpressionStatement();

  @Nullable
  CrystalExtendStatement getExtendStatement();

  @Nullable
  CrystalForStatement getForStatement();

  @Nullable
  CrystalIfStatement getIfStatement();

  @Nullable
  CrystalIncludeStatement getIncludeStatement();

  @Nullable
  CrystalMultiAssignment getMultiAssignment();

  @Nullable
  CrystalNextStatement getNextStatement();

  @Nullable
  CrystalPropertyDeclaration getPropertyDeclaration();

  @Nullable
  CrystalReturnStatement getReturnStatement();

  @Nullable
  CrystalSelectStatement getSelectStatement();

  @Nullable
  CrystalUnlessStatement getUnlessStatement();

  @Nullable
  CrystalUntilStatement getUntilStatement();

  @Nullable
  CrystalWhileStatement getWhileStatement();

  @Nullable
  CrystalWithYieldStatement getWithYieldStatement();

  @Nullable
  CrystalYieldStatement getYieldStatement();

}
