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

public class CrystalMultiAssignTargetImpl extends ASTWrapperPsiElement implements CrystalMultiAssignTarget {

  public CrystalMultiAssignTargetImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitMultiAssignTarget(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalClassVarAccess getClassVarAccess() {
    return PsiTreeUtil.getChildOfType(this, CrystalClassVarAccess.class);
  }

  @Override
  @Nullable
  public CrystalInstanceVarAccess getInstanceVarAccess() {
    return PsiTreeUtil.getChildOfType(this, CrystalInstanceVarAccess.class);
  }

  @Override
  @NotNull
  public List<CrystalMultiAssignTarget> getMultiAssignTargetList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMultiAssignTarget.class);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class);
  }

}
