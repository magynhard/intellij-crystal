// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.ecr;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import de.magynhard.crystal.ecr.psi.impl.*;

public interface EmbeddedCrystalTypes {

  IElementType ECR_BODY = new EmbeddedCrystalElementType("ECR_BODY");
  IElementType ECR_PART = new EmbeddedCrystalElementType("ECR_PART");
  IElementType ECR_TAG = new EmbeddedCrystalElementType("ECR_TAG");
  IElementType ECR_TEXT = new EmbeddedCrystalElementType("ECR_TEXT");

  IElementType ECR_OUTER = new EmbeddedCrystalTokenType("ECR_OUTER");
  IElementType ECR_RAW = new EmbeddedCrystalTokenType("ECR_RAW");
  IElementType ECR_TAG_BEGIN = new EmbeddedCrystalTokenType("ECR_TAG_BEGIN");
  IElementType ECR_TAG_END = new EmbeddedCrystalTokenType("ECR_TAG_END");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ECR_BODY) {
        return new CrystalEcrEcrBodyImpl(node);
      }
      else if (type == ECR_PART) {
        return new CrystalEcrEcrPartImpl(node);
      }
      else if (type == ECR_TAG) {
        return new CrystalEcrEcrTagImpl(node);
      }
      else if (type == ECR_TEXT) {
        return new CrystalEcrEcrTextImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
