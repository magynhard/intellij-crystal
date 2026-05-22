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
  public CrystalClassDefinition getClassDefinition() {
    return findChildByClass(CrystalClassDefinition.class);
  }

  @Override
  @Nullable
  public CrystalConstantAssignment getConstantAssignment() {
    return findChildByClass(CrystalConstantAssignment.class);
  }

  @Override
  @Nullable
  public CrystalMacroDefinition getMacroDefinition() {
    return findChildByClass(CrystalMacroDefinition.class);
  }

  @Override
  @Nullable
  public CrystalMethodDefinition getMethodDefinition() {
    return findChildByClass(CrystalMethodDefinition.class);
  }

  @Override
  @Nullable
  public CrystalStatement getStatement() {
    return findChildByClass(CrystalStatement.class);
  }

  @Override
  @Nullable
  public CrystalStructDefinition getStructDefinition() {
    return findChildByClass(CrystalStructDefinition.class);
  }

}
