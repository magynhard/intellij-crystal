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

public class CrystalProcLiteralImpl extends ASTWrapperPsiElement implements CrystalProcLiteral {

  public CrystalProcLiteralImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitProcLiteral(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalParameterList getParameterList() {
    return PsiTreeUtil.getChildOfType(this, CrystalParameterList.class);
  }

  @Override
  @Nullable
  public CrystalStatementList getStatementList() {
    return PsiTreeUtil.getChildOfType(this, CrystalStatementList.class);
  }

  @Override
  @NotNull
  public List<CrystalTypeReference> getTypeReferenceList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalTypeReference.class);
  }

}
