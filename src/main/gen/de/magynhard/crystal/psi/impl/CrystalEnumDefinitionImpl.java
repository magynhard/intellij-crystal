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
import de.magynhard.crystal.stubs.CrystalEnumDefinitionStub;

public class CrystalEnumDefinitionImpl extends CrystalStubbedEnumDefinitionImpl implements CrystalEnumDefinition {

  public CrystalEnumDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalEnumDefinitionImpl(CrystalEnumDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitEnumDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalEnumBody getEnumBody() {
    return PsiTreeUtil.getChildOfType(this, CrystalEnumBody.class);
  }

  @Override
  @Nullable
  public CrystalTypeName getTypeName() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeName.class);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class);
  }

}
