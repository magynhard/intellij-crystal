// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static de.magynhard.crystal.psi.CrystalTypes.*;
import de.magynhard.crystal.psi.*;

public class CrystalAssignmentImpl extends CrystalAssignmentMixin implements CrystalAssignment {

  public CrystalAssignmentImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitAssignment(this);
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
  public CrystalClassVarAccess getClassVarAccess() {
    return PsiTreeUtil.getChildOfType(this, CrystalClassVarAccess.class);
  }

  @Override
  @Nullable
  public CrystalExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalExpression.class);
  }

  @Override
  @Nullable
  public CrystalInstanceVarAccess getInstanceVarAccess() {
    return PsiTreeUtil.getChildOfType(this, CrystalInstanceVarAccess.class);
  }

  @Override
  @Nullable
  public CrystalPostfixModifier getPostfixModifier() {
    return PsiTreeUtil.getChildOfType(this, CrystalPostfixModifier.class);
  }

}
