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
import de.magynhard.crystal.stubs.CrystalModuleDefinitionStub;

public class CrystalModuleDefinitionImpl extends CrystalStubbedModuleDefinitionImpl implements CrystalModuleDefinition {

  public CrystalModuleDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalModuleDefinitionImpl(CrystalModuleDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitModuleDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public CrystalClassBody getClassBody() {
    return PsiTreeUtil.getChildOfType(this, CrystalClassBody.class);
  }

  @Override
  @NotNull
  public List<CrystalMacroInterpolation> getMacroInterpolationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, CrystalMacroInterpolation.class);
  }

  @Override
  @Nullable
  public CrystalTypeParameters getTypeParameters() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeParameters.class);
  }

}
