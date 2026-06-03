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

public class CrystalTypeReferenceImpl extends ASTWrapperPsiElement implements CrystalTypeReference {

  public CrystalTypeReferenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitTypeReference(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CrystalExpression> getExpressionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalExpression.class);
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
  @NotNull
  public List<CrystalTypeReference> getTypeReferenceList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeReference.class);
  }

}
