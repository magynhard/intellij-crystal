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
    return findChildByClass(CrystalAssignment.class);
  }

  @Override
  @Nullable
  public CrystalBeginStatement getBeginStatement() {
    return findChildByClass(CrystalBeginStatement.class);
  }

  @Override
  @Nullable
  public CrystalBreakStatement getBreakStatement() {
    return findChildByClass(CrystalBreakStatement.class);
  }

  @Override
  @Nullable
  public CrystalCaseStatement getCaseStatement() {
    return findChildByClass(CrystalCaseStatement.class);
  }

  @Override
  @Nullable
  public CrystalConstantAssignment getConstantAssignment() {
    return findChildByClass(CrystalConstantAssignment.class);
  }

  @Override
  @Nullable
  public CrystalExpression getExpression() {
    return findChildByClass(CrystalExpression.class);
  }

  @Override
  @Nullable
  public CrystalForStatement getForStatement() {
    return findChildByClass(CrystalForStatement.class);
  }

  @Override
  @Nullable
  public CrystalIfStatement getIfStatement() {
    return findChildByClass(CrystalIfStatement.class);
  }

  @Override
  @Nullable
  public CrystalNextStatement getNextStatement() {
    return findChildByClass(CrystalNextStatement.class);
  }

  @Override
  @Nullable
  public CrystalReturnStatement getReturnStatement() {
    return findChildByClass(CrystalReturnStatement.class);
  }

  @Override
  @Nullable
  public CrystalUnlessStatement getUnlessStatement() {
    return findChildByClass(CrystalUnlessStatement.class);
  }

  @Override
  @Nullable
  public CrystalUntilStatement getUntilStatement() {
    return findChildByClass(CrystalUntilStatement.class);
  }

  @Override
  @Nullable
  public CrystalWhileStatement getWhileStatement() {
    return findChildByClass(CrystalWhileStatement.class);
  }

  @Override
  @Nullable
  public CrystalYieldStatement getYieldStatement() {
    return findChildByClass(CrystalYieldStatement.class);
  }

}
