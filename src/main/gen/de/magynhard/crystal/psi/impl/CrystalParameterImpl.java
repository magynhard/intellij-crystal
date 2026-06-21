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

public class CrystalParameterImpl extends CrystalParameterMixin implements CrystalParameter {

  public CrystalParameterImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitParameter(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CrystalAnnotationUsage> getAnnotationUsageList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalAnnotationUsage.class);
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
  @NotNull
  public List<CrystalMacroControl> getMacroControlList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroControl.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeArguments> getTypeArgumentsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeArguments.class);
  }

  @Override
  @NotNull
  public List<CrystalTypePath> getTypePathList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypePath.class);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class);
  }

}
