// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalLibExternalVar extends PsiElement {

  @Nullable
  CrystalArrayLiteral getArrayLiteral();

  @Nullable
  CrystalAsmExpression getAsmExpression();

  @Nullable
  CrystalBareCommandExpression getBareCommandExpression();

  @Nullable
  CrystalBeginStatement getBeginStatement();

  @Nullable
  CrystalBreakExpression getBreakExpression();

  @Nullable
  CrystalCaseStatement getCaseStatement();

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @Nullable
  CrystalCommandExpression getCommandExpression();

  @Nullable
  CrystalGroupedExpression getGroupedExpression();

  @Nullable
  CrystalHashLiteral getHashLiteral();

  @Nullable
  CrystalHeredocLiteral getHeredocLiteral();

  @Nullable
  CrystalIfStatement getIfStatement();

  @Nullable
  CrystalImplicitObjectCall getImplicitObjectCall();

  @Nullable
  CrystalInstanceSizeofExpression getInstanceSizeofExpression();

  @Nullable
  CrystalInstanceVarAccess getInstanceVarAccess();

  @Nullable
  CrystalMacroControl getMacroControl();

  @Nullable
  CrystalMacroInterpolation getMacroInterpolation();

  @Nullable
  CrystalMethodCallExpression getMethodCallExpression();

  @Nullable
  CrystalNamespaceAccess getNamespaceAccess();

  @Nullable
  CrystalNextExpression getNextExpression();

  @Nullable
  CrystalOffsetofExpression getOffsetofExpression();

  @Nullable
  CrystalPercentLiteral getPercentLiteral();

  @Nullable
  CrystalPointerofExpression getPointerofExpression();

  @Nullable
  CrystalProcLiteral getProcLiteral();

  @Nullable
  CrystalRegexExpression getRegexExpression();

  @Nullable
  CrystalReturnExpression getReturnExpression();

  @Nullable
  CrystalSizeofExpression getSizeofExpression();

  @Nullable
  CrystalStringExpression getStringExpression();

  @Nullable
  CrystalSymbolStringExpression getSymbolStringExpression();

  @Nullable
  CrystalTupleLiteral getTupleLiteral();

  @Nullable
  CrystalTypeReceiverExpression getTypeReceiverExpression();

  @NotNull
  CrystalTypeReference getTypeReference();

  @Nullable
  CrystalTypeofExpression getTypeofExpression();

  @Nullable
  CrystalUninitializedExpression getUninitializedExpression();

  @Nullable
  CrystalUnlessStatement getUnlessStatement();

  @Nullable
  CrystalVariableReference getVariableReference();

  @Nullable
  CrystalYieldExpression getYieldExpression();

}
