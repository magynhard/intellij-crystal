// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalExpression extends PsiElement {

  @NotNull
  List<CrystalArgumentList> getArgumentListList();

  @NotNull
  List<CrystalArrayLiteral> getArrayLiteralList();

  @NotNull
  List<CrystalAsmExpression> getAsmExpressionList();

  @NotNull
  List<CrystalBeginStatement> getBeginStatementList();

  @NotNull
  List<CrystalBlock> getBlockList();

  @NotNull
  List<CrystalCallArgs> getCallArgsList();

  @NotNull
  List<CrystalCaseStatement> getCaseStatementList();

  @NotNull
  List<CrystalClassVarAccess> getClassVarAccessList();

  @NotNull
  List<CrystalDotCallAccess> getDotCallAccessList();

  @NotNull
  List<CrystalExpression> getExpressionList();

  @NotNull
  List<CrystalGroupedExpression> getGroupedExpressionList();

  @NotNull
  List<CrystalHashLiteral> getHashLiteralList();

  @NotNull
  List<CrystalHeredocLiteral> getHeredocLiteralList();

  @NotNull
  List<CrystalIfStatement> getIfStatementList();

  @NotNull
  List<CrystalImplicitObjectCall> getImplicitObjectCallList();

  @NotNull
  List<CrystalInstanceSizeofExpression> getInstanceSizeofExpressionList();

  @NotNull
  List<CrystalInstanceVarAccess> getInstanceVarAccessList();

  @NotNull
  List<CrystalMacroInterpolation> getMacroInterpolationList();

  @NotNull
  List<CrystalMethodCallExpression> getMethodCallExpressionList();

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
  List<CrystalSizeofExpression> getSizeofExpressionList();

  @NotNull
  List<CrystalStringExpression> getStringExpressionList();

  @NotNull
  List<CrystalSymbolStringExpression> getSymbolStringExpressionList();

  @NotNull
  List<CrystalTupleLiteral> getTupleLiteralList();

  @NotNull
  List<CrystalTypeReference> getTypeReferenceList();

  @NotNull
  List<CrystalTypeofExpression> getTypeofExpressionList();

  @NotNull
  List<CrystalUninitializedExpression> getUninitializedExpressionList();

  @NotNull
  List<CrystalUnlessStatement> getUnlessStatementList();

  @NotNull
  List<CrystalVariableReference> getVariableReferenceList();

  @NotNull
  List<CrystalYieldExpression> getYieldExpressionList();

}
