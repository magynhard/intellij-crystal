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

public class CrystalEcrEcrTextImpl extends ASTWrapperPsiElement implements CrystalEcrEcrText {

  public CrystalEcrEcrTextImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalEcrVisitor visitor) {
    visitor.visitEcrText(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalEcrVisitor) accept((CrystalEcrVisitor)visitor);
    else super.accept(visitor);
  }

}
