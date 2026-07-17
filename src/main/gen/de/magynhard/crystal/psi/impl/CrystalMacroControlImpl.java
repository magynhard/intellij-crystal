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

public class CrystalMacroControlImpl extends ASTWrapperPsiElement implements CrystalMacroControl {

  public CrystalMacroControlImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitMacroControl(this);
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
  @NotNull
  public List<CrystalConstantAssignment> getConstantAssignmentList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalConstantAssignment.class);
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
  public List<CrystalLibExternalVar> getLibExternalVarList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalLibExternalVar.class);
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
  public List<CrystalLibTypeAlias> getLibTypeAliasList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalLibTypeAlias.class);
  }

  @Override
  @NotNull
  public List<CrystalLibUnionDefinition> getLibUnionDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalLibUnionDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroControl> getMacroControlList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroControl.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeAliasLib> getTypeAliasLibList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeAliasLib.class);
  }

}
