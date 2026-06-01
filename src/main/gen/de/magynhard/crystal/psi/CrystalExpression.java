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
  List<CrystalBareArgumentList> getBareArgumentListList();

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
  List<CrystalInstanceSizeofExpression> getInstanceSizeofExpressionList();

  @NotNull
  List<CrystalInstanceVarAccess> getInstanceVarAccessList();

  @NotNull
  List<CrystalMethodCallExpression> getMethodCallExpressionList();

  @NotNull
  List<CrystalPercentLiteral> getPercentLiteralList();

  @NotNull
  List<CrystalPointerofExpression> getPointerofExpressionList();

  @NotNull
  List<CrystalSizeofExpression> getSizeofExpressionList();

  @NotNull
  List<CrystalStringExpression> getStringExpressionList();

  @NotNull
  List<CrystalTupleLiteral> getTupleLiteralList();

  @NotNull
  List<CrystalTypeofExpression> getTypeofExpressionList();

  @NotNull
  List<CrystalUninitializedExpression> getUninitializedExpressionList();

  @NotNull
  List<CrystalUnlessStatement> getUnlessStatementList();

  @NotNull
  List<CrystalVariableReference> getVariableReferenceList();

}
