// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalPointerofExpression extends PsiElement {

  @NotNull
  List<CrystalArgumentList> getArgumentListList();

  @Nullable
  CrystalArrayLiteral getArrayLiteral();

  @Nullable
  CrystalAsmExpression getAsmExpression();

  @Nullable
  CrystalBareCommandExpression getBareCommandExpression();

  @Nullable
  CrystalBeginStatement getBeginStatement();

  @NotNull
  List<CrystalBlock> getBlockList();

  @Nullable
  CrystalBreakExpression getBreakExpression();

  @Nullable
  CrystalCaseStatement getCaseStatement();

  @Nullable
  CrystalClassVarAccess getClassVarAccess();

  @Nullable
  CrystalCommandExpression getCommandExpression();

  @NotNull
  List<CrystalDotCallAccess> getDotCallAccessList();

  @NotNull
  List<CrystalExpression> getExpressionList();

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

  @NotNull
  List<CrystalNamespaceAccess> getNamespaceAccessList();

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
  List<CrystalTypeReference> getTypeReferenceList();

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
