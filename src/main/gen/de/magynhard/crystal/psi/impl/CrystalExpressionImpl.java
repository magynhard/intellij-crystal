// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static de.magynhard.crystal.psi.CrystalTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import de.magynhard.crystal.psi.*;

public class CrystalExpressionImpl extends ASTWrapperPsiElement implements CrystalExpression {

  public CrystalExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CrystalArgumentList> getArgumentListList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalArgumentList.class);
  }

  @Override
  @NotNull
  public List<CrystalArrayLiteral> getArrayLiteralList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalArrayLiteral.class);
  }

  @Override
  @NotNull
  public List<CrystalAsmExpression> getAsmExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalAsmExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalBareCommandExpression> getBareCommandExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalBareCommandExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalBeginStatement> getBeginStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalBeginStatement.class);
  }

  @Override
  @NotNull
  public List<CrystalBlock> getBlockList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalBlock.class);
  }

  @Override
  @NotNull
  public List<CrystalBreakExpression> getBreakExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalBreakExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalCaseStatement> getCaseStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalCaseStatement.class);
  }

  @Override
  @NotNull
  public List<CrystalClassVarAccess> getClassVarAccessList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalClassVarAccess.class);
  }

  @Override
  @NotNull
  public List<CrystalCommandExpression> getCommandExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalCommandExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalDotCallAccess> getDotCallAccessList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalDotCallAccess.class);
  }

  @Override
  @NotNull
  public List<CrystalExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalGroupedExpression> getGroupedExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalGroupedExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalHashLiteral> getHashLiteralList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalHashLiteral.class);
  }

  @Override
  @NotNull
  public List<CrystalHeredocLiteral> getHeredocLiteralList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalHeredocLiteral.class);
  }

  @Override
  @NotNull
  public List<CrystalIfStatement> getIfStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalIfStatement.class);
  }

  @Override
  @NotNull
  public List<CrystalImplicitObjectCall> getImplicitObjectCallList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalImplicitObjectCall.class);
  }

  @Override
  @NotNull
  public List<CrystalInstanceSizeofExpression> getInstanceSizeofExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalInstanceSizeofExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalInstanceVarAccess> getInstanceVarAccessList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalInstanceVarAccess.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroControl> getMacroControlList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroControl.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroInterpolation> getMacroInterpolationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroInterpolation.class);
  }

  @Override
  @NotNull
  public List<CrystalMethodCallExpression> getMethodCallExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMethodCallExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalNamespaceAccess> getNamespaceAccessList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalNamespaceAccess.class);
  }

  @Override
  @NotNull
  public List<CrystalNextExpression> getNextExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalNextExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalOffsetofExpression> getOffsetofExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalOffsetofExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalPercentLiteral> getPercentLiteralList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalPercentLiteral.class);
  }

  @Override
  @NotNull
  public List<CrystalPointerofExpression> getPointerofExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalPointerofExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalProcLiteral> getProcLiteralList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalProcLiteral.class);
  }

  @Override
  @NotNull
  public List<CrystalRegexExpression> getRegexExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalRegexExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalReturnExpression> getReturnExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalReturnExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalSizeofExpression> getSizeofExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalSizeofExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalStringExpression> getStringExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalStringExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalSymbolStringExpression> getSymbolStringExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalSymbolStringExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalTupleLiteral> getTupleLiteralList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTupleLiteral.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeReceiverExpression> getTypeReceiverExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeReceiverExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeReference> getTypeReferenceList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeReference.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeofExpression> getTypeofExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeofExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalUninitializedExpression> getUninitializedExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalUninitializedExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalUnlessStatement> getUnlessStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalUnlessStatement.class);
  }

  @Override
  @NotNull
  public List<CrystalVariableReference> getVariableReferenceList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalVariableReference.class);
  }

  @Override
  @NotNull
  public List<CrystalYieldExpression> getYieldExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalYieldExpression.class);
  }

}
