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

public class CrystalEnumBodyImpl extends ASTWrapperPsiElement implements CrystalEnumBody {

  public CrystalEnumBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitEnumBody(this);
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
  public List<CrystalEnumConstant> getEnumConstantList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalEnumConstant.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroControl> getMacroControlList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroControl.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroControlEscaped> getMacroControlEscapedList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroControlEscaped.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroInterpolation> getMacroInterpolationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroInterpolation.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroInterpolationEscaped> getMacroInterpolationEscapedList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroInterpolationEscaped.class);
  }

  @Override
  @NotNull
  public List<CrystalMethodDefinition> getMethodDefinitionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMethodDefinition.class);
  }

}
