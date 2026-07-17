// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static de.magynhard.crystal.psi.CrystalTypes.*;
import de.magynhard.crystal.psi.*;

public class CrystalDotCallAccessImpl extends CrystalDotCallAccessMixin implements CrystalDotCallAccess {

  public CrystalDotCallAccessImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitDotCallAccess(this);
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
  public CrystalCallArgs getCallArgs() {
    return PsiTreeUtil.getChildOfType(this, CrystalCallArgs.class);
  }

  @Override
  @Nullable
  public CrystalMacroInterpolation getMacroInterpolation() {
    return PsiTreeUtil.getChildOfType(this, CrystalMacroInterpolation.class);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class);
  }

}
