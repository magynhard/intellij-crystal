// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.ecr.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static de.magynhard.crystal.ecr.EmbeddedCrystalTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class EmbeddedCrystalParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, null);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    result_ = parse_root_(root_, builder_);
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_) {
    return parse_root_(root_, builder_, 0);
  }

  static boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return ecrFile(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // ECR_RAW
  public static boolean ecrBody(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ecrBody")) return false;
    if (!nextTokenIs(builder_, ECR_RAW)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ECR_RAW);
    exit_section_(builder_, marker_, ECR_BODY, result_);
    return result_;
  }

  /* ********************************************************** */
  // ecrPart*
  static boolean ecrFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ecrFile")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!ecrPart(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ecrFile", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ecrText | ecrTag
  public static boolean ecrPart(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ecrPart")) return false;
    if (!nextTokenIs(builder_, "<ecr part>", ECR_OUTER, ECR_TAG_BEGIN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ECR_PART, "<ecr part>");
    result_ = ecrText(builder_, level_ + 1);
    if (!result_) result_ = ecrTag(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // ECR_TAG_BEGIN ecrBody ECR_TAG_END
  public static boolean ecrTag(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ecrTag")) return false;
    if (!nextTokenIs(builder_, ECR_TAG_BEGIN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ECR_TAG_BEGIN);
    result_ = result_ && ecrBody(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ECR_TAG_END);
    exit_section_(builder_, marker_, ECR_TAG, result_);
    return result_;
  }

  /* ********************************************************** */
  // ECR_OUTER
  public static boolean ecrText(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ecrText")) return false;
    if (!nextTokenIs(builder_, ECR_OUTER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ECR_OUTER);
    exit_section_(builder_, marker_, ECR_TEXT, result_);
    return result_;
  }

}
