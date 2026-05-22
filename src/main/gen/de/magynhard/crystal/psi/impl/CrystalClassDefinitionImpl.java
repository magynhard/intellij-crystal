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

public class CrystalClassDefinitionImpl extends ASTWrapperPsiElement implements CrystalClassDefinition {

  public CrystalClassDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitClassDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalClassBody getClassBody() {
    return findChildByClass(CrystalClassBody.class);
  }

  @Override
  @Nullable
  public CrystalSuperclassClause getSuperclassClause() {
    return findChildByClass(CrystalSuperclassClause.class);
  }

  @Override
  @Nullable
  public CrystalTypeName getTypeName() {
    return findChildByClass(CrystalTypeName.class);
  }

  @Override
  @Nullable
  public CrystalTypeParameters getTypeParameters() {
    return findChildByClass(CrystalTypeParameters.class);
  }

}
