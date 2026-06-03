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

public class CrystalCaseStatementImpl extends ASTWrapperPsiElement implements CrystalCaseStatement {

  public CrystalCaseStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitCaseStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalElseClause getElseClause() {
    return PsiTreeUtil.getChildOfType(this, CrystalElseClause.class);
  }

  @Override
  @Nullable
  public CrystalExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalExpression.class);
  }

  @Override
  @NotNull
  public List<CrystalInClause> getInClauseList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalInClause.class);
  }

  @Override
  @NotNull
  public List<CrystalWhenClause> getWhenClauseList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalWhenClause.class);
  }

}
