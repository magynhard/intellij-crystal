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

public class CrystalBareCommandExpressionImpl extends CrystalMethodCallExpressionMixin implements CrystalBareCommandExpression {

  public CrystalBareCommandExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitBareCommandExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalBareArgumentList getBareArgumentList() {
    return PsiTreeUtil.getChildOfType(this, CrystalBareArgumentList.class);
  }

  @Override
  @Nullable
  public CrystalBlock getBlock() {
    return PsiTreeUtil.getChildOfType(this, CrystalBlock.class);
  }

  @Override
  @Nullable
  public CrystalPostfixModifier getPostfixModifier() {
    return PsiTreeUtil.getChildOfType(this, CrystalPostfixModifier.class);
  }

}
