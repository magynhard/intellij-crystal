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
import de.magynhard.crystal.stubs.CrystalMethodDefinitionStub;

public class CrystalMethodDefinitionImpl extends CrystalStubbedMethodDefinitionImpl implements CrystalMethodDefinition {

  public CrystalMethodDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalMethodDefinitionImpl(CrystalMethodDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitMethodDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalMethodBody getMethodBody() {
    return PsiTreeUtil.getChildOfType(this, CrystalMethodBody.class);
  }

  @Override
  @Nullable
  public CrystalMethodName getMethodName() {
    return PsiTreeUtil.getChildOfType(this, CrystalMethodName.class);
  }

  @Override
  @Nullable
  public CrystalParameterList getParameterList() {
    return PsiTreeUtil.getChildOfType(this, CrystalParameterList.class);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class);
  }

}
