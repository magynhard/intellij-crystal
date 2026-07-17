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

public class CrystalPointerofExpressionImpl extends ASTWrapperPsiElement implements CrystalPointerofExpression {

  public CrystalPointerofExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitPointerofExpression(this);
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
  @Nullable
  public CrystalArrayLiteral getArrayLiteral() {
    return PsiTreeUtil.getChildOfType(this, CrystalArrayLiteral.class);
  }

  @Override
  @Nullable
  public CrystalAsmExpression getAsmExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalAsmExpression.class);
  }

  @Override
  @Nullable
  public CrystalBareCommandExpression getBareCommandExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalBareCommandExpression.class);
  }

  @Override
  @Nullable
  public CrystalBeginStatement getBeginStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalBeginStatement.class);
  }

  @Override
  @NotNull
  public List<CrystalBlock> getBlockList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalBlock.class);
  }

  @Override
  @Nullable
  public CrystalBreakExpression getBreakExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalBreakExpression.class);
  }

  @Override
  @Nullable
  public CrystalCaseStatement getCaseStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalCaseStatement.class);
  }

  @Override
  @Nullable
  public CrystalClassVarAccess getClassVarAccess() {
    return PsiTreeUtil.getChildOfType(this, CrystalClassVarAccess.class);
  }

  @Override
  @Nullable
  public CrystalCommandExpression getCommandExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalCommandExpression.class);
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
  @Nullable
  public CrystalGroupedExpression getGroupedExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalGroupedExpression.class);
  }

  @Override
  @Nullable
  public CrystalHashLiteral getHashLiteral() {
    return PsiTreeUtil.getChildOfType(this, CrystalHashLiteral.class);
  }

  @Override
  @Nullable
  public CrystalHeredocLiteral getHeredocLiteral() {
    return PsiTreeUtil.getChildOfType(this, CrystalHeredocLiteral.class);
  }

  @Override
  @Nullable
  public CrystalIfStatement getIfStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalIfStatement.class);
  }

  @Override
  @Nullable
  public CrystalImplicitObjectCall getImplicitObjectCall() {
    return PsiTreeUtil.getChildOfType(this, CrystalImplicitObjectCall.class);
  }

  @Override
  @Nullable
  public CrystalInstanceSizeofExpression getInstanceSizeofExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalInstanceSizeofExpression.class);
  }

  @Override
  @Nullable
  public CrystalInstanceVarAccess getInstanceVarAccess() {
    return PsiTreeUtil.getChildOfType(this, CrystalInstanceVarAccess.class);
  }

  @Override
  @Nullable
  public CrystalMacroControl getMacroControl() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroControl.class);
  }

  @Override
  @Nullable
  public CrystalMacroInterpolation getMacroInterpolation() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroInterpolation.class);
  }

  @Override
  @Nullable
  public CrystalMethodCallExpression getMethodCallExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalMethodCallExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalNamespaceAccess> getNamespaceAccessList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalNamespaceAccess.class);
  }

  @Override
  @Nullable
  public CrystalNextExpression getNextExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalNextExpression.class);
  }

  @Override
  @Nullable
  public CrystalOffsetofExpression getOffsetofExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalOffsetofExpression.class);
  }

  @Override
  @Nullable
  public CrystalPercentLiteral getPercentLiteral() {
    return PsiTreeUtil.getChildOfType(this, CrystalPercentLiteral.class);
  }

  @Override
  @Nullable
  public CrystalPointerofExpression getPointerofExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalPointerofExpression.class);
  }

  @Override
  @Nullable
  public CrystalProcLiteral getProcLiteral() {
    return PsiTreeUtil.getChildOfType(this, CrystalProcLiteral.class);
  }

  @Override
  @Nullable
  public CrystalRegexExpression getRegexExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalRegexExpression.class);
  }

  @Override
  @Nullable
  public CrystalReturnExpression getReturnExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalReturnExpression.class);
  }

  @Override
  @Nullable
  public CrystalSizeofExpression getSizeofExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalSizeofExpression.class);
  }

  @Override
  @Nullable
  public CrystalStringExpression getStringExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalStringExpression.class);
  }

  @Override
  @Nullable
  public CrystalSymbolStringExpression getSymbolStringExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalSymbolStringExpression.class);
  }

  @Override
  @Nullable
  public CrystalTupleLiteral getTupleLiteral() {
    return PsiTreeUtil.getChildOfType(this, CrystalTupleLiteral.class);
  }

  @Override
  @Nullable
  public CrystalTypeReceiverExpression getTypeReceiverExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReceiverExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeReference> getTypeReferenceList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeReference.class);
  }

  @Override
  @Nullable
  public CrystalTypeofExpression getTypeofExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeofExpression.class);
  }

  @Override
  @Nullable
  public CrystalUninitializedExpression getUninitializedExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalUninitializedExpression.class);
  }

  @Override
  @Nullable
  public CrystalUnlessStatement getUnlessStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalUnlessStatement.class);
  }

  @Override
  @Nullable
  public CrystalVariableReference getVariableReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalVariableReference.class);
  }

  @Override
  @Nullable
  public CrystalYieldExpression getYieldExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalYieldExpression.class);
  }

}
