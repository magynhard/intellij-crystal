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
import de.magynhard.crystal.stubs.CrystalAnnotationDefinitionStub;

public class CrystalAnnotationDefinitionImpl extends CrystalStubbedAnnotationDefinitionImpl implements CrystalAnnotationDefinition {

  public CrystalAnnotationDefinitionImpl(ASTNode node) {
    super(node);
  }

  public CrystalAnnotationDefinitionImpl(CrystalAnnotationDefinitionStub stub, IStubElementType stubType) {
    super(stub, stubType);
  }

  public void accept(@NotNull CrystalVisitor visitor) {
    visitor.visitAnnotationDefinition(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof CrystalVisitor) accept((CrystalVisitor)visitor);
    else super.accept(visitor);
  }

}
