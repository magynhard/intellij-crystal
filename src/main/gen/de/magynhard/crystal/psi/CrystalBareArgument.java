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

  @NotNull
  List<CrystalBareMethodCallExpression> getBareMethodCallExpressionList();

  @NotNull
  List<CrystalCallArgs> getCallArgsList();

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
  List<CrystalInstanceSizeofExpression> getInstanceSizeofExpressionList();

  @NotNull
  List<CrystalInstanceVarAccess> getInstanceVarAccessList();

  @NotNull
  List<CrystalPercentLiteral> getPercentLiteralList();

  @NotNull
  List<CrystalPointerofExpression> getPointerofExpressionList();

  @NotNull
  List<CrystalProcLiteral> getProcLiteralList();

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
  List<CrystalVariableReference> getVariableReferenceList();

}
