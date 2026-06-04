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

public class CrystalLibExternalVarImpl extends ASTWrapperPsiElement implements CrystalLibExternalVar {

  public CrystalLibExternalVarImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitLibExternalVar(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalStringExpression getStringExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalStringExpression.class);
  }

  @Override
  @NotNull
  public CrystalTypeReference getTypeReference() {
    return notNullChild(PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class));
  }

}
