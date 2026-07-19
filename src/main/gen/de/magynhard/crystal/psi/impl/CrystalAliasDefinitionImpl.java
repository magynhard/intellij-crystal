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
import de.magynhard.crystal.stubs.CrystalAliasDefinitionStub;

public class CrystalAliasDefinitionImpl extends CrystalStubbedAliasDefinitionImpl implements CrystalAliasDefinition {

  public CrystalAliasDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalAliasDefinitionImpl(CrystalAliasDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitAliasDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeReference.class);
  }

}
