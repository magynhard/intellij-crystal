// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalBareArgument extends PsiElement {

  @NotNull
  List<CrystalArgumentList> getArgumentListList();

  @NotNull
  List<CrystalArrayLiteral> getArrayLiteralList();

  @NotNull
  List<CrystalAsmExpression> getAsmExpressionList();

  @Nullable
  CrystalAssignment getAssignment();

  @NotNull
  List<CrystalBareArgumentList> getBareArgumentListList();

  @NotNull
  List<CrystalBareMethodCallExpression> getBareMethodCallExpressionList();

  @NotNull
  List<CrystalBlock> getBlockList();

  @NotNull
  List<CrystalCallArgs> getCallArgsList();

  @NotNull
  List<CrystalClassVarAccess> getClassVarAccessList();

  @NotNull
  List<CrystalCommandExpression> getCommandExpressionList();

  @NotNull
  List<CrystalDotCallAccess> getDotCallAccessList();

  @Nullable
  CrystalExpression getExpression();

  @NotNull
  List<CrystalGroupedExpression> getGroupedExpressionList();

  @NotNull
  List<CrystalHashEntryList> getHashEntryListList();

  @NotNull
  List<CrystalHashLiteral> getHashLiteralList();

  @Nullable
  CrystalIfStatement getIfStatement();

  @NotNull
  List<CrystalImplicitObjectCall> getImplicitObjectCallList();

  @NotNull
  List<CrystalInstanceSizeofExpression> getInstanceSizeofExpressionList();

  @NotNull
  List<CrystalInstanceVarAccess> getInstanceVarAccessList();

  @NotNull
  List<CrystalMacroControl> getMacroControlList();

  @NotNull
  List<CrystalMacroControlEscaped> getMacroControlEscapedList();

  @NotNull
  List<CrystalMacroFreshVariable> getMacroFreshVariableList();

  @NotNull
  List<CrystalMacroIfEnvelope> getMacroIfEnvelopeList();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @NotNull
  List<CrystalMacroInterpolationEscaped> getMacroInterpolationEscapedList();

  @NotNull
  List<CrystalNamespaceAccess> getNamespaceAccessList();

  @NotNull
  List<CrystalNestedAssignment> getNestedAssignmentList();

  @NotNull
  List<CrystalOffsetofExpression> getOffsetofExpressionList();

  @NotNull
  List<CrystalPercentLiteral> getPercentLiteralList();

  @NotNull
  List<CrystalPointerofExpression> getPointerofExpressionList();

  @NotNull
  List<CrystalProcLiteral> getProcLiteralList();

  @NotNull
  List<CrystalRegexExpression> getRegexExpressionList();

  @NotNull
  List<CrystalRequireStatement> getRequireStatementList();

  @NotNull
  List<CrystalSizeofExpression> getSizeofExpressionList();

  @NotNull
  List<CrystalStringExpression> getStringExpressionList();

  @NotNull
  List<CrystalSymbolStringExpression> getSymbolStringExpressionList();

  @NotNull
  List<CrystalTupleLiteral> getTupleLiteralList();

  @NotNull
  List<CrystalTypePath> getTypePathList();

  @Nullable
  CrystalTypeReference getTypeReference();

  @NotNull
  List<CrystalTypeofExpression> getTypeofExpressionList();

  @NotNull
  List<CrystalUninitializedExpression> getUninitializedExpressionList();

  @Nullable
  CrystalUnlessStatement getUnlessStatement();

  @NotNull
  List<CrystalVariableReference> getVariableReferenceList();

}
