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

public class CrystalStatementImpl extends ASTWrapperPsiElement implements CrystalStatement {

  public CrystalStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalAssignment getAssignment() {
    return PsiTreeUtil.getChildOfType(this, CrystalAssignment.class);
  }

  @Override
  @Nullable
  public CrystalBeginStatement getBeginStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalBeginStatement.class);
  }

  @Override
  @Nullable
  public CrystalBreakStatement getBreakStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalBreakStatement.class);
  }

  @Override
  @Nullable
  public CrystalCaseStatement getCaseStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalCaseStatement.class);
  }

  @Override
  @Nullable
  public CrystalConstantAssignment getConstantAssignment() {
    return PsiTreeUtil.getChildOfType(this, CrystalConstantAssignment.class);
  }

  @Override
  @Nullable
  public CrystalExpressionStatement getExpressionStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalExpressionStatement.class);
  }

  @Override
  @Nullable
  public CrystalForStatement getForStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalForStatement.class);
  }

  @Override
  @Nullable
  public CrystalIfStatement getIfStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalIfStatement.class);
  }

  @Override
  @Nullable
  public CrystalNextStatement getNextStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalNextStatement.class);
  }

  @Override
  @Nullable
  public CrystalReturnStatement getReturnStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalReturnStatement.class);
  }

  @Override
  @Nullable
  public CrystalUnlessStatement getUnlessStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalUnlessStatement.class);
  }

  @Override
  @Nullable
  public CrystalUntilStatement getUntilStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalUntilStatement.class);
  }

  @Override
  @Nullable
  public CrystalWhileStatement getWhileStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalWhileStatement.class);
  }

  @Override
  @Nullable
  public CrystalYieldStatement getYieldStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalYieldStatement.class);
  }

}
