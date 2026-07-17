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
import com.intellij.psi.stubs.IStubElementType;
import de.magynhard.crystal.stubs.CrystalConstantAssignmentStub;

public class CrystalConstantAssignmentImpl extends CrystalStubbedConstantAssignmentImpl implements CrystalConstantAssignment {

  public CrystalConstantAssignmentImpl(ASTNode node) {
    super(node);
  }

  public CrystalConstantAssignmentImpl(CrystalConstantAssignmentStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitConstantAssignment(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalExpression.class);
  }

  @Override
  @Nullable
  public CrystalPostfixModifier getPostfixModifier() {
    return PsiTreeUtil.getChildOfType(this, CrystalPostfixModifier.class);
  }

}
