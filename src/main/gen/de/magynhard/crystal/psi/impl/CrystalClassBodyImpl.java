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

public class CrystalClassBodyImpl extends ASTWrapperPsiElement implements CrystalClassBody {

  public CrystalClassBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitClassBody(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CrystalAliasDefinition> getAliasDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalAliasDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalAnnotationUsage> getAnnotationUsageList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalAnnotationUsage.class);
  }

  @Override
  @NotNull
  public List<CrystalClassDefinition> getClassDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalClassDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalEnumDefinition> getEnumDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalEnumDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalExtendStatement> getExtendStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalExtendStatement.class);
  }

  @Override
  @NotNull
  public List<CrystalIncludeStatement> getIncludeStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalIncludeStatement.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroControl> getMacroControlList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroControl.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroDefinition> getMacroDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalMethodDefinition> getMethodDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMethodDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalModuleDefinition> getModuleDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalModuleDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalPropertyDeclaration> getPropertyDeclarationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalPropertyDeclaration.class);
  }

  @Override
  @NotNull
  public List<CrystalStatement> getStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalStatement.class);
  }

  @Override
  @NotNull
  public List<CrystalStructDefinition> getStructDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalStructDefinition.class);
  }

  @Override
  @NotNull
  public List<CrystalVisibilityModifier> getVisibilityModifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalVisibilityModifier.class);
  }

}
