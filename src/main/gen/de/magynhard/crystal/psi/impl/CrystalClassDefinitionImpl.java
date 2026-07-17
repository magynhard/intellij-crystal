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
import de.magynhard.crystal.stubs.CrystalClassDefinitionStub;

public class CrystalClassDefinitionImpl extends CrystalStubbedClassDefinitionImpl implements CrystalClassDefinition {

  public CrystalClassDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalClassDefinitionImpl(CrystalClassDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitClassDefinition(this);
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
  public CrystalSuperclassClause getSuperclassClause() {
    return PsiTreeUtil.getChildOfType(this, CrystalSuperclassClause.class);
  }

  @Override
  @Nullable
  public CrystalTypeParameters getTypeParameters() {
    return PsiTreeUtil.getChildOfType(this, CrystalTypeParameters.class);
  }

}
