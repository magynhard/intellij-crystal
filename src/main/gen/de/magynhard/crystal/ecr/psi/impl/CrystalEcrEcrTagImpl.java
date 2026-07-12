// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.ecr.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static de.magynhard.crystal.ecr.EmbeddedCrystalTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import de.magynhard.crystal.ecr.psi.*;

public class CrystalEcrEcrTagImpl extends ASTWrapperPsiElement implements CrystalEcrEcrTag {

  public CrystalEcrEcrTagImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalEcrVisitor visitor) {
    visitor.visitEcrTag(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalEcrVisitor) accept((CrystalEcrVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public CrystalEcrEcrBody getEcrBody() {
    return findNotNullChildByClass(CrystalEcrEcrBody.class);
  }

}
