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

public class CrystalMethodCallExpressionImpl extends CrystalMethodCallExpressionMixin implements CrystalMethodCallExpression {

  public CrystalMethodCallExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitMethodCallExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalBlock getBlock() {
    return PsiTreeUtil.getChildOfType(this, CrystalBlock.class);
  }

  @Override
  @Nullable
  public CrystalCallArgs getCallArgs() {
    return PsiTreeUtil.getChildOfType(this, CrystalCallArgs.class);
  }

  @Override
  @Nullable
  public CrystalTypePath getTypePath() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypePath.class);
  }

}
