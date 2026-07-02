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

public class CrystalImplicitObjectCallImpl extends ASTWrapperPsiElement implements CrystalImplicitObjectCall {

  public CrystalImplicitObjectCallImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitImplicitObjectCall(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalArgumentList getArgumentList() {
    return PsiTreeUtil.getChildOfType(this, CrystalArgumentList.class);
  }

  @Override
  @Nullable
  public CrystalCallArgs getCallArgs() {
    return PsiTreeUtil.getChildOfType(this, CrystalCallArgs.class);
  }

  @Override
  @Nullable
  public CrystalExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalExpression.class);
  }

}
