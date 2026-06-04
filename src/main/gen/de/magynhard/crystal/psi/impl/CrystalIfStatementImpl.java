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

public class CrystalIfStatementImpl extends ASTWrapperPsiElement implements CrystalIfStatement {

  public CrystalIfStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitIfStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalCondition getCondition() {
    return PsiTreeUtil.getChildOfType(this, CrystalCondition.class);
  }

  @Override
  @Nullable
  public CrystalElseClause getElseClause() {
    return PsiTreeUtil.getChildOfType(this, CrystalElseClause.class);
  }

  @Override
  @NotNull
  public List<CrystalElsifClause> getElsifClauseList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalElsifClause.class);
  }

  @Override
  @Nullable
  public CrystalStatementList getStatementList() {
    return PsiTreeUtil.getChildOfType(this, CrystalStatementList.class);
  }

}
