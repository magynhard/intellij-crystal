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

public class CrystalVisibilityModifierImpl extends ASTWrapperPsiElement implements CrystalVisibilityModifier {

  public CrystalVisibilityModifierImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitVisibilityModifier(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalBareArgumentList getBareArgumentList() {
    return PsiTreeUtil.getChildOfType(this, CrystalBareArgumentList.class);
  }

  @Override
  @Nullable
  public CrystalBlock getBlock() {
    return PsiTreeUtil.getChildOfType(this, CrystalBlock.class);
  }

  @Override
  @Nullable
  public CrystalCallArgs getCallArgs() {
    return PsiTreeUtil.getChildOfType(this, CrystalCallArgs.class);
  }

  @Override
  @Nullable
  public CrystalClassDefinition getClassDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalClassDefinition.class);
  }

  @Override
  @Nullable
  public CrystalConstantAssignment getConstantAssignment() {
    return PsiTreeUtil.getChildOfType(this, CrystalConstantAssignment.class);
  }

  @Override
  @Nullable
  public CrystalMacroDefinition getMacroDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroDefinition.class);
  }

  @Override
  @Nullable
  public CrystalMethodDefinition getMethodDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalMethodDefinition.class);
  }

  @Override
  @Nullable
  public CrystalStatement getStatement() {
    return PsiTreeUtil.getChildOfType(this, CrystalStatement.class);
  }

  @Override
  @Nullable
  public CrystalStructDefinition getStructDefinition() {
    return PsiTreeUtil.getChildOfType(this, CrystalStructDefinition.class);
  }

}
