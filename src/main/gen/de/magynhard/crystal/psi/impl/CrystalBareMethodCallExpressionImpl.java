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

public class CrystalBareMethodCallExpressionImpl extends CrystalBareMethodCallExpressionMixin implements CrystalBareMethodCallExpression {

  public CrystalBareMethodCallExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitBareMethodCallExpression(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public CrystalCallArgs getCallArgs() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, CrystalCallArgs.class));
  }

}
