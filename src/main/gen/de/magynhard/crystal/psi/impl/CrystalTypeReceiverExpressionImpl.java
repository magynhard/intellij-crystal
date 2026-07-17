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

public class CrystalTypeReceiverExpressionImpl extends ASTWrapperPsiElement implements CrystalTypeReceiverExpression {

  public CrystalTypeReceiverExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitTypeReceiverExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public CrystalTypeArguments getTypeArguments() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, CrystalTypeArguments.class));
  }

  @Override
  @NotNull
  public CrystalTypePath getTypePath() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, CrystalTypePath.class));
  }

}
