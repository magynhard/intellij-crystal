// This is a generated file. Not intended for manual editing.
package de.magynhard.crystal.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static de.magynhard.crystal.psi.CrystalTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class CrystalParser implements PsiParser, LightPsiParser {

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
    return crystalFile(builder_, level_ + 1);
  }

  /* ********************************************************** */
  // multiplicative_expression ((PLUS | MINUS) multiplicative_expression)*
  static boolean additive_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additive_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = multiplicative_expression(builder_, level_ + 1);
    result_ = result_ && additive_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((PLUS | MINUS) multiplicative_expression)*
  private static boolean additive_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additive_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!additive_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "additive_expression_1", pos_)) break;
    }
    return true;
  }

  // (PLUS | MINUS) multiplicative_expression
  private static boolean additive_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additive_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = additive_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && multiplicative_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PLUS | MINUS
  private static boolean additive_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additive_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    return result_;
  }

  /* ********************************************************** */
  // ALIAS type_name ASSIGN type_reference
  public static boolean alias_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "alias_definition")) return false;
    if (!nextTokenIs(builder_, ALIAS)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ALIAS_DEFINITION, null);
    result_ = consumeToken(builder_, ALIAS);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, ASSIGN)) && result_;
    result_ = pinned_ && type_reference(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // shift_expression (AMPERSAND shift_expression)*
  static boolean and_bitwise_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_bitwise_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = shift_expression(builder_, level_ + 1);
    result_ = result_ && and_bitwise_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AMPERSAND shift_expression)*
  private static boolean and_bitwise_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_bitwise_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!and_bitwise_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "and_bitwise_expression_1", pos_)) break;
    }
    return true;
  }

  // AMPERSAND shift_expression
  private static boolean and_bitwise_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_bitwise_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AMPERSAND);
    result_ = result_ && shift_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // not_expression (AND_AND not_expression)*
  static boolean and_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = not_expression(builder_, level_ + 1);
    result_ = result_ && and_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AND_AND not_expression)*
  private static boolean and_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!and_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "and_expression_1", pos_)) break;
    }
    return true;
  }

  // AND_AND not_expression
  private static boolean and_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AND_AND);
    result_ = result_ && not_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ANNOTATION type_name END
  public static boolean annotation_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation_definition")) return false;
    if (!nextTokenIs(builder_, ANNOTATION)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ANNOTATION_DEFINITION, null);
    result_ = consumeToken(builder_, ANNOTATION);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type_name(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // AT LBRACKET type_path [LPAREN argument_list RPAREN] RBRACKET
  public static boolean annotation_usage(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation_usage")) return false;
    if (!nextTokenIs(builder_, AT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, AT, LBRACKET);
    result_ = result_ && type_path(builder_, level_ + 1);
    result_ = result_ && annotation_usage_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, ANNOTATION_USAGE, result_);
    return result_;
  }

  // [LPAREN argument_list RPAREN]
  private static boolean annotation_usage_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation_usage_3")) return false;
    annotation_usage_3_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN argument_list RPAREN
  private static boolean annotation_usage_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation_usage_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // [IDENTIFIER COLON] expression
  //            | STAR expression
  //            | DOUBLE_STAR expression
  public static boolean argument(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ARGUMENT, "<argument>");
    result_ = argument_0(builder_, level_ + 1);
    if (!result_) result_ = argument_1(builder_, level_ + 1);
    if (!result_) result_ = argument_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [IDENTIFIER COLON] expression
  private static boolean argument_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = argument_0_0(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [IDENTIFIER COLON]
  private static boolean argument_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_0_0")) return false;
    parseTokens(builder_, 0, IDENTIFIER, COLON);
    return true;
  }

  // STAR expression
  private static boolean argument_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STAR);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOUBLE_STAR expression
  private static boolean argument_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_STAR);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // argument (COMMA argument)*
  public static boolean argument_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ARGUMENT_LIST, "<argument list>");
    result_ = argument(builder_, level_ + 1);
    result_ = result_ && argument_list_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (COMMA argument)*
  private static boolean argument_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!argument_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "argument_list_1", pos_)) break;
    }
    return true;
  }

  // COMMA argument
  private static boolean argument_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && argument(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LBRACKET [expression_list] RBRACKET [OF type_reference]
  public static boolean array_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_literal")) return false;
    if (!nextTokenIs(builder_, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && array_literal_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    result_ = result_ && array_literal_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, ARRAY_LITERAL, result_);
    return result_;
  }

  // [expression_list]
  private static boolean array_literal_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_literal_1")) return false;
    expression_list(builder_, level_ + 1);
    return true;
  }

  // [OF type_reference]
  private static boolean array_literal_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_literal_3")) return false;
    array_literal_3_0(builder_, level_ + 1);
    return true;
  }

  // OF type_reference
  private static boolean array_literal_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_literal_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OF);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | SLASH_ASSIGN
  //                      | PERCENT_ASSIGN | AMPERSAND_ASSIGN | PIPE_ASSIGN | CARET_ASSIGN
  //                      | DOUBLE_STAR_ASSIGN | LSHIFT_ASSIGN | RSHIFT_ASSIGN
  //                      | OR_OR_ASSIGN | AND_AND_ASSIGN
  static boolean assign_op(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assign_op")) return false;
    boolean result_;
    result_ = consumeToken(builder_, ASSIGN);
    if (!result_) result_ = consumeToken(builder_, PLUS_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, MINUS_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, STAR_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, SLASH_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, PERCENT_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, PIPE_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, CARET_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_STAR_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, LSHIFT_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, RSHIFT_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, OR_OR_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, AND_AND_ASSIGN);
    return result_;
  }

  /* ********************************************************** */
  // variable assign_op expression
  public static boolean assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignment")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASSIGNMENT, "<assignment>");
    result_ = variable(builder_, level_ + 1);
    result_ = result_ && assign_op(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // bare_multiplicative_expression ((PLUS | MINUS) bare_multiplicative_expression)*
  static boolean bare_additive_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_additive_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_multiplicative_expression(builder_, level_ + 1);
    result_ = result_ && bare_additive_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((PLUS | MINUS) bare_multiplicative_expression)*
  private static boolean bare_additive_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_additive_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_additive_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_additive_expression_1", pos_)) break;
    }
    return true;
  }

  // (PLUS | MINUS) bare_multiplicative_expression
  private static boolean bare_additive_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_additive_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_additive_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && bare_multiplicative_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PLUS | MINUS
  private static boolean bare_additive_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_additive_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    return result_;
  }

  /* ********************************************************** */
  // bare_shift_expression (AMPERSAND bare_shift_expression)*
  static boolean bare_and_bitwise_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_bitwise_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_shift_expression(builder_, level_ + 1);
    result_ = result_ && bare_and_bitwise_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AMPERSAND bare_shift_expression)*
  private static boolean bare_and_bitwise_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_bitwise_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_and_bitwise_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_and_bitwise_expression_1", pos_)) break;
    }
    return true;
  }

  // AMPERSAND bare_shift_expression
  private static boolean bare_and_bitwise_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_bitwise_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AMPERSAND);
    result_ = result_ && bare_shift_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_not_expression (AND_AND bare_not_expression)*
  static boolean bare_and_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_not_expression(builder_, level_ + 1);
    result_ = result_ && bare_and_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AND_AND bare_not_expression)*
  private static boolean bare_and_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_and_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_and_expression_1", pos_)) break;
    }
    return true;
  }

  // AND_AND bare_not_expression
  private static boolean bare_and_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AND_AND);
    result_ = result_ && bare_not_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // [IDENTIFIER COLON] bare_expression
  //                 | STAR bare_expression
  //                 | DOUBLE_STAR bare_expression
  public static boolean bare_argument(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BARE_ARGUMENT, "<bare argument>");
    result_ = bare_argument_0(builder_, level_ + 1);
    if (!result_) result_ = bare_argument_1(builder_, level_ + 1);
    if (!result_) result_ = bare_argument_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [IDENTIFIER COLON] bare_expression
  private static boolean bare_argument_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_argument_0_0(builder_, level_ + 1);
    result_ = result_ && bare_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [IDENTIFIER COLON]
  private static boolean bare_argument_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument_0_0")) return false;
    parseTokens(builder_, 0, IDENTIFIER, COLON);
    return true;
  }

  // STAR bare_expression
  private static boolean bare_argument_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STAR);
    result_ = result_ && bare_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOUBLE_STAR bare_expression
  private static boolean bare_argument_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_STAR);
    result_ = result_ && bare_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_argument (COMMA bare_argument)*
  public static boolean bare_argument_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BARE_ARGUMENT_LIST, "<bare argument list>");
    result_ = bare_argument(builder_, level_ + 1);
    result_ = result_ && bare_argument_list_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (COMMA bare_argument)*
  private static boolean bare_argument_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_argument_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_argument_list_1", pos_)) break;
    }
    return true;
  }

  // COMMA bare_argument
  private static boolean bare_argument_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && bare_argument(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_range_expression ((EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ) bare_range_expression)*
  static boolean bare_comparison_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_comparison_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_range_expression(builder_, level_ + 1);
    result_ = result_ && bare_comparison_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ) bare_range_expression)*
  private static boolean bare_comparison_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_comparison_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_comparison_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_comparison_expression_1", pos_)) break;
    }
    return true;
  }

  // (EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ) bare_range_expression
  private static boolean bare_comparison_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_comparison_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_comparison_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && bare_range_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ
  private static boolean bare_comparison_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_comparison_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, LTE);
    if (!result_) result_ = consumeToken(builder_, GTE);
    if (!result_) result_ = consumeToken(builder_, SPACESHIP);
    if (!result_) result_ = consumeToken(builder_, CASE_EQ);
    return result_;
  }

  /* ********************************************************** */
  // bare_or_expression [QUESTION expression COLON expression]
  static boolean bare_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_or_expression(builder_, level_ + 1);
    result_ = result_ && bare_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [QUESTION expression COLON expression]
  private static boolean bare_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_expression_1")) return false;
    bare_expression_1_0(builder_, level_ + 1);
    return true;
  }

  // QUESTION expression COLON expression
  private static boolean bare_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUESTION);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // (IDENTIFIER | CONSTANT) call_args
  public static boolean bare_method_call_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_method_call_expression")) return false;
    if (!nextTokenIs(builder_, "<bare method call expression>", CONSTANT, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BARE_METHOD_CALL_EXPRESSION, "<bare method call expression>");
    result_ = bare_method_call_expression_0(builder_, level_ + 1);
    result_ = result_ && call_args(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // IDENTIFIER | CONSTANT
  private static boolean bare_method_call_expression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_method_call_expression_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    return result_;
  }

  /* ********************************************************** */
  // bare_power_expression ((STAR | SLASH | DOUBLE_SLASH | PERCENT) bare_power_expression)*
  static boolean bare_multiplicative_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_multiplicative_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_power_expression(builder_, level_ + 1);
    result_ = result_ && bare_multiplicative_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((STAR | SLASH | DOUBLE_SLASH | PERCENT) bare_power_expression)*
  private static boolean bare_multiplicative_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_multiplicative_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_multiplicative_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_multiplicative_expression_1", pos_)) break;
    }
    return true;
  }

  // (STAR | SLASH | DOUBLE_SLASH | PERCENT) bare_power_expression
  private static boolean bare_multiplicative_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_multiplicative_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_multiplicative_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && bare_power_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STAR | SLASH | DOUBLE_SLASH | PERCENT
  private static boolean bare_multiplicative_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_multiplicative_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_SLASH);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    return result_;
  }

  /* ********************************************************** */
  // BANG bare_not_expression | bare_comparison_expression
  static boolean bare_not_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_not_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_not_expression_0(builder_, level_ + 1);
    if (!result_) result_ = bare_comparison_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BANG bare_not_expression
  private static boolean bare_not_expression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_not_expression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BANG);
    result_ = result_ && bare_not_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_xor_expression (PIPE bare_xor_expression)*
  static boolean bare_or_bitwise_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_bitwise_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_xor_expression(builder_, level_ + 1);
    result_ = result_ && bare_or_bitwise_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (PIPE bare_xor_expression)*
  private static boolean bare_or_bitwise_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_bitwise_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_or_bitwise_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_or_bitwise_expression_1", pos_)) break;
    }
    return true;
  }

  // PIPE bare_xor_expression
  private static boolean bare_or_bitwise_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_bitwise_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && bare_xor_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_and_expression (OR_OR bare_and_expression)*
  static boolean bare_or_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_and_expression(builder_, level_ + 1);
    result_ = result_ && bare_or_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (OR_OR bare_and_expression)*
  private static boolean bare_or_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_or_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_or_expression_1", pos_)) break;
    }
    return true;
  }

  // OR_OR bare_and_expression
  private static boolean bare_or_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OR_OR);
    result_ = result_ && bare_and_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_primary_expression bare_postfix_op*
  static boolean bare_postfix_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_primary_expression(builder_, level_ + 1);
    result_ = result_ && bare_postfix_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // bare_postfix_op*
  private static boolean bare_postfix_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_postfix_op(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_postfix_expression_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // DOT (IDENTIFIER | CONSTANT) [call_args]
  //                           | DOUBLE_COLON CONSTANT
  //                           | LBRACKET argument_list RBRACKET
  //                           | call_args
  static boolean bare_postfix_op(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_postfix_op_0(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, DOUBLE_COLON, CONSTANT);
    if (!result_) result_ = bare_postfix_op_2(builder_, level_ + 1);
    if (!result_) result_ = call_args(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOT (IDENTIFIER | CONSTANT) [call_args]
  private static boolean bare_postfix_op_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && bare_postfix_op_0_1(builder_, level_ + 1);
    result_ = result_ && bare_postfix_op_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT
  private static boolean bare_postfix_op_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_0_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    return result_;
  }

  // [call_args]
  private static boolean bare_postfix_op_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_0_2")) return false;
    call_args(builder_, level_ + 1);
    return true;
  }

  // LBRACKET argument_list RBRACKET
  private static boolean bare_postfix_op_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_unary_expression (DOUBLE_STAR bare_unary_expression)*
  static boolean bare_power_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_power_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_unary_expression(builder_, level_ + 1);
    result_ = result_ && bare_power_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (DOUBLE_STAR bare_unary_expression)*
  private static boolean bare_power_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_power_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_power_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_power_expression_1", pos_)) break;
    }
    return true;
  }

  // DOUBLE_STAR bare_unary_expression
  private static boolean bare_power_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_power_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_STAR);
    result_ = result_ && bare_unary_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // grouped_expression
  //                                   | array_literal
  //                                   | hash_literal
  //                                   | tuple_literal
  //                                   | bare_method_call_expression
  //                                   | literal
  //                                   | instance_var_access
  //                                   | class_var_access
  //                                   | variable_reference
  //                                   | typeof_expression
  //                                   | sizeof_expression
  //                                   | instance_sizeof_expression
  //                                   | pointerof_expression
  static boolean bare_primary_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_primary_expression")) return false;
    boolean result_;
    result_ = grouped_expression(builder_, level_ + 1);
    if (!result_) result_ = array_literal(builder_, level_ + 1);
    if (!result_) result_ = hash_literal(builder_, level_ + 1);
    if (!result_) result_ = tuple_literal(builder_, level_ + 1);
    if (!result_) result_ = bare_method_call_expression(builder_, level_ + 1);
    if (!result_) result_ = literal(builder_, level_ + 1);
    if (!result_) result_ = instance_var_access(builder_, level_ + 1);
    if (!result_) result_ = class_var_access(builder_, level_ + 1);
    if (!result_) result_ = variable_reference(builder_, level_ + 1);
    if (!result_) result_ = typeof_expression(builder_, level_ + 1);
    if (!result_) result_ = sizeof_expression(builder_, level_ + 1);
    if (!result_) result_ = instance_sizeof_expression(builder_, level_ + 1);
    if (!result_) result_ = pointerof_expression(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // bare_or_bitwise_expression [(DOTDOT | DOTDOTDOT) bare_or_bitwise_expression]
  static boolean bare_range_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_range_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_or_bitwise_expression(builder_, level_ + 1);
    result_ = result_ && bare_range_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [(DOTDOT | DOTDOTDOT) bare_or_bitwise_expression]
  private static boolean bare_range_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_range_expression_1")) return false;
    bare_range_expression_1_0(builder_, level_ + 1);
    return true;
  }

  // (DOTDOT | DOTDOTDOT) bare_or_bitwise_expression
  private static boolean bare_range_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_range_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_range_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && bare_or_bitwise_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOTDOT | DOTDOTDOT
  private static boolean bare_range_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_range_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, DOTDOT);
    if (!result_) result_ = consumeToken(builder_, DOTDOTDOT);
    return result_;
  }

  /* ********************************************************** */
  // bare_additive_expression ((LSHIFT | RSHIFT) bare_additive_expression)*
  static boolean bare_shift_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_shift_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_additive_expression(builder_, level_ + 1);
    result_ = result_ && bare_shift_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((LSHIFT | RSHIFT) bare_additive_expression)*
  private static boolean bare_shift_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_shift_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_shift_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_shift_expression_1", pos_)) break;
    }
    return true;
  }

  // (LSHIFT | RSHIFT) bare_additive_expression
  private static boolean bare_shift_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_shift_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_shift_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && bare_additive_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LSHIFT | RSHIFT
  private static boolean bare_shift_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_shift_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, LSHIFT);
    if (!result_) result_ = consumeToken(builder_, RSHIFT);
    return result_;
  }

  /* ********************************************************** */
  // (PLUS | MINUS | TILDE | AMPERSAND | STAR) bare_unary_expression
  //                                 | bare_postfix_expression
  static boolean bare_unary_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_unary_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_unary_expression_0(builder_, level_ + 1);
    if (!result_) result_ = bare_postfix_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (PLUS | MINUS | TILDE | AMPERSAND | STAR) bare_unary_expression
  private static boolean bare_unary_expression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_unary_expression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_unary_expression_0_0(builder_, level_ + 1);
    result_ = result_ && bare_unary_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PLUS | MINUS | TILDE | AMPERSAND | STAR
  private static boolean bare_unary_expression_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_unary_expression_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, TILDE);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    if (!result_) result_ = consumeToken(builder_, STAR);
    return result_;
  }

  /* ********************************************************** */
  // bare_and_bitwise_expression (CARET bare_and_bitwise_expression)*
  static boolean bare_xor_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_xor_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_and_bitwise_expression(builder_, level_ + 1);
    result_ = result_ && bare_xor_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (CARET bare_and_bitwise_expression)*
  private static boolean bare_xor_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_xor_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_xor_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_xor_expression_1", pos_)) break;
    }
    return true;
  }

  // CARET bare_and_bitwise_expression
  private static boolean bare_xor_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_xor_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CARET);
    result_ = result_ && bare_and_bitwise_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // BEGIN statement_list rescue_clause* [else_clause] [ensure_clause] END
  public static boolean begin_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "begin_statement")) return false;
    if (!nextTokenIs(builder_, BEGIN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BEGIN_STATEMENT, null);
    result_ = consumeToken(builder_, BEGIN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, statement_list(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, begin_statement_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, begin_statement_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, begin_statement_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // rescue_clause*
  private static boolean begin_statement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "begin_statement_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!rescue_clause(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "begin_statement_2", pos_)) break;
    }
    return true;
  }

  // [else_clause]
  private static boolean begin_statement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "begin_statement_3")) return false;
    else_clause(builder_, level_ + 1);
    return true;
  }

  // [ensure_clause]
  private static boolean begin_statement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "begin_statement_4")) return false;
    ensure_clause(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // DO [PIPE parameter_list PIPE] statement_list END
  //         | LBRACE [PIPE parameter_list PIPE] statement_list RBRACE
  public static boolean block(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block")) return false;
    if (!nextTokenIs(builder_, "<block>", DO, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BLOCK, "<block>");
    result_ = block_0(builder_, level_ + 1);
    if (!result_) result_ = block_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // DO [PIPE parameter_list PIPE] statement_list END
  private static boolean block_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DO);
    result_ = result_ && block_0_1(builder_, level_ + 1);
    result_ = result_ && statement_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [PIPE parameter_list PIPE]
  private static boolean block_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0_1")) return false;
    block_0_1_0(builder_, level_ + 1);
    return true;
  }

  // PIPE parameter_list PIPE
  private static boolean block_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, PIPE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LBRACE [PIPE parameter_list PIPE] statement_list RBRACE
  private static boolean block_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && block_1_1(builder_, level_ + 1);
    result_ = result_ && statement_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [PIPE parameter_list PIPE]
  private static boolean block_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_1_1")) return false;
    block_1_1_0(builder_, level_ + 1);
    return true;
  }

  // PIPE parameter_list PIPE
  private static boolean block_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, PIPE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // BREAK [expression]
  public static boolean break_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "break_statement")) return false;
    if (!nextTokenIs(builder_, BREAK)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BREAK_STATEMENT, null);
    result_ = consumeToken(builder_, BREAK);
    pinned_ = result_; // pin = 1
    result_ = result_ && break_statement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [expression]
  private static boolean break_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "break_statement_1")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LPAREN argument_list RPAREN
  //             | LPAREN RPAREN
  public static boolean call_args(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "call_args")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = call_args_0(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, LPAREN, RPAREN);
    exit_section_(builder_, marker_, CALL_ARGS, result_);
    return result_;
  }

  // LPAREN argument_list RPAREN
  private static boolean call_args_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "call_args_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CASE [expression] NEWLINE* when_clause+ [else_clause] END
  public static boolean case_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "case_statement")) return false;
    if (!nextTokenIs(builder_, CASE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CASE_STATEMENT, null);
    result_ = consumeToken(builder_, CASE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, case_statement_1(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, case_statement_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, case_statement_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, case_statement_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [expression]
  private static boolean case_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "case_statement_1")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  // NEWLINE*
  private static boolean case_statement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "case_statement_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NEWLINE)) break;
      if (!empty_element_parsed_guard_(builder_, "case_statement_2", pos_)) break;
    }
    return true;
  }

  // when_clause+
  private static boolean case_statement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "case_statement_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = when_clause(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!when_clause(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "case_statement_3", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [else_clause]
  private static boolean case_statement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "case_statement_4")) return false;
    else_clause(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // class_member*
  public static boolean class_body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_body")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_BODY, "<class body>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!class_member(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "class_body", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // [ABSTRACT] CLASS type_name [type_parameters] [superclass_clause] class_body END
  public static boolean class_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_definition")) return false;
    if (!nextTokenIs(builder_, "<class definition>", ABSTRACT, CLASS)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CLASS_DEFINITION, "<class definition>");
    result_ = class_definition_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CLASS);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, type_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, class_definition_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, class_definition_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, class_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [ABSTRACT]
  private static boolean class_definition_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_definition_0")) return false;
    consumeToken(builder_, ABSTRACT);
    return true;
  }

  // [type_parameters]
  private static boolean class_definition_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_definition_3")) return false;
    type_parameters(builder_, level_ + 1);
    return true;
  }

  // [superclass_clause]
  private static boolean class_definition_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_definition_4")) return false;
    superclass_clause(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // NEWLINE
  //                         | SEMICOLON
  //                         | annotation_usage
  //                         | method_definition
  //                         | macro_definition
  //                         | class_definition
  //                         | module_definition
  //                         | struct_definition
  //                         | enum_definition
  //                         | include_statement
  //                         | extend_statement
  //                         | alias_definition
  //                         | visibility_modifier
  //                         | property_declaration
  //                         | statement
  static boolean class_member(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_member")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEWLINE);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = annotation_usage(builder_, level_ + 1);
    if (!result_) result_ = method_definition(builder_, level_ + 1);
    if (!result_) result_ = macro_definition(builder_, level_ + 1);
    if (!result_) result_ = class_definition(builder_, level_ + 1);
    if (!result_) result_ = module_definition(builder_, level_ + 1);
    if (!result_) result_ = struct_definition(builder_, level_ + 1);
    if (!result_) result_ = enum_definition(builder_, level_ + 1);
    if (!result_) result_ = include_statement(builder_, level_ + 1);
    if (!result_) result_ = extend_statement(builder_, level_ + 1);
    if (!result_) result_ = alias_definition(builder_, level_ + 1);
    if (!result_) result_ = visibility_modifier(builder_, level_ + 1);
    if (!result_) result_ = property_declaration(builder_, level_ + 1);
    if (!result_) result_ = statement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // CLASS_VAR
  public static boolean class_var_access(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "class_var_access")) return false;
    if (!nextTokenIs(builder_, CLASS_VAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CLASS_VAR);
    exit_section_(builder_, marker_, CLASS_VAR_ACCESS, result_);
    return result_;
  }

  /* ********************************************************** */
  // range_expression ((EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ) range_expression)*
  static boolean comparison_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparison_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = range_expression(builder_, level_ + 1);
    result_ = result_ && comparison_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ) range_expression)*
  private static boolean comparison_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparison_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!comparison_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "comparison_expression_1", pos_)) break;
    }
    return true;
  }

  // (EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ) range_expression
  private static boolean comparison_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparison_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = comparison_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && range_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ
  private static boolean comparison_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparison_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, LTE);
    if (!result_) result_ = consumeToken(builder_, GTE);
    if (!result_) result_ = consumeToken(builder_, SPACESHIP);
    if (!result_) result_ = consumeToken(builder_, CASE_EQ);
    return result_;
  }

  /* ********************************************************** */
  // CONSTANT ASSIGN expression
  public static boolean constant_assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constant_assignment")) return false;
    if (!nextTokenIs(builder_, CONSTANT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONSTANT_ASSIGNMENT, null);
    result_ = consumeTokens(builder_, 2, CONSTANT, ASSIGN);
    pinned_ = result_; // pin = 2
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // top_level_statement*
  static boolean crystalFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "crystalFile")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!top_level_statement(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "crystalFile", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ELSE statement_list
  public static boolean else_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "else_clause")) return false;
    if (!nextTokenIs(builder_, ELSE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELSE);
    result_ = result_ && statement_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, ELSE_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // ELSIF expression then_clause statement_list
  public static boolean elsif_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elsif_clause")) return false;
    if (!nextTokenIs(builder_, ELSIF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELSIF);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && then_clause(builder_, level_ + 1);
    result_ = result_ && statement_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, ELSIF_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // ENSURE statement_list
  public static boolean ensure_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ensure_clause")) return false;
    if (!nextTokenIs(builder_, ENSURE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ENSURE);
    result_ = result_ && statement_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, ENSURE_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // enum_member*
  public static boolean enum_body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enum_body")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ENUM_BODY, "<enum body>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!enum_member(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "enum_body", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // CONSTANT [ASSIGN expression]
  public static boolean enum_constant(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enum_constant")) return false;
    if (!nextTokenIs(builder_, CONSTANT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONSTANT);
    result_ = result_ && enum_constant_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, ENUM_CONSTANT, result_);
    return result_;
  }

  // [ASSIGN expression]
  private static boolean enum_constant_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enum_constant_1")) return false;
    enum_constant_1_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN expression
  private static boolean enum_constant_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enum_constant_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ENUM type_name [COLON type_reference] enum_body END
  public static boolean enum_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enum_definition")) return false;
    if (!nextTokenIs(builder_, ENUM)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ENUM_DEFINITION, null);
    result_ = consumeToken(builder_, ENUM);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, enum_definition_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, enum_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [COLON type_reference]
  private static boolean enum_definition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enum_definition_2")) return false;
    enum_definition_2_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean enum_definition_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enum_definition_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // NEWLINE | SEMICOLON | annotation_usage | enum_constant | method_definition
  static boolean enum_member(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "enum_member")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEWLINE);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = annotation_usage(builder_, level_ + 1);
    if (!result_) result_ = enum_constant(builder_, level_ + 1);
    if (!result_) result_ = method_definition(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // or_expression [QUESTION expression COLON expression]
  public static boolean expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, EXPRESSION, "<expression>");
    result_ = or_expression(builder_, level_ + 1);
    result_ = result_ && expression_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [QUESTION expression COLON expression]
  private static boolean expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_1")) return false;
    expression_1_0(builder_, level_ + 1);
    return true;
  }

  // QUESTION expression COLON expression
  private static boolean expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, QUESTION);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // expression (COMMA expression)*
  public static boolean expression_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPRESSION_LIST, "<expression list>");
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && expression_list_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (COMMA expression)*
  private static boolean expression_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!expression_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "expression_list_1", pos_)) break;
    }
    return true;
  }

  // COMMA expression
  private static boolean expression_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // EXTEND type_reference
  public static boolean extend_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extend_statement")) return false;
    if (!nextTokenIs(builder_, EXTEND)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXTEND_STATEMENT, null);
    result_ = consumeToken(builder_, EXTEND);
    pinned_ = result_; // pin = 1
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // FOR IDENTIFIER IN expression then_clause statement_list END
  public static boolean for_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "for_statement")) return false;
    if (!nextTokenIs(builder_, FOR)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FOR_STATEMENT, null);
    result_ = consumeTokens(builder_, 1, FOR, IDENTIFIER, IN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, then_clause(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, statement_list(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // FUN IDENTIFIER [LPAREN parameter_list RPAREN] [COLON type_reference]
  public static boolean fun_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fun_definition")) return false;
    if (!nextTokenIs(builder_, FUN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, FUN_DEFINITION, null);
    result_ = consumeTokens(builder_, 1, FUN, IDENTIFIER);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, fun_definition_2(builder_, level_ + 1));
    result_ = pinned_ && fun_definition_3(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [LPAREN parameter_list RPAREN]
  private static boolean fun_definition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fun_definition_2")) return false;
    fun_definition_2_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN parameter_list RPAREN
  private static boolean fun_definition_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fun_definition_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COLON type_reference]
  private static boolean fun_definition_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fun_definition_3")) return false;
    fun_definition_3_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean fun_definition_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fun_definition_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN expression RPAREN
  public static boolean grouped_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "grouped_expression")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, GROUPED_EXPRESSION, result_);
    return result_;
  }

  /* ********************************************************** */
  // expression (DOUBLE_ARROW | COLON) expression
  public static boolean hash_entry(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, HASH_ENTRY, "<hash entry>");
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && hash_entry_1(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // DOUBLE_ARROW | COLON
  private static boolean hash_entry_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, DOUBLE_ARROW);
    if (!result_) result_ = consumeToken(builder_, COLON);
    return result_;
  }

  /* ********************************************************** */
  // hash_entry (COMMA hash_entry)*
  public static boolean hash_entry_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, HASH_ENTRY_LIST, "<hash entry list>");
    result_ = hash_entry(builder_, level_ + 1);
    result_ = result_ && hash_entry_list_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (COMMA hash_entry)*
  private static boolean hash_entry_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!hash_entry_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "hash_entry_list_1", pos_)) break;
    }
    return true;
  }

  // COMMA hash_entry
  private static boolean hash_entry_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && hash_entry(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LBRACE [hash_entry_list] RBRACE [OF type_reference DOUBLE_ARROW type_reference]
  public static boolean hash_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_literal")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && hash_literal_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    result_ = result_ && hash_literal_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, HASH_LITERAL, result_);
    return result_;
  }

  // [hash_entry_list]
  private static boolean hash_literal_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_literal_1")) return false;
    hash_entry_list(builder_, level_ + 1);
    return true;
  }

  // [OF type_reference DOUBLE_ARROW type_reference]
  private static boolean hash_literal_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_literal_3")) return false;
    hash_literal_3_0(builder_, level_ + 1);
    return true;
  }

  // OF type_reference DOUBLE_ARROW type_reference
  private static boolean hash_literal_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_literal_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OF);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_ARROW);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // HEREDOC_START HEREDOC_CONTENT* HEREDOC_END
  public static boolean heredoc_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "heredoc_literal")) return false;
    if (!nextTokenIs(builder_, HEREDOC_START)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, HEREDOC_START);
    result_ = result_ && heredoc_literal_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, HEREDOC_END);
    exit_section_(builder_, marker_, HEREDOC_LITERAL, result_);
    return result_;
  }

  // HEREDOC_CONTENT*
  private static boolean heredoc_literal_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "heredoc_literal_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, HEREDOC_CONTENT)) break;
      if (!empty_element_parsed_guard_(builder_, "heredoc_literal_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // IF expression then_clause statement_list elsif_clause* [else_clause] END
  public static boolean if_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "if_statement")) return false;
    if (!nextTokenIs(builder_, IF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IF_STATEMENT, null);
    result_ = consumeToken(builder_, IF);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, then_clause(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, statement_list(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, if_statement_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, if_statement_5(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // elsif_clause*
  private static boolean if_statement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "if_statement_4")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!elsif_clause(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "if_statement_4", pos_)) break;
    }
    return true;
  }

  // [else_clause]
  private static boolean if_statement_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "if_statement_5")) return false;
    else_clause(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // INCLUDE type_reference
  public static boolean include_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "include_statement")) return false;
    if (!nextTokenIs(builder_, INCLUDE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, INCLUDE_STATEMENT, null);
    result_ = consumeToken(builder_, INCLUDE);
    pinned_ = result_; // pin = 1
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // INSTANCE_SIZEOF LPAREN type_reference RPAREN
  public static boolean instance_sizeof_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "instance_sizeof_expression")) return false;
    if (!nextTokenIs(builder_, INSTANCE_SIZEOF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, INSTANCE_SIZEOF_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, INSTANCE_SIZEOF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type_reference(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // INSTANCE_VAR
  public static boolean instance_var_access(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "instance_var_access")) return false;
    if (!nextTokenIs(builder_, INSTANCE_VAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, INSTANCE_VAR);
    exit_section_(builder_, marker_, INSTANCE_VAR_ACCESS, result_);
    return result_;
  }

  /* ********************************************************** */
  // lib_member*
  public static boolean lib_body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_body")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIB_BODY, "<lib body>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!lib_member(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "lib_body", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // LIB CONSTANT lib_body END
  public static boolean lib_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_definition")) return false;
    if (!nextTokenIs(builder_, LIB)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, LIB_DEFINITION, null);
    result_ = consumeTokens(builder_, 1, LIB, CONSTANT);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, lib_body(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // NEWLINE | SEMICOLON | fun_definition | type_alias_lib | lib_struct_definition
  static boolean lib_member(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_member")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEWLINE);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = fun_definition(builder_, level_ + 1);
    if (!result_) result_ = type_alias_lib(builder_, level_ + 1);
    if (!result_) result_ = lib_struct_definition(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // STRUCT CONSTANT lib_body END
  public static boolean lib_struct_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_struct_definition")) return false;
    if (!nextTokenIs(builder_, STRUCT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, STRUCT, CONSTANT);
    result_ = result_ && lib_body(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, LIB_STRUCT_DEFINITION, result_);
    return result_;
  }

  /* ********************************************************** */
  // INTEGER_LITERAL
  //                   | FLOAT_LITERAL
  //                   | CHAR_LITERAL
  //                   | string_expression
  //                   | SYMBOL_LITERAL
  //                   | REGEX_LITERAL
  //                   | COMMAND_LITERAL
  //                   | NIL
  //                   | TRUE
  //                   | FALSE
  //                   | SELF
  //                   | SUPER
  //                   | heredoc_literal
  //                   | percent_literal
  static boolean literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literal")) return false;
    boolean result_;
    result_ = consumeToken(builder_, INTEGER_LITERAL);
    if (!result_) result_ = consumeToken(builder_, FLOAT_LITERAL);
    if (!result_) result_ = consumeToken(builder_, CHAR_LITERAL);
    if (!result_) result_ = string_expression(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, SYMBOL_LITERAL);
    if (!result_) result_ = consumeToken(builder_, REGEX_LITERAL);
    if (!result_) result_ = consumeToken(builder_, COMMAND_LITERAL);
    if (!result_) result_ = consumeToken(builder_, NIL);
    if (!result_) result_ = consumeToken(builder_, TRUE);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    if (!result_) result_ = consumeToken(builder_, SELF);
    if (!result_) result_ = consumeToken(builder_, SUPER);
    if (!result_) result_ = heredoc_literal(builder_, level_ + 1);
    if (!result_) result_ = percent_literal(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // MACRO method_name [LPAREN parameter_list RPAREN] method_body END
  public static boolean macro_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_definition")) return false;
    if (!nextTokenIs(builder_, MACRO)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MACRO_DEFINITION, null);
    result_ = consumeToken(builder_, MACRO);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, method_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, macro_definition_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, method_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [LPAREN parameter_list RPAREN]
  private static boolean macro_definition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_definition_2")) return false;
    macro_definition_2_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN parameter_list RPAREN
  private static boolean macro_definition_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_definition_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // statement_list
  public static boolean method_body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_body")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_BODY, "<method body>");
    result_ = statement_list(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // (IDENTIFIER | CONSTANT) call_args [block]
  //                          | (IDENTIFIER | CONSTANT) bare_argument_list [block]
  //                          | (IDENTIFIER | CONSTANT) block
  public static boolean method_call_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression")) return false;
    if (!nextTokenIs(builder_, "<method call expression>", CONSTANT, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_CALL_EXPRESSION, "<method call expression>");
    result_ = method_call_expression_0(builder_, level_ + 1);
    if (!result_) result_ = method_call_expression_1(builder_, level_ + 1);
    if (!result_) result_ = method_call_expression_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (IDENTIFIER | CONSTANT) call_args [block]
  private static boolean method_call_expression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = method_call_expression_0_0(builder_, level_ + 1);
    result_ = result_ && call_args(builder_, level_ + 1);
    result_ = result_ && method_call_expression_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT
  private static boolean method_call_expression_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    return result_;
  }

  // [block]
  private static boolean method_call_expression_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_0_2")) return false;
    block(builder_, level_ + 1);
    return true;
  }

  // (IDENTIFIER | CONSTANT) bare_argument_list [block]
  private static boolean method_call_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = method_call_expression_1_0(builder_, level_ + 1);
    result_ = result_ && bare_argument_list(builder_, level_ + 1);
    result_ = result_ && method_call_expression_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT
  private static boolean method_call_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    return result_;
  }

  // [block]
  private static boolean method_call_expression_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_1_2")) return false;
    block(builder_, level_ + 1);
    return true;
  }

  // (IDENTIFIER | CONSTANT) block
  private static boolean method_call_expression_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = method_call_expression_2_0(builder_, level_ + 1);
    result_ = result_ && block(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT
  private static boolean method_call_expression_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    return result_;
  }

  /* ********************************************************** */
  // [ABSTRACT] DEF method_name [LPAREN parameter_list RPAREN] [COLON type_reference] method_body END
  public static boolean method_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition")) return false;
    if (!nextTokenIs(builder_, "<method definition>", ABSTRACT, DEF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_DEFINITION, "<method definition>");
    result_ = method_definition_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DEF);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, method_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, method_definition_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, method_definition_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, method_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [ABSTRACT]
  private static boolean method_definition_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0")) return false;
    consumeToken(builder_, ABSTRACT);
    return true;
  }

  // [LPAREN parameter_list RPAREN]
  private static boolean method_definition_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_3")) return false;
    method_definition_3_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN parameter_list RPAREN
  private static boolean method_definition_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_3_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [COLON type_reference]
  private static boolean method_definition_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_4")) return false;
    method_definition_4_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean method_definition_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER
  //               | CONSTANT
  //               | operator_method_name
  //               | SELF DOT (IDENTIFIER | CONSTANT | operator_method_name)
  public static boolean method_name(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_name")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_NAME, "<method name>");
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    if (!result_) result_ = operator_method_name(builder_, level_ + 1);
    if (!result_) result_ = method_name_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // SELF DOT (IDENTIFIER | CONSTANT | operator_method_name)
  private static boolean method_name_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_name_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, SELF, DOT);
    result_ = result_ && method_name_3_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT | operator_method_name
  private static boolean method_name_3_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_name_3_2")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    if (!result_) result_ = operator_method_name(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // MODULE type_name [type_parameters] class_body END
  public static boolean module_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "module_definition")) return false;
    if (!nextTokenIs(builder_, MODULE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MODULE_DEFINITION, null);
    result_ = consumeToken(builder_, MODULE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, module_definition_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, class_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [type_parameters]
  private static boolean module_definition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "module_definition_2")) return false;
    type_parameters(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // power_expression ((STAR | SLASH | DOUBLE_SLASH | PERCENT) power_expression)*
  static boolean multiplicative_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicative_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = power_expression(builder_, level_ + 1);
    result_ = result_ && multiplicative_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((STAR | SLASH | DOUBLE_SLASH | PERCENT) power_expression)*
  private static boolean multiplicative_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicative_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!multiplicative_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "multiplicative_expression_1", pos_)) break;
    }
    return true;
  }

  // (STAR | SLASH | DOUBLE_SLASH | PERCENT) power_expression
  private static boolean multiplicative_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicative_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = multiplicative_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && power_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STAR | SLASH | DOUBLE_SLASH | PERCENT
  private static boolean multiplicative_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicative_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_SLASH);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    return result_;
  }

  /* ********************************************************** */
  // NEXT [expression]
  public static boolean next_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "next_statement")) return false;
    if (!nextTokenIs(builder_, NEXT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, NEXT_STATEMENT, null);
    result_ = consumeToken(builder_, NEXT);
    pinned_ = result_; // pin = 1
    result_ = result_ && next_statement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [expression]
  private static boolean next_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "next_statement_1")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // BANG not_expression | comparison_expression
  static boolean not_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = not_expression_0(builder_, level_ + 1);
    if (!result_) result_ = comparison_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BANG not_expression
  private static boolean not_expression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "not_expression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BANG);
    result_ = result_ && not_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // PLUS | MINUS | STAR | SLASH | PERCENT | AMPERSAND | PIPE | CARET | TILDE
  //                                 | DOUBLE_STAR | LSHIFT | RSHIFT | EQ | NEQ | LT | GT | LTE | GTE
  //                                 | SPACESHIP | CASE_EQ | LBRACKET RBRACKET | LBRACKET RBRACKET ASSIGN
  static boolean operator_method_name(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "operator_method_name")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    if (!result_) result_ = consumeToken(builder_, PIPE);
    if (!result_) result_ = consumeToken(builder_, CARET);
    if (!result_) result_ = consumeToken(builder_, TILDE);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, LSHIFT);
    if (!result_) result_ = consumeToken(builder_, RSHIFT);
    if (!result_) result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, LTE);
    if (!result_) result_ = consumeToken(builder_, GTE);
    if (!result_) result_ = consumeToken(builder_, SPACESHIP);
    if (!result_) result_ = consumeToken(builder_, CASE_EQ);
    if (!result_) result_ = parseTokens(builder_, 0, LBRACKET, RBRACKET);
    if (!result_) result_ = parseTokens(builder_, 0, LBRACKET, RBRACKET, ASSIGN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // xor_expression (PIPE xor_expression)*
  static boolean or_bitwise_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_bitwise_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = xor_expression(builder_, level_ + 1);
    result_ = result_ && or_bitwise_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (PIPE xor_expression)*
  private static boolean or_bitwise_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_bitwise_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!or_bitwise_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "or_bitwise_expression_1", pos_)) break;
    }
    return true;
  }

  // PIPE xor_expression
  private static boolean or_bitwise_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_bitwise_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && xor_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // and_expression (OR_OR and_expression)*
  static boolean or_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = and_expression(builder_, level_ + 1);
    result_ = result_ && or_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (OR_OR and_expression)*
  private static boolean or_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!or_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "or_expression_1", pos_)) break;
    }
    return true;
  }

  // OR_OR and_expression
  private static boolean or_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OR_OR);
    result_ = result_ && and_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // [STAR | DOUBLE_STAR | AMPERSAND] IDENTIFIER [COLON type_reference] [ASSIGN expression]
  //             | [STAR | DOUBLE_STAR | AMPERSAND] instance_var_access [COLON type_reference] [ASSIGN expression]
  public static boolean parameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAMETER, "<parameter>");
    result_ = parameter_0(builder_, level_ + 1);
    if (!result_) result_ = parameter_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [STAR | DOUBLE_STAR | AMPERSAND] IDENTIFIER [COLON type_reference] [ASSIGN expression]
  private static boolean parameter_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parameter_0_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IDENTIFIER);
    result_ = result_ && parameter_0_2(builder_, level_ + 1);
    result_ = result_ && parameter_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [STAR | DOUBLE_STAR | AMPERSAND]
  private static boolean parameter_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_0")) return false;
    parameter_0_0_0(builder_, level_ + 1);
    return true;
  }

  // STAR | DOUBLE_STAR | AMPERSAND
  private static boolean parameter_0_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    return result_;
  }

  // [COLON type_reference]
  private static boolean parameter_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_2")) return false;
    parameter_0_2_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean parameter_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ASSIGN expression]
  private static boolean parameter_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_3")) return false;
    parameter_0_3_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN expression
  private static boolean parameter_0_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [STAR | DOUBLE_STAR | AMPERSAND] instance_var_access [COLON type_reference] [ASSIGN expression]
  private static boolean parameter_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parameter_1_0(builder_, level_ + 1);
    result_ = result_ && instance_var_access(builder_, level_ + 1);
    result_ = result_ && parameter_1_2(builder_, level_ + 1);
    result_ = result_ && parameter_1_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [STAR | DOUBLE_STAR | AMPERSAND]
  private static boolean parameter_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_0")) return false;
    parameter_1_0_0(builder_, level_ + 1);
    return true;
  }

  // STAR | DOUBLE_STAR | AMPERSAND
  private static boolean parameter_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    return result_;
  }

  // [COLON type_reference]
  private static boolean parameter_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_2")) return false;
    parameter_1_2_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean parameter_1_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ASSIGN expression]
  private static boolean parameter_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_3")) return false;
    parameter_1_3_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN expression
  private static boolean parameter_1_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // [parameter (COMMA parameter)*]
  public static boolean parameter_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAMETER_LIST, "<parameter list>");
    parameter_list_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  // parameter (COMMA parameter)*
  private static boolean parameter_list_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parameter(builder_, level_ + 1);
    result_ = result_ && parameter_list_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA parameter)*
  private static boolean parameter_list_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!parameter_list_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parameter_list_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA parameter
  private static boolean parameter_list_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && parameter(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // PERCENT_LITERAL_BEGIN PERCENT_LITERAL_END
  public static boolean percent_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "percent_literal")) return false;
    if (!nextTokenIs(builder_, PERCENT_LITERAL_BEGIN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, PERCENT_LITERAL_BEGIN, PERCENT_LITERAL_END);
    exit_section_(builder_, marker_, PERCENT_LITERAL, result_);
    return result_;
  }

  /* ********************************************************** */
  // POINTEROF LPAREN (instance_var_access | class_var_access | variable_reference) RPAREN
  public static boolean pointerof_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pointerof_expression")) return false;
    if (!nextTokenIs(builder_, POINTEROF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, POINTEROF_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, POINTEROF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, pointerof_expression_2(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // instance_var_access | class_var_access | variable_reference
  private static boolean pointerof_expression_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pointerof_expression_2")) return false;
    boolean result_;
    result_ = instance_var_access(builder_, level_ + 1);
    if (!result_) result_ = class_var_access(builder_, level_ + 1);
    if (!result_) result_ = variable_reference(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // primary_expression postfix_op*
  static boolean postfix_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = primary_expression(builder_, level_ + 1);
    result_ = result_ && postfix_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // postfix_op*
  private static boolean postfix_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!postfix_op(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "postfix_expression_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // DOT (IDENTIFIER | CONSTANT) [call_args | bare_argument_list] [block]
  //                      | DOUBLE_COLON CONSTANT
  //                      | LBRACKET argument_list RBRACKET
  //                      | call_args [block]
  //                      | DOT IDENTIFIER assign_op expression
  static boolean postfix_op(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = postfix_op_0(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, DOUBLE_COLON, CONSTANT);
    if (!result_) result_ = postfix_op_2(builder_, level_ + 1);
    if (!result_) result_ = postfix_op_3(builder_, level_ + 1);
    if (!result_) result_ = postfix_op_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOT (IDENTIFIER | CONSTANT) [call_args | bare_argument_list] [block]
  private static boolean postfix_op_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && postfix_op_0_1(builder_, level_ + 1);
    result_ = result_ && postfix_op_0_2(builder_, level_ + 1);
    result_ = result_ && postfix_op_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT
  private static boolean postfix_op_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_0_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    return result_;
  }

  // [call_args | bare_argument_list]
  private static boolean postfix_op_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_0_2")) return false;
    postfix_op_0_2_0(builder_, level_ + 1);
    return true;
  }

  // call_args | bare_argument_list
  private static boolean postfix_op_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_0_2_0")) return false;
    boolean result_;
    result_ = call_args(builder_, level_ + 1);
    if (!result_) result_ = bare_argument_list(builder_, level_ + 1);
    return result_;
  }

  // [block]
  private static boolean postfix_op_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_0_3")) return false;
    block(builder_, level_ + 1);
    return true;
  }

  // LBRACKET argument_list RBRACKET
  private static boolean postfix_op_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // call_args [block]
  private static boolean postfix_op_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = call_args(builder_, level_ + 1);
    result_ = result_ && postfix_op_3_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [block]
  private static boolean postfix_op_3_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_3_1")) return false;
    block(builder_, level_ + 1);
    return true;
  }

  // DOT IDENTIFIER assign_op expression
  private static boolean postfix_op_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOT, IDENTIFIER);
    result_ = result_ && assign_op(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // unary_expression (DOUBLE_STAR unary_expression)*
  static boolean power_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "power_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = unary_expression(builder_, level_ + 1);
    result_ = result_ && power_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (DOUBLE_STAR unary_expression)*
  private static boolean power_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "power_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!power_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "power_expression_1", pos_)) break;
    }
    return true;
  }

  // DOUBLE_STAR unary_expression
  private static boolean power_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "power_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_STAR);
    result_ = result_ && unary_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // grouped_expression
  //                              | array_literal
  //                              | hash_literal
  //                              | tuple_literal
  //                              | method_call_expression
  //                              | literal
  //                              | instance_var_access
  //                              | class_var_access
  //                              | variable_reference
  //                              | typeof_expression
  //                              | sizeof_expression
  //                              | instance_sizeof_expression
  //                              | pointerof_expression
  //                              | if_statement
  //                              | unless_statement
  //                              | case_statement
  //                              | begin_statement
  static boolean primary_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "primary_expression")) return false;
    boolean result_;
    result_ = grouped_expression(builder_, level_ + 1);
    if (!result_) result_ = array_literal(builder_, level_ + 1);
    if (!result_) result_ = hash_literal(builder_, level_ + 1);
    if (!result_) result_ = tuple_literal(builder_, level_ + 1);
    if (!result_) result_ = method_call_expression(builder_, level_ + 1);
    if (!result_) result_ = literal(builder_, level_ + 1);
    if (!result_) result_ = instance_var_access(builder_, level_ + 1);
    if (!result_) result_ = class_var_access(builder_, level_ + 1);
    if (!result_) result_ = variable_reference(builder_, level_ + 1);
    if (!result_) result_ = typeof_expression(builder_, level_ + 1);
    if (!result_) result_ = sizeof_expression(builder_, level_ + 1);
    if (!result_) result_ = instance_sizeof_expression(builder_, level_ + 1);
    if (!result_) result_ = pointerof_expression(builder_, level_ + 1);
    if (!result_) result_ = if_statement(builder_, level_ + 1);
    if (!result_) result_ = unless_statement(builder_, level_ + 1);
    if (!result_) result_ = case_statement(builder_, level_ + 1);
    if (!result_) result_ = begin_statement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // (instance_var_access | class_var_access | IDENTIFIER | SELF DOT IDENTIFIER) COLON type_reference [ASSIGN expression]
  public static boolean property_declaration(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_declaration")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PROPERTY_DECLARATION, "<property declaration>");
    result_ = property_declaration_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && property_declaration_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // instance_var_access | class_var_access | IDENTIFIER | SELF DOT IDENTIFIER
  private static boolean property_declaration_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_declaration_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = instance_var_access(builder_, level_ + 1);
    if (!result_) result_ = class_var_access(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = parseTokens(builder_, 0, SELF, DOT, IDENTIFIER);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ASSIGN expression]
  private static boolean property_declaration_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_declaration_3")) return false;
    property_declaration_3_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN expression
  private static boolean property_declaration_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_declaration_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // or_bitwise_expression [(DOTDOT | DOTDOTDOT) or_bitwise_expression]
  static boolean range_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "range_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = or_bitwise_expression(builder_, level_ + 1);
    result_ = result_ && range_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [(DOTDOT | DOTDOTDOT) or_bitwise_expression]
  private static boolean range_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "range_expression_1")) return false;
    range_expression_1_0(builder_, level_ + 1);
    return true;
  }

  // (DOTDOT | DOTDOTDOT) or_bitwise_expression
  private static boolean range_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "range_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = range_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && or_bitwise_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOTDOT | DOTDOTDOT
  private static boolean range_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "range_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, DOTDOT);
    if (!result_) result_ = consumeToken(builder_, DOTDOTDOT);
    return result_;
  }

  /* ********************************************************** */
  // REQUIRE string_expression
  public static boolean require_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "require_statement")) return false;
    if (!nextTokenIs(builder_, REQUIRE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, REQUIRE_STATEMENT, null);
    result_ = consumeToken(builder_, REQUIRE);
    pinned_ = result_; // pin = 1
    result_ = result_ && string_expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // RESCUE [IDENTIFIER [COLON type_reference]] then_clause statement_list
  public static boolean rescue_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rescue_clause")) return false;
    if (!nextTokenIs(builder_, RESCUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, RESCUE);
    result_ = result_ && rescue_clause_1(builder_, level_ + 1);
    result_ = result_ && then_clause(builder_, level_ + 1);
    result_ = result_ && statement_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, RESCUE_CLAUSE, result_);
    return result_;
  }

  // [IDENTIFIER [COLON type_reference]]
  private static boolean rescue_clause_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rescue_clause_1")) return false;
    rescue_clause_1_0(builder_, level_ + 1);
    return true;
  }

  // IDENTIFIER [COLON type_reference]
  private static boolean rescue_clause_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rescue_clause_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IDENTIFIER);
    result_ = result_ && rescue_clause_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COLON type_reference]
  private static boolean rescue_clause_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rescue_clause_1_0_1")) return false;
    rescue_clause_1_0_1_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean rescue_clause_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "rescue_clause_1_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // RETURN [expression]
  public static boolean return_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "return_statement")) return false;
    if (!nextTokenIs(builder_, RETURN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, RETURN_STATEMENT, null);
    result_ = consumeToken(builder_, RETURN);
    pinned_ = result_; // pin = 1
    result_ = result_ && return_statement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [expression]
  private static boolean return_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "return_statement_1")) return false;
    expression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // additive_expression ((LSHIFT | RSHIFT) additive_expression)*
  static boolean shift_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shift_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = additive_expression(builder_, level_ + 1);
    result_ = result_ && shift_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((LSHIFT | RSHIFT) additive_expression)*
  private static boolean shift_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shift_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!shift_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "shift_expression_1", pos_)) break;
    }
    return true;
  }

  // (LSHIFT | RSHIFT) additive_expression
  private static boolean shift_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shift_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = shift_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && additive_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LSHIFT | RSHIFT
  private static boolean shift_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shift_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, LSHIFT);
    if (!result_) result_ = consumeToken(builder_, RSHIFT);
    return result_;
  }

  /* ********************************************************** */
  // SIZEOF LPAREN type_reference RPAREN
  public static boolean sizeof_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sizeof_expression")) return false;
    if (!nextTokenIs(builder_, SIZEOF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SIZEOF_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, SIZEOF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type_reference(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // if_statement
  //             | unless_statement
  //             | while_statement
  //             | until_statement
  //             | case_statement
  //             | begin_statement
  //             | for_statement
  //             | return_statement
  //             | break_statement
  //             | next_statement
  //             | yield_statement
  //             | assignment
  //             | constant_assignment
  //             | expression
  public static boolean statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATEMENT, "<statement>");
    result_ = if_statement(builder_, level_ + 1);
    if (!result_) result_ = unless_statement(builder_, level_ + 1);
    if (!result_) result_ = while_statement(builder_, level_ + 1);
    if (!result_) result_ = until_statement(builder_, level_ + 1);
    if (!result_) result_ = case_statement(builder_, level_ + 1);
    if (!result_) result_ = begin_statement(builder_, level_ + 1);
    if (!result_) result_ = for_statement(builder_, level_ + 1);
    if (!result_) result_ = return_statement(builder_, level_ + 1);
    if (!result_) result_ = break_statement(builder_, level_ + 1);
    if (!result_) result_ = next_statement(builder_, level_ + 1);
    if (!result_) result_ = yield_statement(builder_, level_ + 1);
    if (!result_) result_ = assignment(builder_, level_ + 1);
    if (!result_) result_ = constant_assignment(builder_, level_ + 1);
    if (!result_) result_ = expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // statement_or_separator*
  public static boolean statement_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_list")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STATEMENT_LIST, "<statement list>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!statement_or_separator(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "statement_list", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // NEWLINE | SEMICOLON | statement
  static boolean statement_or_separator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "statement_or_separator")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEWLINE);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = statement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // (STRING_LITERAL | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END)+
  public static boolean string_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "string_expression")) return false;
    if (!nextTokenIs(builder_, "<string expression>", STRING_INTERPOLATION_BEGIN, STRING_LITERAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STRING_EXPRESSION, "<string expression>");
    result_ = string_expression_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!string_expression_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "string_expression", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // STRING_LITERAL | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END
  private static boolean string_expression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "string_expression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING_LITERAL);
    if (!result_) result_ = string_expression_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END
  private static boolean string_expression_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "string_expression_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING_INTERPOLATION_BEGIN);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, STRING_INTERPOLATION_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // [ABSTRACT] STRUCT type_name [type_parameters] [superclass_clause] class_body END
  public static boolean struct_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "struct_definition")) return false;
    if (!nextTokenIs(builder_, "<struct definition>", ABSTRACT, STRUCT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, STRUCT_DEFINITION, "<struct definition>");
    result_ = struct_definition_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, STRUCT);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, type_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, struct_definition_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, struct_definition_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, class_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [ABSTRACT]
  private static boolean struct_definition_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "struct_definition_0")) return false;
    consumeToken(builder_, ABSTRACT);
    return true;
  }

  // [type_parameters]
  private static boolean struct_definition_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "struct_definition_3")) return false;
    type_parameters(builder_, level_ + 1);
    return true;
  }

  // [superclass_clause]
  private static boolean struct_definition_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "struct_definition_4")) return false;
    superclass_clause(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LT type_reference
  public static boolean superclass_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "superclass_clause")) return false;
    if (!nextTokenIs(builder_, LT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LT);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, SUPERCLASS_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // THEN | NEWLINE | SEMICOLON
  static boolean then_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "then_clause")) return false;
    boolean result_;
    result_ = consumeToken(builder_, THEN);
    if (!result_) result_ = consumeToken(builder_, NEWLINE);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    return result_;
  }

  /* ********************************************************** */
  // NEWLINE
  //                               | SEMICOLON
  //                               | require_statement
  //                               | include_statement
  //                               | extend_statement
  //                               | annotation_usage
  //                               | class_definition
  //                               | module_definition
  //                               | struct_definition
  //                               | enum_definition
  //                               | lib_definition
  //                               | annotation_definition
  //                               | method_definition
  //                               | macro_definition
  //                               | alias_definition
  //                               | visibility_modifier
  //                               | statement
  static boolean top_level_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_level_statement")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEWLINE);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = require_statement(builder_, level_ + 1);
    if (!result_) result_ = include_statement(builder_, level_ + 1);
    if (!result_) result_ = extend_statement(builder_, level_ + 1);
    if (!result_) result_ = annotation_usage(builder_, level_ + 1);
    if (!result_) result_ = class_definition(builder_, level_ + 1);
    if (!result_) result_ = module_definition(builder_, level_ + 1);
    if (!result_) result_ = struct_definition(builder_, level_ + 1);
    if (!result_) result_ = enum_definition(builder_, level_ + 1);
    if (!result_) result_ = lib_definition(builder_, level_ + 1);
    if (!result_) result_ = annotation_definition(builder_, level_ + 1);
    if (!result_) result_ = method_definition(builder_, level_ + 1);
    if (!result_) result_ = macro_definition(builder_, level_ + 1);
    if (!result_) result_ = alias_definition(builder_, level_ + 1);
    if (!result_) result_ = visibility_modifier(builder_, level_ + 1);
    if (!result_) result_ = statement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // LBRACE expression_list RBRACE
  public static boolean tuple_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tuple_literal")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && expression_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, TUPLE_LITERAL, result_);
    return result_;
  }

  /* ********************************************************** */
  // ALIAS CONSTANT ASSIGN type_reference
  public static boolean type_alias_lib(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_alias_lib")) return false;
    if (!nextTokenIs(builder_, ALIAS)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ALIAS, CONSTANT, ASSIGN);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, TYPE_ALIAS_LIB, result_);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN type_reference (COMMA type_reference)* RPAREN
  public static boolean type_arguments(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_arguments")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && type_arguments_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, TYPE_ARGUMENTS, result_);
    return result_;
  }

  // (COMMA type_reference)*
  private static boolean type_arguments_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_arguments_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_arguments_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_arguments_2", pos_)) break;
    }
    return true;
  }

  // COMMA type_reference
  private static boolean type_arguments_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_arguments_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CONSTANT (DOUBLE_COLON CONSTANT)*
  public static boolean type_name(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_name")) return false;
    if (!nextTokenIs(builder_, CONSTANT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CONSTANT);
    result_ = result_ && type_name_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, TYPE_NAME, result_);
    return result_;
  }

  // (DOUBLE_COLON CONSTANT)*
  private static boolean type_name_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_name_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_name_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_name_1", pos_)) break;
    }
    return true;
  }

  // DOUBLE_COLON CONSTANT
  private static boolean type_name_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_name_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOUBLE_COLON, CONSTANT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN CONSTANT (COMMA CONSTANT)* RPAREN
  public static boolean type_parameters(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameters")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LPAREN, CONSTANT);
    result_ = result_ && type_parameters_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, TYPE_PARAMETERS, result_);
    return result_;
  }

  // (COMMA CONSTANT)*
  private static boolean type_parameters_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameters_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_parameters_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_parameters_2", pos_)) break;
    }
    return true;
  }

  // COMMA CONSTANT
  private static boolean type_parameters_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameters_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COMMA, CONSTANT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // [DOUBLE_COLON] CONSTANT (DOUBLE_COLON CONSTANT)*
  public static boolean type_path(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_path")) return false;
    if (!nextTokenIs(builder_, "<type path>", CONSTANT, DOUBLE_COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPE_PATH, "<type path>");
    result_ = type_path_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CONSTANT);
    result_ = result_ && type_path_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [DOUBLE_COLON]
  private static boolean type_path_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_path_0")) return false;
    consumeToken(builder_, DOUBLE_COLON);
    return true;
  }

  // (DOUBLE_COLON CONSTANT)*
  private static boolean type_path_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_path_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_path_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_path_2", pos_)) break;
    }
    return true;
  }

  // DOUBLE_COLON CONSTANT
  private static boolean type_path_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_path_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, DOUBLE_COLON, CONSTANT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // type_union
  public static boolean type_reference(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_reference")) return false;
    if (!nextTokenIs(builder_, "<type reference>", CONSTANT, DOUBLE_COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPE_REFERENCE, "<type reference>");
    result_ = type_union(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // type_path [type_arguments] [QUESTION] [STAR] [DOUBLE_STAR]
  static boolean type_single(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single")) return false;
    if (!nextTokenIs(builder_, "", CONSTANT, DOUBLE_COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type_path(builder_, level_ + 1);
    result_ = result_ && type_single_1(builder_, level_ + 1);
    result_ = result_ && type_single_2(builder_, level_ + 1);
    result_ = result_ && type_single_3(builder_, level_ + 1);
    result_ = result_ && type_single_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [type_arguments]
  private static boolean type_single_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_1")) return false;
    type_arguments(builder_, level_ + 1);
    return true;
  }

  // [QUESTION]
  private static boolean type_single_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_2")) return false;
    consumeToken(builder_, QUESTION);
    return true;
  }

  // [STAR]
  private static boolean type_single_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_3")) return false;
    consumeToken(builder_, STAR);
    return true;
  }

  // [DOUBLE_STAR]
  private static boolean type_single_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_4")) return false;
    consumeToken(builder_, DOUBLE_STAR);
    return true;
  }

  /* ********************************************************** */
  // type_single (PIPE type_single)*
  static boolean type_union(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_union")) return false;
    if (!nextTokenIs(builder_, "", CONSTANT, DOUBLE_COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type_single(builder_, level_ + 1);
    result_ = result_ && type_union_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (PIPE type_single)*
  private static boolean type_union_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_union_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_union_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_union_1", pos_)) break;
    }
    return true;
  }

  // PIPE type_single
  private static boolean type_union_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_union_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && type_single(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // TYPEOF LPAREN expression RPAREN
  public static boolean typeof_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "typeof_expression")) return false;
    if (!nextTokenIs(builder_, TYPEOF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TYPEOF_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, TYPEOF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // (PLUS | MINUS | TILDE | AMPERSAND | STAR) unary_expression
  //                             | postfix_expression
  static boolean unary_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unary_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = unary_expression_0(builder_, level_ + 1);
    if (!result_) result_ = postfix_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (PLUS | MINUS | TILDE | AMPERSAND | STAR) unary_expression
  private static boolean unary_expression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unary_expression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = unary_expression_0_0(builder_, level_ + 1);
    result_ = result_ && unary_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PLUS | MINUS | TILDE | AMPERSAND | STAR
  private static boolean unary_expression_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unary_expression_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, TILDE);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    if (!result_) result_ = consumeToken(builder_, STAR);
    return result_;
  }

  /* ********************************************************** */
  // UNLESS expression then_clause statement_list [else_clause] END
  public static boolean unless_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unless_statement")) return false;
    if (!nextTokenIs(builder_, UNLESS)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UNLESS_STATEMENT, null);
    result_ = consumeToken(builder_, UNLESS);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, then_clause(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, statement_list(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, unless_statement_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [else_clause]
  private static boolean unless_statement_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unless_statement_4")) return false;
    else_clause(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // UNTIL expression then_clause statement_list END
  public static boolean until_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "until_statement")) return false;
    if (!nextTokenIs(builder_, UNTIL)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UNTIL_STATEMENT, null);
    result_ = consumeToken(builder_, UNTIL);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, then_clause(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, statement_list(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // instance_var_access | class_var_access | GLOBAL_VAR | IDENTIFIER
  static boolean variable(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variable")) return false;
    boolean result_;
    result_ = instance_var_access(builder_, level_ + 1);
    if (!result_) result_ = class_var_access(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, GLOBAL_VAR);
    if (!result_) result_ = consumeToken(builder_, IDENTIFIER);
    return result_;
  }

  /* ********************************************************** */
  // GLOBAL_VAR | IDENTIFIER | CONSTANT
  public static boolean variable_reference(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "variable_reference")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VARIABLE_REFERENCE, "<variable reference>");
    result_ = consumeToken(builder_, GLOBAL_VAR);
    if (!result_) result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // (PRIVATE | PROTECTED) (method_definition | macro_definition | class_definition | struct_definition | constant_assignment | statement)
  public static boolean visibility_modifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier")) return false;
    if (!nextTokenIs(builder_, "<visibility modifier>", PRIVATE, PROTECTED)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VISIBILITY_MODIFIER, "<visibility modifier>");
    result_ = visibility_modifier_0(builder_, level_ + 1);
    result_ = result_ && visibility_modifier_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // PRIVATE | PROTECTED
  private static boolean visibility_modifier_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PRIVATE);
    if (!result_) result_ = consumeToken(builder_, PROTECTED);
    return result_;
  }

  // method_definition | macro_definition | class_definition | struct_definition | constant_assignment | statement
  private static boolean visibility_modifier_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_1")) return false;
    boolean result_;
    result_ = method_definition(builder_, level_ + 1);
    if (!result_) result_ = macro_definition(builder_, level_ + 1);
    if (!result_) result_ = class_definition(builder_, level_ + 1);
    if (!result_) result_ = struct_definition(builder_, level_ + 1);
    if (!result_) result_ = constant_assignment(builder_, level_ + 1);
    if (!result_) result_ = statement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // WHEN expression_list then_clause statement_list
  public static boolean when_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "when_clause")) return false;
    if (!nextTokenIs(builder_, WHEN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, WHEN);
    result_ = result_ && expression_list(builder_, level_ + 1);
    result_ = result_ && then_clause(builder_, level_ + 1);
    result_ = result_ && statement_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, WHEN_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // WHILE expression then_clause statement_list END
  public static boolean while_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "while_statement")) return false;
    if (!nextTokenIs(builder_, WHILE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, WHILE_STATEMENT, null);
    result_ = consumeToken(builder_, WHILE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, then_clause(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, statement_list(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // and_bitwise_expression (CARET and_bitwise_expression)*
  static boolean xor_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xor_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = and_bitwise_expression(builder_, level_ + 1);
    result_ = result_ && xor_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (CARET and_bitwise_expression)*
  private static boolean xor_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xor_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!xor_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "xor_expression_1", pos_)) break;
    }
    return true;
  }

  // CARET and_bitwise_expression
  private static boolean xor_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xor_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CARET);
    result_ = result_ && and_bitwise_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // YIELD [LPAREN argument_list RPAREN]
  public static boolean yield_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_statement")) return false;
    if (!nextTokenIs(builder_, YIELD)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, YIELD_STATEMENT, null);
    result_ = consumeToken(builder_, YIELD);
    pinned_ = result_; // pin = 1
    result_ = result_ && yield_statement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [LPAREN argument_list RPAREN]
  private static boolean yield_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_statement_1")) return false;
    yield_statement_1_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN argument_list RPAREN
  private static boolean yield_statement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_statement_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

}
