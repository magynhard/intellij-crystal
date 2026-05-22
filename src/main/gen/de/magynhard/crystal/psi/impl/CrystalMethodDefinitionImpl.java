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

public class CrystalMethodDefinitionImpl extends ASTWrapperPsiElement implements CrystalMethodDefinition {

  public CrystalMethodDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitMethodDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalMethodBody getMethodBody() {
    return findChildByClass(CrystalMethodBody.class);
  }

  @Override
  @Nullable
  public CrystalMethodName getMethodName() {
    return findChildByClass(CrystalMethodName.class);
  }

  @Override
  @Nullable
  public CrystalParameterList getParameterList() {
    return findChildByClass(CrystalParameterList.class);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return findChildByClass(CrystalTypeReference.class);
  }

}
