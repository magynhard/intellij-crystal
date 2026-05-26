// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface CrystalEnumBody extends PsiElement {

  @NotNull
  List<CrystalAnnotationUsage> getAnnotationUsageList();

  @NotNull
  List<CrystalEnumConstant> getEnumConstantList();

  @NotNull
  List<CrystalMethodDefinition> getMethodDefinitionList();

}
