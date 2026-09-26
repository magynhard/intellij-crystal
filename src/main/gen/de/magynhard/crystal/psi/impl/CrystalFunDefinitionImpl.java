// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static de.magynhard.crystal.psi.CrystalTypes.*;
import com.intellij.extapi.psi.StubBasedPsiElementBase;
import de.magynhard.crystal.stubs.CrystalFunDefinitionStub;
import de.magynhard.crystal.psi.*;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.tree.IElementType;

public class CrystalFunDefinitionImpl extends StubBasedPsiElementBase<CrystalFunDefinitionStub> implements CrystalFunDefinition {

  public CrystalFunDefinitionImpl(@NotNull CrystalFunDefinitionStub stub, @NotNull IStubElementType<?, ?> type) {
    super(stub, type);
  }

  public CrystalFunDefinitionImpl(@NotNull CrystalFunDefinitionStub stub, @NotNull IElementType type) {
    super(stub, type);
  }

  public CrystalFunDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public CrystalFunDefinitionImpl(CrystalFunDefinitionStub stub, IElementType type, ASTNode node) {
    super(stub, type, node);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitFunDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<CrystalMacroInterpolation> getMacroInterpolationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroInterpolation.class);
  }

  @Override
  @Nullable
  public CrystalParameterList getParameterList() {
    return PsiTreeUtil.getChildOfType(this, CrystalParameterList.class);
  }

  @Override
  @Nullable
  public CrystalStringExpression getStringExpression() {
    return PsiTreeUtil.getChildOfType(this, CrystalStringExpression.class);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class);
  }

}
