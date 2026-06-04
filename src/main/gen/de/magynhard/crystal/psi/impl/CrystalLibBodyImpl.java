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

public class CrystalLibBodyImpl extends ASTWrapperPsiElement implements CrystalLibBody {

  public CrystalLibBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitLibBody(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CrystalEnumDefinition> getEnumDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalEnumDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalFunDefinition> getFunDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalFunDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalLibField> getLibFieldList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalLibField.class);
  }

  @Override
  @NotNull
  public List<CrystalLibStructDefinition> getLibStructDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalLibStructDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalLibUnionDefinition> getLibUnionDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalLibUnionDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeAliasLib> getTypeAliasLibList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeAliasLib.class);
  }

}
