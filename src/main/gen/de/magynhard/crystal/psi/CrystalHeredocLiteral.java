// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;

public interface CrystalHeredocLiteral extends PsiLanguageInjectionHost {

  @NotNull
  List<CrystalExpression> getExpressionList();

  @NotNull
  List<CrystalPostfixModifier> getPostfixModifierList();

}
