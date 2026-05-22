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
import de.magynhard.crystal.stubs.CrystalMacroDefinitionStub;

public class CrystalMacroDefinitionImpl extends CrystalStubbedMacroDefinitionImpl implements CrystalMacroDefinition {

  public CrystalMacroDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalMacroDefinitionImpl(CrystalMacroDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitMacroDefinition(this);
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

}
