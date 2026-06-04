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
  // NEWLINE*
  static boolean NLS(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "NLS")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NEWLINE)) break;
      if (!empty_element_parsed_guard_(builder_, "NLS", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // multiplicative_expression ((PLUS | MINUS | WRAP_PLUS | WRAP_MINUS) NLS multiplicative_expression)*
  static boolean additive_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additive_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = multiplicative_expression(builder_, level_ + 1);
    result_ = result_ && additive_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((PLUS | MINUS | WRAP_PLUS | WRAP_MINUS) NLS multiplicative_expression)*
  private static boolean additive_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additive_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!additive_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "additive_expression_1", pos_)) break;
    }
    return true;
  }

  // (PLUS | MINUS | WRAP_PLUS | WRAP_MINUS) NLS multiplicative_expression
  private static boolean additive_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additive_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = additive_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && multiplicative_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PLUS | MINUS | WRAP_PLUS | WRAP_MINUS
  private static boolean additive_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "additive_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, WRAP_PLUS);
    if (!result_) result_ = consumeToken(builder_, WRAP_MINUS);
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
  // shift_expression (AMPERSAND NLS shift_expression)*
  static boolean and_bitwise_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_bitwise_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = shift_expression(builder_, level_ + 1);
    result_ = result_ && and_bitwise_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AMPERSAND NLS shift_expression)*
  private static boolean and_bitwise_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_bitwise_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!and_bitwise_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "and_bitwise_expression_1", pos_)) break;
    }
    return true;
  }

  // AMPERSAND NLS shift_expression
  private static boolean and_bitwise_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_bitwise_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AMPERSAND);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && shift_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // not_expression (AND_AND NLS not_expression)*
  static boolean and_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = not_expression(builder_, level_ + 1);
    result_ = result_ && and_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AND_AND NLS not_expression)*
  private static boolean and_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!and_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "and_expression_1", pos_)) break;
    }
    return true;
  }

  // AND_AND NLS not_expression
  private static boolean and_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "and_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AND_AND);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && not_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ANNOTATION type_name NEWLINE* END
  public static boolean annotation_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation_definition")) return false;
    if (!nextTokenIs(builder_, ANNOTATION)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ANNOTATION_DEFINITION, null);
    result_ = consumeToken(builder_, ANNOTATION);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, type_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, annotation_definition_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // NEWLINE*
  private static boolean annotation_definition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation_definition_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NEWLINE)) break;
      if (!empty_element_parsed_guard_(builder_, "annotation_definition_2", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // AT LBRACKET type_path [LPAREN NLS argument_list NLS RPAREN] RBRACKET
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

  // [LPAREN NLS argument_list NLS RPAREN]
  private static boolean annotation_usage_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation_usage_3")) return false;
    annotation_usage_3_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN NLS argument_list NLS RPAREN
  private static boolean annotation_usage_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "annotation_usage_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // STAR expression
  //            | DOUBLE_STAR expression
  //            | AMPERSAND expression
  //            | OUT IDENTIFIER
  //            | [IDENTIFIER COLON] expression
  public static boolean argument(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ARGUMENT, "<argument>");
    result_ = argument_0(builder_, level_ + 1);
    if (!result_) result_ = argument_1(builder_, level_ + 1);
    if (!result_) result_ = argument_2(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, OUT, IDENTIFIER);
    if (!result_) result_ = argument_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // STAR expression
  private static boolean argument_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STAR);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOUBLE_STAR expression
  private static boolean argument_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_STAR);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AMPERSAND expression
  private static boolean argument_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AMPERSAND);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [IDENTIFIER COLON] expression
  private static boolean argument_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = argument_4_0(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [IDENTIFIER COLON]
  private static boolean argument_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_4_0")) return false;
    parseTokens(builder_, 0, IDENTIFIER, COLON);
    return true;
  }

  /* ********************************************************** */
  // argument (NLS COMMA NLS argument)* [NLS COMMA]
  public static boolean argument_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ARGUMENT_LIST, "<argument list>");
    result_ = argument(builder_, level_ + 1);
    result_ = result_ && argument_list_1(builder_, level_ + 1);
    result_ = result_ && argument_list_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (NLS COMMA NLS argument)*
  private static boolean argument_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!argument_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "argument_list_1", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS argument
  private static boolean argument_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && argument(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [NLS COMMA]
  private static boolean argument_list_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_2")) return false;
    argument_list_2_0(builder_, level_ + 1);
    return true;
  }

  // NLS COMMA
  private static boolean argument_list_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "argument_list_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LBRACKET NLS [expression_list] NLS RBRACKET [OF type_reference]
  public static boolean array_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_literal")) return false;
    if (!nextTokenIs(builder_, LBRACKET)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && array_literal_2(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    result_ = result_ && array_literal_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, ARRAY_LITERAL, result_);
    return result_;
  }

  // [expression_list]
  private static boolean array_literal_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_literal_2")) return false;
    expression_list(builder_, level_ + 1);
    return true;
  }

  // [OF type_reference]
  private static boolean array_literal_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_literal_5")) return false;
    array_literal_5_0(builder_, level_ + 1);
    return true;
  }

  // OF type_reference
  private static boolean array_literal_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_literal_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OF);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // string_expression (NLS COMMA NLS string_expression)*
  static boolean asm_clobber_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_clobber_list")) return false;
    if (!nextTokenIs(builder_, "", STRING_INTERPOLATION_BEGIN, STRING_LITERAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = string_expression(builder_, level_ + 1);
    result_ = result_ && asm_clobber_list_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (NLS COMMA NLS string_expression)*
  private static boolean asm_clobber_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_clobber_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!asm_clobber_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "asm_clobber_list_1", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS string_expression
  private static boolean asm_clobber_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_clobber_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && string_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // ASM LPAREN NLS string_expression NLS [asm_sections] NLS RPAREN
  public static boolean asm_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_expression")) return false;
    if (!nextTokenIs(builder_, ASM)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASM_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, ASM, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, NLS(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, string_expression(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, asm_expression_5(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [asm_sections]
  private static boolean asm_expression_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_expression_5")) return false;
    asm_sections(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // string_expression LPAREN expression RPAREN
  public static boolean asm_operand(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_operand")) return false;
    if (!nextTokenIs(builder_, "<asm operand>", STRING_INTERPOLATION_BEGIN, STRING_LITERAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, ASM_OPERAND, "<asm operand>");
    result_ = string_expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LPAREN);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // asm_operand (NLS COMMA NLS asm_operand)*
  static boolean asm_operand_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_operand_list")) return false;
    if (!nextTokenIs(builder_, "", STRING_INTERPOLATION_BEGIN, STRING_LITERAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = asm_operand(builder_, level_ + 1);
    result_ = result_ && asm_operand_list_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (NLS COMMA NLS asm_operand)*
  private static boolean asm_operand_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_operand_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!asm_operand_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "asm_operand_list_1", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS asm_operand
  private static boolean asm_operand_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_operand_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_operand(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // string_expression (NLS COMMA NLS string_expression)*
  static boolean asm_option_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_option_list")) return false;
    if (!nextTokenIs(builder_, "", STRING_INTERPOLATION_BEGIN, STRING_LITERAL)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = string_expression(builder_, level_ + 1);
    result_ = result_ && asm_option_list_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (NLS COMMA NLS string_expression)*
  private static boolean asm_option_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_option_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!asm_option_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "asm_option_list_1", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS string_expression
  private static boolean asm_option_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_option_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && string_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // DOUBLE_COLON NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]
  //                        | DOUBLE_COLON NLS [asm_operand_list] [NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]]
  //                        | COLON NLS [asm_operand_list] NLS DOUBLE_COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]
  //                        | COLON NLS [asm_operand_list] [NLS COLON NLS [asm_operand_list] [NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]]]
  static boolean asm_sections(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections")) return false;
    if (!nextTokenIs(builder_, "", COLON, DOUBLE_COLON)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = asm_sections_0(builder_, level_ + 1);
    if (!result_) result_ = asm_sections_1(builder_, level_ + 1);
    if (!result_) result_ = asm_sections_2(builder_, level_ + 1);
    if (!result_) result_ = asm_sections_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOUBLE_COLON NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]
  private static boolean asm_sections_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_0_4(builder_, level_ + 1);
    result_ = result_ && asm_sections_0_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_clobber_list]
  private static boolean asm_sections_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_0_4")) return false;
    asm_clobber_list(builder_, level_ + 1);
    return true;
  }

  // [NLS COLON NLS [asm_option_list]]
  private static boolean asm_sections_0_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_0_5")) return false;
    asm_sections_0_5_0(builder_, level_ + 1);
    return true;
  }

  // NLS COLON NLS [asm_option_list]
  private static boolean asm_sections_0_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_0_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_0_5_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_option_list]
  private static boolean asm_sections_0_5_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_0_5_0_3")) return false;
    asm_option_list(builder_, level_ + 1);
    return true;
  }

  // DOUBLE_COLON NLS [asm_operand_list] [NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]]
  private static boolean asm_sections_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOUBLE_COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_1_2(builder_, level_ + 1);
    result_ = result_ && asm_sections_1_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_operand_list]
  private static boolean asm_sections_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_1_2")) return false;
    asm_operand_list(builder_, level_ + 1);
    return true;
  }

  // [NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]]
  private static boolean asm_sections_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_1_3")) return false;
    asm_sections_1_3_0(builder_, level_ + 1);
    return true;
  }

  // NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]
  private static boolean asm_sections_1_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_1_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_1_3_0_3(builder_, level_ + 1);
    result_ = result_ && asm_sections_1_3_0_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_clobber_list]
  private static boolean asm_sections_1_3_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_1_3_0_3")) return false;
    asm_clobber_list(builder_, level_ + 1);
    return true;
  }

  // [NLS COLON NLS [asm_option_list]]
  private static boolean asm_sections_1_3_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_1_3_0_4")) return false;
    asm_sections_1_3_0_4_0(builder_, level_ + 1);
    return true;
  }

  // NLS COLON NLS [asm_option_list]
  private static boolean asm_sections_1_3_0_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_1_3_0_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_1_3_0_4_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_option_list]
  private static boolean asm_sections_1_3_0_4_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_1_3_0_4_0_3")) return false;
    asm_option_list(builder_, level_ + 1);
    return true;
  }

  // COLON NLS [asm_operand_list] NLS DOUBLE_COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]
  private static boolean asm_sections_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_2_2(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOUBLE_COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_2_6(builder_, level_ + 1);
    result_ = result_ && asm_sections_2_7(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_operand_list]
  private static boolean asm_sections_2_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_2_2")) return false;
    asm_operand_list(builder_, level_ + 1);
    return true;
  }

  // [asm_clobber_list]
  private static boolean asm_sections_2_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_2_6")) return false;
    asm_clobber_list(builder_, level_ + 1);
    return true;
  }

  // [NLS COLON NLS [asm_option_list]]
  private static boolean asm_sections_2_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_2_7")) return false;
    asm_sections_2_7_0(builder_, level_ + 1);
    return true;
  }

  // NLS COLON NLS [asm_option_list]
  private static boolean asm_sections_2_7_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_2_7_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_2_7_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_option_list]
  private static boolean asm_sections_2_7_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_2_7_0_3")) return false;
    asm_option_list(builder_, level_ + 1);
    return true;
  }

  // COLON NLS [asm_operand_list] [NLS COLON NLS [asm_operand_list] [NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]]]
  private static boolean asm_sections_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_3_2(builder_, level_ + 1);
    result_ = result_ && asm_sections_3_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_operand_list]
  private static boolean asm_sections_3_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_2")) return false;
    asm_operand_list(builder_, level_ + 1);
    return true;
  }

  // [NLS COLON NLS [asm_operand_list] [NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]]]
  private static boolean asm_sections_3_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3")) return false;
    asm_sections_3_3_0(builder_, level_ + 1);
    return true;
  }

  // NLS COLON NLS [asm_operand_list] [NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]]
  private static boolean asm_sections_3_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_3_3_0_3(builder_, level_ + 1);
    result_ = result_ && asm_sections_3_3_0_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_operand_list]
  private static boolean asm_sections_3_3_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3_0_3")) return false;
    asm_operand_list(builder_, level_ + 1);
    return true;
  }

  // [NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]]
  private static boolean asm_sections_3_3_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3_0_4")) return false;
    asm_sections_3_3_0_4_0(builder_, level_ + 1);
    return true;
  }

  // NLS COLON NLS [asm_clobber_list] [NLS COLON NLS [asm_option_list]]
  private static boolean asm_sections_3_3_0_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3_0_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_3_3_0_4_0_3(builder_, level_ + 1);
    result_ = result_ && asm_sections_3_3_0_4_0_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_clobber_list]
  private static boolean asm_sections_3_3_0_4_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3_0_4_0_3")) return false;
    asm_clobber_list(builder_, level_ + 1);
    return true;
  }

  // [NLS COLON NLS [asm_option_list]]
  private static boolean asm_sections_3_3_0_4_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3_0_4_0_4")) return false;
    asm_sections_3_3_0_4_0_4_0(builder_, level_ + 1);
    return true;
  }

  // NLS COLON NLS [asm_option_list]
  private static boolean asm_sections_3_3_0_4_0_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3_0_4_0_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && asm_sections_3_3_0_4_0_4_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [asm_option_list]
  private static boolean asm_sections_3_3_0_4_0_4_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "asm_sections_3_3_0_4_0_4_0_3")) return false;
    asm_option_list(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // ASSIGN | PLUS_ASSIGN | MINUS_ASSIGN | STAR_ASSIGN | SLASH_ASSIGN
  //                      | PERCENT_ASSIGN | AMPERSAND_ASSIGN | PIPE_ASSIGN | CARET_ASSIGN
  //                      | DOUBLE_STAR_ASSIGN | DOUBLE_SLASH_ASSIGN | LSHIFT_ASSIGN | RSHIFT_ASSIGN
  //                      | OR_OR_ASSIGN | AND_AND_ASSIGN
  //                      | WRAP_PLUS_ASSIGN | WRAP_MINUS_ASSIGN | WRAP_STAR_ASSIGN | WRAP_DOUBLE_STAR_ASSIGN
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
    if (!result_) result_ = consumeToken(builder_, DOUBLE_SLASH_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, LSHIFT_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, RSHIFT_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, OR_OR_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, AND_AND_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, WRAP_PLUS_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, WRAP_MINUS_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, WRAP_STAR_ASSIGN);
    if (!result_) result_ = consumeToken(builder_, WRAP_DOUBLE_STAR_ASSIGN);
    return result_;
  }

  /* ********************************************************** */
  // variable assign_op NLS (assignment | expression) [postfix_modifier]
  public static boolean assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignment")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, ASSIGNMENT, "<assignment>");
    result_ = variable(builder_, level_ + 1);
    result_ = result_ && assign_op(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, NLS(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, assignment_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && assignment_4(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // assignment | expression
  private static boolean assignment_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignment_3")) return false;
    boolean result_;
    result_ = assignment(builder_, level_ + 1);
    if (!result_) result_ = expression(builder_, level_ + 1);
    return result_;
  }

  // [postfix_modifier]
  private static boolean assignment_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "assignment_4")) return false;
    postfix_modifier(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // bare_multiplicative_expression ((PLUS | MINUS | WRAP_PLUS | WRAP_MINUS) NLS bare_multiplicative_expression)*
  static boolean bare_additive_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_additive_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_multiplicative_expression(builder_, level_ + 1);
    result_ = result_ && bare_additive_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((PLUS | MINUS | WRAP_PLUS | WRAP_MINUS) NLS bare_multiplicative_expression)*
  private static boolean bare_additive_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_additive_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_additive_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_additive_expression_1", pos_)) break;
    }
    return true;
  }

  // (PLUS | MINUS | WRAP_PLUS | WRAP_MINUS) NLS bare_multiplicative_expression
  private static boolean bare_additive_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_additive_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_additive_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && bare_multiplicative_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PLUS | MINUS | WRAP_PLUS | WRAP_MINUS
  private static boolean bare_additive_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_additive_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, WRAP_PLUS);
    if (!result_) result_ = consumeToken(builder_, WRAP_MINUS);
    return result_;
  }

  /* ********************************************************** */
  // bare_shift_expression (AMPERSAND NLS bare_shift_expression)*
  static boolean bare_and_bitwise_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_bitwise_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_shift_expression(builder_, level_ + 1);
    result_ = result_ && bare_and_bitwise_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AMPERSAND NLS bare_shift_expression)*
  private static boolean bare_and_bitwise_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_bitwise_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_and_bitwise_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_and_bitwise_expression_1", pos_)) break;
    }
    return true;
  }

  // AMPERSAND NLS bare_shift_expression
  private static boolean bare_and_bitwise_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_bitwise_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AMPERSAND);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && bare_shift_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_not_expression (AND_AND NLS bare_not_expression)*
  static boolean bare_and_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_not_expression(builder_, level_ + 1);
    result_ = result_ && bare_and_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (AND_AND NLS bare_not_expression)*
  private static boolean bare_and_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_and_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_and_expression_1", pos_)) break;
    }
    return true;
  }

  // AND_AND NLS bare_not_expression
  private static boolean bare_and_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_and_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AND_AND);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && bare_not_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // [IDENTIFIER COLON] bare_expression
  //                 | STAR bare_expression
  //                 | DOUBLE_STAR bare_expression
  //                 | OUT IDENTIFIER
  public static boolean bare_argument(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_argument")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, BARE_ARGUMENT, "<bare argument>");
    result_ = bare_argument_0(builder_, level_ + 1);
    if (!result_) result_ = bare_argument_1(builder_, level_ + 1);
    if (!result_) result_ = bare_argument_2(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, OUT, IDENTIFIER);
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
  // bare_range_expression ((EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ | MATCH_OP) NLS bare_range_expression)*
  static boolean bare_comparison_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_comparison_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_range_expression(builder_, level_ + 1);
    result_ = result_ && bare_comparison_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ | MATCH_OP) NLS bare_range_expression)*
  private static boolean bare_comparison_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_comparison_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_comparison_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_comparison_expression_1", pos_)) break;
    }
    return true;
  }

  // (EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ | MATCH_OP) NLS bare_range_expression
  private static boolean bare_comparison_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_comparison_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_comparison_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && bare_range_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ | MATCH_OP
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
    if (!result_) result_ = consumeToken(builder_, MATCH_OP);
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
  // bare_power_expression ((STAR | SLASH | DOUBLE_SLASH | PERCENT | WRAP_STAR) NLS bare_power_expression)*
  static boolean bare_multiplicative_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_multiplicative_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_power_expression(builder_, level_ + 1);
    result_ = result_ && bare_multiplicative_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((STAR | SLASH | DOUBLE_SLASH | PERCENT | WRAP_STAR) NLS bare_power_expression)*
  private static boolean bare_multiplicative_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_multiplicative_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_multiplicative_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_multiplicative_expression_1", pos_)) break;
    }
    return true;
  }

  // (STAR | SLASH | DOUBLE_SLASH | PERCENT | WRAP_STAR) NLS bare_power_expression
  private static boolean bare_multiplicative_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_multiplicative_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_multiplicative_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && bare_power_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STAR | SLASH | DOUBLE_SLASH | PERCENT | WRAP_STAR
  private static boolean bare_multiplicative_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_multiplicative_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_SLASH);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    if (!result_) result_ = consumeToken(builder_, WRAP_STAR);
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
  // bare_xor_expression (PIPE NLS bare_xor_expression)*
  static boolean bare_or_bitwise_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_bitwise_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_xor_expression(builder_, level_ + 1);
    result_ = result_ && bare_or_bitwise_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (PIPE NLS bare_xor_expression)*
  private static boolean bare_or_bitwise_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_bitwise_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_or_bitwise_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_or_bitwise_expression_1", pos_)) break;
    }
    return true;
  }

  // PIPE NLS bare_xor_expression
  private static boolean bare_or_bitwise_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_bitwise_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && bare_xor_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_and_expression (OR_OR NLS bare_and_expression)*
  static boolean bare_or_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_and_expression(builder_, level_ + 1);
    result_ = result_ && bare_or_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (OR_OR NLS bare_and_expression)*
  private static boolean bare_or_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_or_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_or_expression_1", pos_)) break;
    }
    return true;
  }

  // OR_OR NLS bare_and_expression
  private static boolean bare_or_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_or_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OR_OR);
    result_ = result_ && NLS(builder_, level_ + 1);
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
  // NLS DOT (AS | AS_QUESTION | IS_A) LPAREN type_reference RPAREN
  //                           | NLS DOT (IDENTIFIER | CONSTANT | keyword_as_method) [call_args]
  //                           | DOUBLE_COLON CONSTANT
  //                           | LBRACKET argument_list RBRACKET
  //                           | call_args
  static boolean bare_postfix_op(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_postfix_op_0(builder_, level_ + 1);
    if (!result_) result_ = bare_postfix_op_1(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, DOUBLE_COLON, CONSTANT);
    if (!result_) result_ = bare_postfix_op_3(builder_, level_ + 1);
    if (!result_) result_ = call_args(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NLS DOT (AS | AS_QUESTION | IS_A) LPAREN type_reference RPAREN
  private static boolean bare_postfix_op_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOT);
    result_ = result_ && bare_postfix_op_0_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LPAREN);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AS | AS_QUESTION | IS_A
  private static boolean bare_postfix_op_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_0_2")) return false;
    boolean result_;
    result_ = consumeToken(builder_, AS);
    if (!result_) result_ = consumeToken(builder_, AS_QUESTION);
    if (!result_) result_ = consumeToken(builder_, IS_A);
    return result_;
  }

  // NLS DOT (IDENTIFIER | CONSTANT | keyword_as_method) [call_args]
  private static boolean bare_postfix_op_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOT);
    result_ = result_ && bare_postfix_op_1_2(builder_, level_ + 1);
    result_ = result_ && bare_postfix_op_1_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT | keyword_as_method
  private static boolean bare_postfix_op_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_1_2")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    if (!result_) result_ = keyword_as_method(builder_, level_ + 1);
    return result_;
  }

  // [call_args]
  private static boolean bare_postfix_op_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_1_3")) return false;
    call_args(builder_, level_ + 1);
    return true;
  }

  // LBRACKET argument_list RBRACKET
  private static boolean bare_postfix_op_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_postfix_op_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_unary_expression ((DOUBLE_STAR | WRAP_DOUBLE_STAR) NLS bare_unary_expression)*
  static boolean bare_power_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_power_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_unary_expression(builder_, level_ + 1);
    result_ = result_ && bare_power_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((DOUBLE_STAR | WRAP_DOUBLE_STAR) NLS bare_unary_expression)*
  private static boolean bare_power_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_power_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_power_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_power_expression_1", pos_)) break;
    }
    return true;
  }

  // (DOUBLE_STAR | WRAP_DOUBLE_STAR) NLS bare_unary_expression
  private static boolean bare_power_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_power_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_power_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && bare_unary_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOUBLE_STAR | WRAP_DOUBLE_STAR
  private static boolean bare_power_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_power_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, WRAP_DOUBLE_STAR);
    return result_;
  }

  /* ********************************************************** */
  // grouped_expression
  //                                   | array_literal
  //                                   | hash_literal
  //                                   | tuple_literal
  //                                   | proc_literal
  //                                   | bare_method_call_expression
  //                                   | literal
  //                                   | instance_var_access
  //                                   | class_var_access
  //                                   | variable_reference
  //                                   | typeof_expression
  //                                   | sizeof_expression
  //                                   | instance_sizeof_expression
  //                                   | pointerof_expression
  //                                   | offsetof_expression
  //                                   | uninitialized_expression
  //                                   | asm_expression
  static boolean bare_primary_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_primary_expression")) return false;
    boolean result_;
    result_ = grouped_expression(builder_, level_ + 1);
    if (!result_) result_ = array_literal(builder_, level_ + 1);
    if (!result_) result_ = hash_literal(builder_, level_ + 1);
    if (!result_) result_ = tuple_literal(builder_, level_ + 1);
    if (!result_) result_ = proc_literal(builder_, level_ + 1);
    if (!result_) result_ = bare_method_call_expression(builder_, level_ + 1);
    if (!result_) result_ = literal(builder_, level_ + 1);
    if (!result_) result_ = instance_var_access(builder_, level_ + 1);
    if (!result_) result_ = class_var_access(builder_, level_ + 1);
    if (!result_) result_ = variable_reference(builder_, level_ + 1);
    if (!result_) result_ = typeof_expression(builder_, level_ + 1);
    if (!result_) result_ = sizeof_expression(builder_, level_ + 1);
    if (!result_) result_ = instance_sizeof_expression(builder_, level_ + 1);
    if (!result_) result_ = pointerof_expression(builder_, level_ + 1);
    if (!result_) result_ = offsetof_expression(builder_, level_ + 1);
    if (!result_) result_ = uninitialized_expression(builder_, level_ + 1);
    if (!result_) result_ = asm_expression(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // bare_or_bitwise_expression [(DOTDOT | DOTDOTDOT) NLS [bare_or_bitwise_expression]]
  static boolean bare_range_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_range_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_or_bitwise_expression(builder_, level_ + 1);
    result_ = result_ && bare_range_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [(DOTDOT | DOTDOTDOT) NLS [bare_or_bitwise_expression]]
  private static boolean bare_range_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_range_expression_1")) return false;
    bare_range_expression_1_0(builder_, level_ + 1);
    return true;
  }

  // (DOTDOT | DOTDOTDOT) NLS [bare_or_bitwise_expression]
  private static boolean bare_range_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_range_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_range_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && bare_range_expression_1_0_2(builder_, level_ + 1);
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

  // [bare_or_bitwise_expression]
  private static boolean bare_range_expression_1_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_range_expression_1_0_2")) return false;
    bare_or_bitwise_expression(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // bare_additive_expression ((LSHIFT | RSHIFT) NLS bare_additive_expression)*
  static boolean bare_shift_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_shift_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_additive_expression(builder_, level_ + 1);
    result_ = result_ && bare_shift_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((LSHIFT | RSHIFT) NLS bare_additive_expression)*
  private static boolean bare_shift_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_shift_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_shift_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_shift_expression_1", pos_)) break;
    }
    return true;
  }

  // (LSHIFT | RSHIFT) NLS bare_additive_expression
  private static boolean bare_shift_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_shift_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_shift_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
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
  // STAR &(COMMA | RPAREN | NEWLINE)
  static boolean bare_splat_separator(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_splat_separator")) return false;
    if (!nextTokenIs(builder_, STAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STAR);
    result_ = result_ && bare_splat_separator_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // &(COMMA | RPAREN | NEWLINE)
  private static boolean bare_splat_separator_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_splat_separator_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _AND_);
    result_ = bare_splat_separator_1_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // COMMA | RPAREN | NEWLINE
  private static boolean bare_splat_separator_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_splat_separator_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, RPAREN);
    if (!result_) result_ = consumeToken(builder_, NEWLINE);
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
  // bare_and_bitwise_expression (CARET NLS bare_and_bitwise_expression)*
  static boolean bare_xor_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_xor_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bare_and_bitwise_expression(builder_, level_ + 1);
    result_ = result_ && bare_xor_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (CARET NLS bare_and_bitwise_expression)*
  private static boolean bare_xor_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_xor_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!bare_xor_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bare_xor_expression_1", pos_)) break;
    }
    return true;
  }

  // CARET NLS bare_and_bitwise_expression
  private static boolean bare_xor_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bare_xor_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CARET);
    result_ = result_ && NLS(builder_, level_ + 1);
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
  // DO [PIPE NLS parameter_list NLS PIPE] statement_list rescue_clause* [else_clause] [ensure_clause] END
  //         | LBRACE [PIPE NLS parameter_list NLS PIPE] statement_list RBRACE
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

  // DO [PIPE NLS parameter_list NLS PIPE] statement_list rescue_clause* [else_clause] [ensure_clause] END
  private static boolean block_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DO);
    result_ = result_ && block_0_1(builder_, level_ + 1);
    result_ = result_ && statement_list(builder_, level_ + 1);
    result_ = result_ && block_0_3(builder_, level_ + 1);
    result_ = result_ && block_0_4(builder_, level_ + 1);
    result_ = result_ && block_0_5(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [PIPE NLS parameter_list NLS PIPE]
  private static boolean block_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0_1")) return false;
    block_0_1_0(builder_, level_ + 1);
    return true;
  }

  // PIPE NLS parameter_list NLS PIPE
  private static boolean block_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, PIPE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // rescue_clause*
  private static boolean block_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!rescue_clause(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "block_0_3", pos_)) break;
    }
    return true;
  }

  // [else_clause]
  private static boolean block_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0_4")) return false;
    else_clause(builder_, level_ + 1);
    return true;
  }

  // [ensure_clause]
  private static boolean block_0_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_0_5")) return false;
    ensure_clause(builder_, level_ + 1);
    return true;
  }

  // LBRACE [PIPE NLS parameter_list NLS PIPE] statement_list RBRACE
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

  // [PIPE NLS parameter_list NLS PIPE]
  private static boolean block_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_1_1")) return false;
    block_1_1_0(builder_, level_ + 1);
    return true;
  }

  // PIPE NLS parameter_list NLS PIPE
  private static boolean block_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "block_1_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, PIPE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // BREAK postfix_modifier
  //                   | BREAK expression [postfix_modifier]
  //                   | BREAK
  public static boolean break_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "break_statement")) return false;
    if (!nextTokenIs(builder_, BREAK)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = break_statement_0(builder_, level_ + 1);
    if (!result_) result_ = break_statement_1(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, BREAK);
    exit_section_(builder_, marker_, BREAK_STATEMENT, result_);
    return result_;
  }

  // BREAK postfix_modifier
  private static boolean break_statement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "break_statement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BREAK);
    result_ = result_ && postfix_modifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // BREAK expression [postfix_modifier]
  private static boolean break_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "break_statement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, BREAK);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && break_statement_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [postfix_modifier]
  private static boolean break_statement_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "break_statement_1_2")) return false;
    postfix_modifier(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // LPAREN NLS argument_list NLS RPAREN
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

  // LPAREN NLS argument_list NLS RPAREN
  private static boolean call_args_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "call_args_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CASE [expression] NEWLINE* (when_clause | in_clause)+ [else_clause] END
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

  // (when_clause | in_clause)+
  private static boolean case_statement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "case_statement_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = case_statement_3_0(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!case_statement_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "case_statement_3", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // when_clause | in_clause
  private static boolean case_statement_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "case_statement_3_0")) return false;
    boolean result_;
    result_ = when_clause(builder_, level_ + 1);
    if (!result_) result_ = in_clause(builder_, level_ + 1);
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
  // range_expression ((EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ | MATCH_OP) NLS range_expression)*
  static boolean comparison_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparison_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = range_expression(builder_, level_ + 1);
    result_ = result_ && comparison_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ | MATCH_OP) NLS range_expression)*
  private static boolean comparison_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparison_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!comparison_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "comparison_expression_1", pos_)) break;
    }
    return true;
  }

  // (EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ | MATCH_OP) NLS range_expression
  private static boolean comparison_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "comparison_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = comparison_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && range_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP | CASE_EQ | MATCH_OP
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
    if (!result_) result_ = consumeToken(builder_, MATCH_OP);
    return result_;
  }

  /* ********************************************************** */
  // condition_assignment | expression
  public static boolean condition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "condition")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONDITION, "<condition>");
    result_ = condition_assignment(builder_, level_ + 1);
    if (!result_) result_ = expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // variable ASSIGN NLS expression
  static boolean condition_assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "condition_assignment")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = variable(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ASSIGN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // CONSTANT ASSIGN NLS expression [postfix_modifier]
  public static boolean constant_assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constant_assignment")) return false;
    if (!nextTokenIs(builder_, CONSTANT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, CONSTANT_ASSIGNMENT, null);
    result_ = consumeTokens(builder_, 2, CONSTANT, ASSIGN);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, NLS(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, expression(builder_, level_ + 1)) && result_;
    result_ = pinned_ && constant_assignment_4(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [postfix_modifier]
  private static boolean constant_assignment_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "constant_assignment_4")) return false;
    postfix_modifier(builder_, level_ + 1);
    return true;
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
  // ELSIF condition then_clause statement_list
  public static boolean elsif_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "elsif_clause")) return false;
    if (!nextTokenIs(builder_, ELSIF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ELSIF);
    result_ = result_ && condition(builder_, level_ + 1);
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
  // assign_op NLS expression
  static boolean expression_assign_suffix(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_assign_suffix")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = assign_op(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // expression (NLS COMMA NLS expression)* [COMMA]
  public static boolean expression_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPRESSION_LIST, "<expression list>");
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && expression_list_1(builder_, level_ + 1);
    result_ = result_ && expression_list_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (NLS COMMA NLS expression)*
  private static boolean expression_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!expression_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "expression_list_1", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS expression
  private static boolean expression_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COMMA]
  private static boolean expression_list_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_list_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // expression [expression_assign_suffix | postfix_modifier]
  public static boolean expression_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_statement")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXPRESSION_STATEMENT, "<expression statement>");
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && expression_statement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // [expression_assign_suffix | postfix_modifier]
  private static boolean expression_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_statement_1")) return false;
    expression_statement_1_0(builder_, level_ + 1);
    return true;
  }

  // expression_assign_suffix | postfix_modifier
  private static boolean expression_statement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "expression_statement_1_0")) return false;
    boolean result_;
    result_ = expression_assign_suffix(builder_, level_ + 1);
    if (!result_) result_ = postfix_modifier(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // EXTEND (type_reference | SELF)
  public static boolean extend_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extend_statement")) return false;
    if (!nextTokenIs(builder_, EXTEND)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, EXTEND_STATEMENT, null);
    result_ = consumeToken(builder_, EXTEND);
    pinned_ = result_; // pin = 1
    result_ = result_ && extend_statement_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // type_reference | SELF
  private static boolean extend_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "extend_statement_1")) return false;
    boolean result_;
    result_ = type_reference(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, SELF);
    return result_;
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
  // FUN IDENTIFIER [LPAREN NLS parameter_list NLS RPAREN] [COLON type_reference]
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

  // [LPAREN NLS parameter_list NLS RPAREN]
  private static boolean fun_definition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fun_definition_2")) return false;
    fun_definition_2_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN NLS parameter_list NLS RPAREN
  private static boolean fun_definition_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "fun_definition_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
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
  // LPAREN NLS expression [assign_op NLS expression] NLS RPAREN
  public static boolean grouped_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "grouped_expression")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && grouped_expression_3(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, GROUPED_EXPRESSION, result_);
    return result_;
  }

  // [assign_op NLS expression]
  private static boolean grouped_expression_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "grouped_expression_3")) return false;
    grouped_expression_3_0(builder_, level_ + 1);
    return true;
  }

  // assign_op NLS expression
  private static boolean grouped_expression_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "grouped_expression_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = assign_op(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
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
  // hash_entry (NLS COMMA NLS hash_entry)* [COMMA]
  public static boolean hash_entry_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, HASH_ENTRY_LIST, "<hash entry list>");
    result_ = hash_entry(builder_, level_ + 1);
    result_ = result_ && hash_entry_list_1(builder_, level_ + 1);
    result_ = result_ && hash_entry_list_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (NLS COMMA NLS hash_entry)*
  private static boolean hash_entry_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!hash_entry_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "hash_entry_list_1", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS hash_entry
  private static boolean hash_entry_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && hash_entry(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COMMA]
  private static boolean hash_entry_list_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_entry_list_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // LBRACE NLS [hash_entry_list] NLS RBRACE [OF type_reference DOUBLE_ARROW type_reference]
  public static boolean hash_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_literal")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && hash_literal_2(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    result_ = result_ && hash_literal_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, HASH_LITERAL, result_);
    return result_;
  }

  // [hash_entry_list]
  private static boolean hash_literal_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_literal_2")) return false;
    hash_entry_list(builder_, level_ + 1);
    return true;
  }

  // [OF type_reference DOUBLE_ARROW type_reference]
  private static boolean hash_literal_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_literal_5")) return false;
    hash_literal_5_0(builder_, level_ + 1);
    return true;
  }

  // OF type_reference DOUBLE_ARROW type_reference
  private static boolean hash_literal_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "hash_literal_5_0")) return false;
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
  // HEREDOC_START (HEREDOC_CONTENT | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END)* HEREDOC_END
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

  // (HEREDOC_CONTENT | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END)*
  private static boolean heredoc_literal_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "heredoc_literal_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!heredoc_literal_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "heredoc_literal_1", pos_)) break;
    }
    return true;
  }

  // HEREDOC_CONTENT | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END
  private static boolean heredoc_literal_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "heredoc_literal_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, HEREDOC_CONTENT);
    if (!result_) result_ = heredoc_literal_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END
  private static boolean heredoc_literal_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "heredoc_literal_1_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING_INTERPOLATION_BEGIN);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, STRING_INTERPOLATION_END);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // IF condition then_clause statement_list elsif_clause* [else_clause] END
  public static boolean if_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "if_statement")) return false;
    if (!nextTokenIs(builder_, IF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, IF_STATEMENT, null);
    result_ = consumeToken(builder_, IF);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, condition(builder_, level_ + 1));
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
  // DOT (IDENTIFIER | RESPONDS_TO | IS_A | NIL_QUESTION | operator_method_name) [call_args]
  public static boolean implicit_object_call(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "implicit_object_call")) return false;
    if (!nextTokenIs(builder_, DOT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && implicit_object_call_1(builder_, level_ + 1);
    result_ = result_ && implicit_object_call_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, IMPLICIT_OBJECT_CALL, result_);
    return result_;
  }

  // IDENTIFIER | RESPONDS_TO | IS_A | NIL_QUESTION | operator_method_name
  private static boolean implicit_object_call_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "implicit_object_call_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, RESPONDS_TO);
    if (!result_) result_ = consumeToken(builder_, IS_A);
    if (!result_) result_ = consumeToken(builder_, NIL_QUESTION);
    if (!result_) result_ = operator_method_name(builder_, level_ + 1);
    return result_;
  }

  // [call_args]
  private static boolean implicit_object_call_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "implicit_object_call_2")) return false;
    call_args(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // IN expression_list [IF expression] then_clause statement_list
  public static boolean in_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "in_clause")) return false;
    if (!nextTokenIs(builder_, IN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IN);
    result_ = result_ && expression_list(builder_, level_ + 1);
    result_ = result_ && in_clause_2(builder_, level_ + 1);
    result_ = result_ && then_clause(builder_, level_ + 1);
    result_ = result_ && statement_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, IN_CLAUSE, result_);
    return result_;
  }

  // [IF expression]
  private static boolean in_clause_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "in_clause_2")) return false;
    in_clause_2_0(builder_, level_ + 1);
    return true;
  }

  // IF expression
  private static boolean in_clause_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "in_clause_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, IF);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
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
  // INSTANCE_SIZEOF LPAREN NLS type_reference NLS RPAREN
  public static boolean instance_sizeof_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "instance_sizeof_expression")) return false;
    if (!nextTokenIs(builder_, INSTANCE_SIZEOF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, INSTANCE_SIZEOF_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, INSTANCE_SIZEOF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, NLS(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, type_reference(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
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
  // ABSTRACT | ALIAS | AS | AS_QUESTION | ASM
  //                             | BEGIN | BREAK
  //                             | CASE | CLASS
  //                             | DEF | DO
  //                             | ELSE | ELSIF | END | ENSURE | ENUM | EXTEND
  //                             | FALSE | FOR | FORALL | FUN
  //                             | IF | IN | INCLUDE | INSTANCE_SIZEOF | IS_A
  //                             | LIB
  //                             | MACRO | MODULE
  //                             | NEXT | NIL | NIL_QUESTION
  //                             | OF | OFFSETOF | OUT
  //                             | POINTEROF | PREVIOUS_DEF | PRIVATE | PROTECTED
  //                             | REQUIRE | RESCUE | RESPONDS_TO | RETURN
  //                             | SELECT | SELF | SIZEOF | STRUCT | SUPER
  //                             | THEN | TRUE | TYPEOF
  //                             | UNINITIALIZED | UNION | UNLESS | UNTIL
  //                             | VERBATIM
  //                             | WHEN | WHILE | WITH
  //                             | YIELD
  //                             | PLUS | MINUS | STAR | SLASH | PERCENT | DOUBLE_STAR
  //                             | EQ | NEQ | LT | GT | LTE | GTE | SPACESHIP
  //                             | LSHIFT | RSHIFT | AMPERSAND | PIPE | CARET | TILDE
  //                             | DOUBLE_SLASH
  static boolean keyword_as_method(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "keyword_as_method")) return false;
    boolean result_;
    result_ = consumeToken(builder_, ABSTRACT);
    if (!result_) result_ = consumeToken(builder_, ALIAS);
    if (!result_) result_ = consumeToken(builder_, AS);
    if (!result_) result_ = consumeToken(builder_, AS_QUESTION);
    if (!result_) result_ = consumeToken(builder_, ASM);
    if (!result_) result_ = consumeToken(builder_, BEGIN);
    if (!result_) result_ = consumeToken(builder_, BREAK);
    if (!result_) result_ = consumeToken(builder_, CASE);
    if (!result_) result_ = consumeToken(builder_, CLASS);
    if (!result_) result_ = consumeToken(builder_, DEF);
    if (!result_) result_ = consumeToken(builder_, DO);
    if (!result_) result_ = consumeToken(builder_, ELSE);
    if (!result_) result_ = consumeToken(builder_, ELSIF);
    if (!result_) result_ = consumeToken(builder_, END);
    if (!result_) result_ = consumeToken(builder_, ENSURE);
    if (!result_) result_ = consumeToken(builder_, ENUM);
    if (!result_) result_ = consumeToken(builder_, EXTEND);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    if (!result_) result_ = consumeToken(builder_, FOR);
    if (!result_) result_ = consumeToken(builder_, FORALL);
    if (!result_) result_ = consumeToken(builder_, FUN);
    if (!result_) result_ = consumeToken(builder_, IF);
    if (!result_) result_ = consumeToken(builder_, IN);
    if (!result_) result_ = consumeToken(builder_, INCLUDE);
    if (!result_) result_ = consumeToken(builder_, INSTANCE_SIZEOF);
    if (!result_) result_ = consumeToken(builder_, IS_A);
    if (!result_) result_ = consumeToken(builder_, LIB);
    if (!result_) result_ = consumeToken(builder_, MACRO);
    if (!result_) result_ = consumeToken(builder_, MODULE);
    if (!result_) result_ = consumeToken(builder_, NEXT);
    if (!result_) result_ = consumeToken(builder_, NIL);
    if (!result_) result_ = consumeToken(builder_, NIL_QUESTION);
    if (!result_) result_ = consumeToken(builder_, OF);
    if (!result_) result_ = consumeToken(builder_, OFFSETOF);
    if (!result_) result_ = consumeToken(builder_, OUT);
    if (!result_) result_ = consumeToken(builder_, POINTEROF);
    if (!result_) result_ = consumeToken(builder_, PREVIOUS_DEF);
    if (!result_) result_ = consumeToken(builder_, PRIVATE);
    if (!result_) result_ = consumeToken(builder_, PROTECTED);
    if (!result_) result_ = consumeToken(builder_, REQUIRE);
    if (!result_) result_ = consumeToken(builder_, RESCUE);
    if (!result_) result_ = consumeToken(builder_, RESPONDS_TO);
    if (!result_) result_ = consumeToken(builder_, RETURN);
    if (!result_) result_ = consumeToken(builder_, SELECT);
    if (!result_) result_ = consumeToken(builder_, SELF);
    if (!result_) result_ = consumeToken(builder_, SIZEOF);
    if (!result_) result_ = consumeToken(builder_, STRUCT);
    if (!result_) result_ = consumeToken(builder_, SUPER);
    if (!result_) result_ = consumeToken(builder_, THEN);
    if (!result_) result_ = consumeToken(builder_, TRUE);
    if (!result_) result_ = consumeToken(builder_, TYPEOF);
    if (!result_) result_ = consumeToken(builder_, UNINITIALIZED);
    if (!result_) result_ = consumeToken(builder_, UNION);
    if (!result_) result_ = consumeToken(builder_, UNLESS);
    if (!result_) result_ = consumeToken(builder_, UNTIL);
    if (!result_) result_ = consumeToken(builder_, VERBATIM);
    if (!result_) result_ = consumeToken(builder_, WHEN);
    if (!result_) result_ = consumeToken(builder_, WHILE);
    if (!result_) result_ = consumeToken(builder_, WITH);
    if (!result_) result_ = consumeToken(builder_, YIELD);
    if (!result_) result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, LTE);
    if (!result_) result_ = consumeToken(builder_, GTE);
    if (!result_) result_ = consumeToken(builder_, SPACESHIP);
    if (!result_) result_ = consumeToken(builder_, LSHIFT);
    if (!result_) result_ = consumeToken(builder_, RSHIFT);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    if (!result_) result_ = consumeToken(builder_, PIPE);
    if (!result_) result_ = consumeToken(builder_, CARET);
    if (!result_) result_ = consumeToken(builder_, TILDE);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_SLASH);
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
  // GLOBAL_VAR [ASSIGN string_expression] COLON type_reference
  public static boolean lib_external_var(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_external_var")) return false;
    if (!nextTokenIs(builder_, GLOBAL_VAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, GLOBAL_VAR);
    result_ = result_ && lib_external_var_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, LIB_EXTERNAL_VAR, result_);
    return result_;
  }

  // [ASSIGN string_expression]
  private static boolean lib_external_var_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_external_var_1")) return false;
    lib_external_var_1_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN string_expression
  private static boolean lib_external_var_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_external_var_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && string_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER COLON type_reference
  public static boolean lib_field(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_field")) return false;
    if (!nextTokenIs(builder_, IDENTIFIER)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, IDENTIFIER, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, LIB_FIELD, result_);
    return result_;
  }

  /* ********************************************************** */
  // NEWLINE | SEMICOLON | fun_definition | type_alias_lib | lib_struct_definition | lib_union_definition | enum_definition | lib_external_var | lib_field
  static boolean lib_member(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_member")) return false;
    boolean result_;
    result_ = consumeToken(builder_, NEWLINE);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = fun_definition(builder_, level_ + 1);
    if (!result_) result_ = type_alias_lib(builder_, level_ + 1);
    if (!result_) result_ = lib_struct_definition(builder_, level_ + 1);
    if (!result_) result_ = lib_union_definition(builder_, level_ + 1);
    if (!result_) result_ = enum_definition(builder_, level_ + 1);
    if (!result_) result_ = lib_external_var(builder_, level_ + 1);
    if (!result_) result_ = lib_field(builder_, level_ + 1);
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
  // UNION CONSTANT lib_body END
  public static boolean lib_union_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "lib_union_definition")) return false;
    if (!nextTokenIs(builder_, UNION)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, UNION, CONSTANT);
    result_ = result_ && lib_body(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, LIB_UNION_DEFINITION, result_);
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
  //                   | PREVIOUS_DEF
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
    if (!result_) result_ = consumeToken(builder_, PREVIOUS_DEF);
    if (!result_) result_ = heredoc_literal(builder_, level_ + 1);
    if (!result_) result_ = percent_literal(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // macro_body_element*
  public static boolean macro_body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_body")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MACRO_BODY, "<macro body>");
    while (true) {
      int pos_ = current_position_(builder_);
      if (!macro_body_element(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "macro_body", pos_)) break;
    }
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // MACRO_BODY_CONTENT
  //                              | MACRO_FRESH_VAR
  //                              | macro_interpolation
  //                              | macro_control
  //                              | NEWLINE
  static boolean macro_body_element(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_body_element")) return false;
    boolean result_;
    result_ = consumeToken(builder_, MACRO_BODY_CONTENT);
    if (!result_) result_ = consumeToken(builder_, MACRO_FRESH_VAR);
    if (!result_) result_ = macro_interpolation(builder_, level_ + 1);
    if (!result_) result_ = macro_control(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEWLINE);
    return result_;
  }

  /* ********************************************************** */
  // MACRO_CONTROL_BEGIN macro_control_token* MACRO_CONTROL_END
  public static boolean macro_control(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_control")) return false;
    if (!nextTokenIs(builder_, MACRO_CONTROL_BEGIN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, MACRO_CONTROL_BEGIN);
    result_ = result_ && macro_control_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MACRO_CONTROL_END);
    exit_section_(builder_, marker_, MACRO_CONTROL, result_);
    return result_;
  }

  // macro_control_token*
  private static boolean macro_control_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_control_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!macro_control_token(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "macro_control_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // IDENTIFIER | CONSTANT | INSTANCE_VAR | CLASS_VAR
  //     | INTEGER_LITERAL | STRING_LITERAL | STRING_INTERPOLATION_BEGIN | STRING_INTERPOLATION_END
  //     | TRUE | FALSE | NIL
  //     | IF | ELSE | ELSIF | END | FOR | IN | UNLESS | BEGIN | YIELD | VERBATIM
  //     | LPAREN | RPAREN | LBRACKET | RBRACKET | LBRACE | RBRACE | COMMA | DOT | COLON
  //     | EQ | NEQ | LT | GT | LTE | GTE | OR_OR | AND_AND | PIPE | AMPERSAND
  //     | ASSIGN | PLUS | MINUS | STAR | SLASH | QUESTION | BANG | DOTDOT | DOTDOTDOT
  //     | DOUBLE_COLON | PERCENT
  //     | NEWLINE | SEMICOLON | HASH | AT | ARROW | ANNOTATION
  static boolean macro_control_token(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_control_token")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    if (!result_) result_ = consumeToken(builder_, INSTANCE_VAR);
    if (!result_) result_ = consumeToken(builder_, CLASS_VAR);
    if (!result_) result_ = consumeToken(builder_, INTEGER_LITERAL);
    if (!result_) result_ = consumeToken(builder_, STRING_LITERAL);
    if (!result_) result_ = consumeToken(builder_, STRING_INTERPOLATION_BEGIN);
    if (!result_) result_ = consumeToken(builder_, STRING_INTERPOLATION_END);
    if (!result_) result_ = consumeToken(builder_, TRUE);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    if (!result_) result_ = consumeToken(builder_, NIL);
    if (!result_) result_ = consumeToken(builder_, IF);
    if (!result_) result_ = consumeToken(builder_, ELSE);
    if (!result_) result_ = consumeToken(builder_, ELSIF);
    if (!result_) result_ = consumeToken(builder_, END);
    if (!result_) result_ = consumeToken(builder_, FOR);
    if (!result_) result_ = consumeToken(builder_, IN);
    if (!result_) result_ = consumeToken(builder_, UNLESS);
    if (!result_) result_ = consumeToken(builder_, BEGIN);
    if (!result_) result_ = consumeToken(builder_, YIELD);
    if (!result_) result_ = consumeToken(builder_, VERBATIM);
    if (!result_) result_ = consumeToken(builder_, LPAREN);
    if (!result_) result_ = consumeToken(builder_, RPAREN);
    if (!result_) result_ = consumeToken(builder_, LBRACKET);
    if (!result_) result_ = consumeToken(builder_, RBRACKET);
    if (!result_) result_ = consumeToken(builder_, LBRACE);
    if (!result_) result_ = consumeToken(builder_, RBRACE);
    if (!result_) result_ = consumeToken(builder_, COMMA);
    if (!result_) result_ = consumeToken(builder_, DOT);
    if (!result_) result_ = consumeToken(builder_, COLON);
    if (!result_) result_ = consumeToken(builder_, EQ);
    if (!result_) result_ = consumeToken(builder_, NEQ);
    if (!result_) result_ = consumeToken(builder_, LT);
    if (!result_) result_ = consumeToken(builder_, GT);
    if (!result_) result_ = consumeToken(builder_, LTE);
    if (!result_) result_ = consumeToken(builder_, GTE);
    if (!result_) result_ = consumeToken(builder_, OR_OR);
    if (!result_) result_ = consumeToken(builder_, AND_AND);
    if (!result_) result_ = consumeToken(builder_, PIPE);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    if (!result_) result_ = consumeToken(builder_, ASSIGN);
    if (!result_) result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, QUESTION);
    if (!result_) result_ = consumeToken(builder_, BANG);
    if (!result_) result_ = consumeToken(builder_, DOTDOT);
    if (!result_) result_ = consumeToken(builder_, DOTDOTDOT);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_COLON);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    if (!result_) result_ = consumeToken(builder_, NEWLINE);
    if (!result_) result_ = consumeToken(builder_, SEMICOLON);
    if (!result_) result_ = consumeToken(builder_, HASH);
    if (!result_) result_ = consumeToken(builder_, AT);
    if (!result_) result_ = consumeToken(builder_, ARROW);
    if (!result_) result_ = consumeToken(builder_, ANNOTATION);
    return result_;
  }

  /* ********************************************************** */
  // MACRO method_name [LPAREN NLS parameter_list NLS RPAREN] macro_body END
  public static boolean macro_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_definition")) return false;
    if (!nextTokenIs(builder_, MACRO)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MACRO_DEFINITION, null);
    result_ = consumeToken(builder_, MACRO);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, method_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, macro_definition_2(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, macro_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [LPAREN NLS parameter_list NLS RPAREN]
  private static boolean macro_definition_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_definition_2")) return false;
    macro_definition_2_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN NLS parameter_list NLS RPAREN
  private static boolean macro_definition_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_definition_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // MACRO_INTERPOLATION_BEGIN expression MACRO_INTERPOLATION_END
  public static boolean macro_interpolation(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "macro_interpolation")) return false;
    if (!nextTokenIs(builder_, MACRO_INTERPOLATION_BEGIN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, MACRO_INTERPOLATION_BEGIN);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, MACRO_INTERPOLATION_END);
    exit_section_(builder_, marker_, MACRO_INTERPOLATION, result_);
    return result_;
  }

  /* ********************************************************** */
  // statement_list rescue_clause* [else_clause] [ensure_clause]
  public static boolean method_body(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_body")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_BODY, "<method body>");
    result_ = statement_list(builder_, level_ + 1);
    result_ = result_ && method_body_1(builder_, level_ + 1);
    result_ = result_ && method_body_2(builder_, level_ + 1);
    result_ = result_ && method_body_3(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // rescue_clause*
  private static boolean method_body_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_body_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!rescue_clause(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "method_body_1", pos_)) break;
    }
    return true;
  }

  // [else_clause]
  private static boolean method_body_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_body_2")) return false;
    else_clause(builder_, level_ + 1);
    return true;
  }

  // [ensure_clause]
  private static boolean method_body_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_body_3")) return false;
    ensure_clause(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // (IDENTIFIER | CONSTANT | SELECT) call_args [block]
  //                          | (IDENTIFIER | CONSTANT | SELECT) bare_argument_list [block]
  //                          | (IDENTIFIER | CONSTANT) block
  //                          | (SUPER | PREVIOUS_DEF) call_args
  //                          | (SUPER | PREVIOUS_DEF) bare_argument_list
  public static boolean method_call_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_CALL_EXPRESSION, "<method call expression>");
    result_ = method_call_expression_0(builder_, level_ + 1);
    if (!result_) result_ = method_call_expression_1(builder_, level_ + 1);
    if (!result_) result_ = method_call_expression_2(builder_, level_ + 1);
    if (!result_) result_ = method_call_expression_3(builder_, level_ + 1);
    if (!result_) result_ = method_call_expression_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (IDENTIFIER | CONSTANT | SELECT) call_args [block]
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

  // IDENTIFIER | CONSTANT | SELECT
  private static boolean method_call_expression_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    if (!result_) result_ = consumeToken(builder_, SELECT);
    return result_;
  }

  // [block]
  private static boolean method_call_expression_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_0_2")) return false;
    block(builder_, level_ + 1);
    return true;
  }

  // (IDENTIFIER | CONSTANT | SELECT) bare_argument_list [block]
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

  // IDENTIFIER | CONSTANT | SELECT
  private static boolean method_call_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    if (!result_) result_ = consumeToken(builder_, SELECT);
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

  // (SUPER | PREVIOUS_DEF) call_args
  private static boolean method_call_expression_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = method_call_expression_3_0(builder_, level_ + 1);
    result_ = result_ && call_args(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SUPER | PREVIOUS_DEF
  private static boolean method_call_expression_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_3_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SUPER);
    if (!result_) result_ = consumeToken(builder_, PREVIOUS_DEF);
    return result_;
  }

  // (SUPER | PREVIOUS_DEF) bare_argument_list
  private static boolean method_call_expression_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = method_call_expression_4_0(builder_, level_ + 1);
    result_ = result_ && bare_argument_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SUPER | PREVIOUS_DEF
  private static boolean method_call_expression_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_call_expression_4_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, SUPER);
    if (!result_) result_ = consumeToken(builder_, PREVIOUS_DEF);
    return result_;
  }

  /* ********************************************************** */
  // ABSTRACT DEF method_name [LPAREN NLS parameter_list NLS RPAREN] [COLON type_reference] [FORALL CONSTANT (COMMA CONSTANT)*]
  //                     | DEF method_name [LPAREN NLS parameter_list NLS RPAREN] [COLON type_reference] [FORALL CONSTANT (COMMA CONSTANT)*] method_body END
  public static boolean method_definition(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition")) return false;
    if (!nextTokenIs(builder_, "<method definition>", ABSTRACT, DEF)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_DEFINITION, "<method definition>");
    result_ = method_definition_0(builder_, level_ + 1);
    if (!result_) result_ = method_definition_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // ABSTRACT DEF method_name [LPAREN NLS parameter_list NLS RPAREN] [COLON type_reference] [FORALL CONSTANT (COMMA CONSTANT)*]
  private static boolean method_definition_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeTokens(builder_, 2, ABSTRACT, DEF);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, method_name(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, method_definition_0_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, method_definition_0_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && method_definition_0_5(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [LPAREN NLS parameter_list NLS RPAREN]
  private static boolean method_definition_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0_3")) return false;
    method_definition_0_3_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN NLS parameter_list NLS RPAREN
  private static boolean method_definition_0_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0_3_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, parameter_list(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [COLON type_reference]
  private static boolean method_definition_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0_4")) return false;
    method_definition_0_4_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean method_definition_0_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [FORALL CONSTANT (COMMA CONSTANT)*]
  private static boolean method_definition_0_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0_5")) return false;
    method_definition_0_5_0(builder_, level_ + 1);
    return true;
  }

  // FORALL CONSTANT (COMMA CONSTANT)*
  private static boolean method_definition_0_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0_5_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeTokens(builder_, 2, FORALL, CONSTANT);
    pinned_ = result_; // pin = 2
    result_ = result_ && method_definition_0_5_0_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (COMMA CONSTANT)*
  private static boolean method_definition_0_5_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0_5_0_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!method_definition_0_5_0_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "method_definition_0_5_0_2", pos_)) break;
    }
    return true;
  }

  // COMMA CONSTANT
  private static boolean method_definition_0_5_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_0_5_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 2, COMMA, CONSTANT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DEF method_name [LPAREN NLS parameter_list NLS RPAREN] [COLON type_reference] [FORALL CONSTANT (COMMA CONSTANT)*] method_body END
  private static boolean method_definition_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, DEF);
    result_ = result_ && method_name(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, method_definition_1_2(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, method_definition_1_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, method_definition_1_4(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, method_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [LPAREN NLS parameter_list NLS RPAREN]
  private static boolean method_definition_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1_2")) return false;
    method_definition_1_2_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN NLS parameter_list NLS RPAREN
  private static boolean method_definition_1_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1_2_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    pinned_ = result_; // pin = 2
    result_ = result_ && report_error_(builder_, parameter_list(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [COLON type_reference]
  private static boolean method_definition_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1_3")) return false;
    method_definition_1_3_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean method_definition_1_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [FORALL CONSTANT (COMMA CONSTANT)*]
  private static boolean method_definition_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1_4")) return false;
    method_definition_1_4_0(builder_, level_ + 1);
    return true;
  }

  // FORALL CONSTANT (COMMA CONSTANT)*
  private static boolean method_definition_1_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1_4_0")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_);
    result_ = consumeTokens(builder_, 2, FORALL, CONSTANT);
    pinned_ = result_; // pin = 2
    result_ = result_ && method_definition_1_4_0_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // (COMMA CONSTANT)*
  private static boolean method_definition_1_4_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1_4_0_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!method_definition_1_4_0_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "method_definition_1_4_0_2", pos_)) break;
    }
    return true;
  }

  // COMMA CONSTANT
  private static boolean method_definition_1_4_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_definition_1_4_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 2, COMMA, CONSTANT);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // IDENTIFIER
  //               | CONSTANT
  //               | operator_method_name
  //               | SELF DOT (IDENTIFIER | CONSTANT | operator_method_name)
  //               | SELECT
  public static boolean method_name(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "method_name")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, METHOD_NAME, "<method name>");
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    if (!result_) result_ = operator_method_name(builder_, level_ + 1);
    if (!result_) result_ = method_name_3(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, SELECT);
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
  // STAR variable
  //                        | STAR UNDERSCORE
  //                        | variable [COLON type_reference]
  //                        | UNDERSCORE
  //                        | LPAREN multi_assign_target COMMA multi_assign_target_list RPAREN
  public static boolean multi_assign_target(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MULTI_ASSIGN_TARGET, "<multi assign target>");
    result_ = multi_assign_target_0(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, STAR, UNDERSCORE);
    if (!result_) result_ = multi_assign_target_2(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, UNDERSCORE);
    if (!result_) result_ = multi_assign_target_4(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // STAR variable
  private static boolean multi_assign_target_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STAR);
    result_ = result_ && variable(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // variable [COLON type_reference]
  private static boolean multi_assign_target_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = variable(builder_, level_ + 1);
    result_ = result_ && multi_assign_target_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COLON type_reference]
  private static boolean multi_assign_target_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target_2_1")) return false;
    multi_assign_target_2_1_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean multi_assign_target_2_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target_2_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LPAREN multi_assign_target COMMA multi_assign_target_list RPAREN
  private static boolean multi_assign_target_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && multi_assign_target(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && multi_assign_target_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // multi_assign_target (COMMA NLS multi_assign_target)*
  static boolean multi_assign_target_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target_list")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = multi_assign_target(builder_, level_ + 1);
    result_ = result_ && multi_assign_target_list_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA NLS multi_assign_target)*
  private static boolean multi_assign_target_list_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target_list_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!multi_assign_target_list_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "multi_assign_target_list_1", pos_)) break;
    }
    return true;
  }

  // COMMA NLS multi_assign_target
  private static boolean multi_assign_target_list_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_target_list_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && multi_assign_target(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // expression (COMMA expression)*
  static boolean multi_assign_values(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_values")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = expression(builder_, level_ + 1);
    result_ = result_ && multi_assign_values_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA expression)*
  private static boolean multi_assign_values_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_values_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!multi_assign_values_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "multi_assign_values_1", pos_)) break;
    }
    return true;
  }

  // COMMA expression
  private static boolean multi_assign_values_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assign_values_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // multi_assign_target COMMA NLS multi_assign_target_list ASSIGN NLS multi_assign_values
  public static boolean multi_assignment(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multi_assignment")) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, MULTI_ASSIGNMENT, "<multi assignment>");
    result_ = multi_assign_target(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && multi_assign_target_list(builder_, level_ + 1);
    pinned_ = result_; // pin = 4
    result_ = result_ && report_error_(builder_, consumeToken(builder_, ASSIGN));
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
    result_ = pinned_ && multi_assign_values(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // power_expression ((STAR | SLASH | DOUBLE_SLASH | PERCENT | WRAP_STAR) NLS power_expression)*
  static boolean multiplicative_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicative_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = power_expression(builder_, level_ + 1);
    result_ = result_ && multiplicative_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((STAR | SLASH | DOUBLE_SLASH | PERCENT | WRAP_STAR) NLS power_expression)*
  private static boolean multiplicative_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicative_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!multiplicative_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "multiplicative_expression_1", pos_)) break;
    }
    return true;
  }

  // (STAR | SLASH | DOUBLE_SLASH | PERCENT | WRAP_STAR) NLS power_expression
  private static boolean multiplicative_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicative_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = multiplicative_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && power_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STAR | SLASH | DOUBLE_SLASH | PERCENT | WRAP_STAR
  private static boolean multiplicative_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "multiplicative_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, SLASH);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_SLASH);
    if (!result_) result_ = consumeToken(builder_, PERCENT);
    if (!result_) result_ = consumeToken(builder_, WRAP_STAR);
    return result_;
  }

  /* ********************************************************** */
  // NEXT postfix_modifier
  //                  | NEXT expression [postfix_modifier]
  //                  | NEXT
  public static boolean next_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "next_statement")) return false;
    if (!nextTokenIs(builder_, NEXT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = next_statement_0(builder_, level_ + 1);
    if (!result_) result_ = next_statement_1(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, NEXT);
    exit_section_(builder_, marker_, NEXT_STATEMENT, result_);
    return result_;
  }

  // NEXT postfix_modifier
  private static boolean next_statement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "next_statement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NEXT);
    result_ = result_ && postfix_modifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NEXT expression [postfix_modifier]
  private static boolean next_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "next_statement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NEXT);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && next_statement_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [postfix_modifier]
  private static boolean next_statement_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "next_statement_1_2")) return false;
    postfix_modifier(builder_, level_ + 1);
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
  // OFFSETOF LPAREN NLS type_reference COMMA NLS instance_var_access NLS RPAREN
  public static boolean offsetof_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "offsetof_expression")) return false;
    if (!nextTokenIs(builder_, OFFSETOF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, OFFSETOF_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, OFFSETOF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, NLS(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, type_reference(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, consumeToken(builder_, COMMA)) && result_;
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, instance_var_access(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // PLUS | MINUS | STAR | SLASH | PERCENT | AMPERSAND | PIPE | CARET | TILDE
  //                                | WRAP_PLUS | WRAP_MINUS | WRAP_STAR | WRAP_DOUBLE_STAR
  //                                 | DOUBLE_STAR | LSHIFT | RSHIFT | EQ | NEQ | LT | GT | LTE | GTE
  //                                 | SPACESHIP | CASE_EQ | MATCH_OP | LBRACKET RBRACKET ASSIGN | LBRACKET RBRACKET
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
    if (!result_) result_ = consumeToken(builder_, WRAP_PLUS);
    if (!result_) result_ = consumeToken(builder_, WRAP_MINUS);
    if (!result_) result_ = consumeToken(builder_, WRAP_STAR);
    if (!result_) result_ = consumeToken(builder_, WRAP_DOUBLE_STAR);
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
    if (!result_) result_ = consumeToken(builder_, MATCH_OP);
    if (!result_) result_ = parseTokens(builder_, 0, LBRACKET, RBRACKET, ASSIGN);
    if (!result_) result_ = parseTokens(builder_, 0, LBRACKET, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // xor_expression (PIPE NLS xor_expression)*
  static boolean or_bitwise_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_bitwise_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = xor_expression(builder_, level_ + 1);
    result_ = result_ && or_bitwise_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (PIPE NLS xor_expression)*
  private static boolean or_bitwise_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_bitwise_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!or_bitwise_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "or_bitwise_expression_1", pos_)) break;
    }
    return true;
  }

  // PIPE NLS xor_expression
  private static boolean or_bitwise_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_bitwise_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && xor_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // and_expression (OR_OR NLS and_expression)*
  static boolean or_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = and_expression(builder_, level_ + 1);
    result_ = result_ && or_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (OR_OR NLS and_expression)*
  private static boolean or_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!or_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "or_expression_1", pos_)) break;
    }
    return true;
  }

  // OR_OR NLS and_expression
  private static boolean or_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "or_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, OR_OR);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && and_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // annotation_usage* [STAR | DOUBLE_STAR | AMPERSAND] IDENTIFIER IDENTIFIER [COLON type_reference] [ASSIGN expression]
  //             | annotation_usage* [STAR | DOUBLE_STAR | AMPERSAND] IDENTIFIER [COLON type_reference] [ASSIGN expression]
  //             | annotation_usage* [STAR | DOUBLE_STAR | AMPERSAND] instance_var_access [COLON type_reference] [ASSIGN expression]
  //             | AMPERSAND COLON type_union (COMMA type_union)* ARROW [type_union]
  //             | AMPERSAND [COLON type_reference]
  //             | LPAREN IDENTIFIER (COMMA IDENTIFIER)* RPAREN
  public static boolean parameter(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAMETER, "<parameter>");
    result_ = parameter_0(builder_, level_ + 1);
    if (!result_) result_ = parameter_1(builder_, level_ + 1);
    if (!result_) result_ = parameter_2(builder_, level_ + 1);
    if (!result_) result_ = parameter_3(builder_, level_ + 1);
    if (!result_) result_ = parameter_4(builder_, level_ + 1);
    if (!result_) result_ = parameter_5(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // annotation_usage* [STAR | DOUBLE_STAR | AMPERSAND] IDENTIFIER IDENTIFIER [COLON type_reference] [ASSIGN expression]
  private static boolean parameter_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parameter_0_0(builder_, level_ + 1);
    result_ = result_ && parameter_0_1(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, IDENTIFIER, IDENTIFIER);
    result_ = result_ && parameter_0_4(builder_, level_ + 1);
    result_ = result_ && parameter_0_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // annotation_usage*
  private static boolean parameter_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!annotation_usage(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parameter_0_0", pos_)) break;
    }
    return true;
  }

  // [STAR | DOUBLE_STAR | AMPERSAND]
  private static boolean parameter_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_1")) return false;
    parameter_0_1_0(builder_, level_ + 1);
    return true;
  }

  // STAR | DOUBLE_STAR | AMPERSAND
  private static boolean parameter_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    return result_;
  }

  // [COLON type_reference]
  private static boolean parameter_0_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_4")) return false;
    parameter_0_4_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean parameter_0_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ASSIGN expression]
  private static boolean parameter_0_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_5")) return false;
    parameter_0_5_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN expression
  private static boolean parameter_0_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_0_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // annotation_usage* [STAR | DOUBLE_STAR | AMPERSAND] IDENTIFIER [COLON type_reference] [ASSIGN expression]
  private static boolean parameter_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parameter_1_0(builder_, level_ + 1);
    result_ = result_ && parameter_1_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, IDENTIFIER);
    result_ = result_ && parameter_1_3(builder_, level_ + 1);
    result_ = result_ && parameter_1_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // annotation_usage*
  private static boolean parameter_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!annotation_usage(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parameter_1_0", pos_)) break;
    }
    return true;
  }

  // [STAR | DOUBLE_STAR | AMPERSAND]
  private static boolean parameter_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_1")) return false;
    parameter_1_1_0(builder_, level_ + 1);
    return true;
  }

  // STAR | DOUBLE_STAR | AMPERSAND
  private static boolean parameter_1_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    return result_;
  }

  // [COLON type_reference]
  private static boolean parameter_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_3")) return false;
    parameter_1_3_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean parameter_1_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ASSIGN expression]
  private static boolean parameter_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_4")) return false;
    parameter_1_4_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN expression
  private static boolean parameter_1_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_1_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // annotation_usage* [STAR | DOUBLE_STAR | AMPERSAND] instance_var_access [COLON type_reference] [ASSIGN expression]
  private static boolean parameter_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parameter_2_0(builder_, level_ + 1);
    result_ = result_ && parameter_2_1(builder_, level_ + 1);
    result_ = result_ && instance_var_access(builder_, level_ + 1);
    result_ = result_ && parameter_2_3(builder_, level_ + 1);
    result_ = result_ && parameter_2_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // annotation_usage*
  private static boolean parameter_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_2_0")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!annotation_usage(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parameter_2_0", pos_)) break;
    }
    return true;
  }

  // [STAR | DOUBLE_STAR | AMPERSAND]
  private static boolean parameter_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_2_1")) return false;
    parameter_2_1_0(builder_, level_ + 1);
    return true;
  }

  // STAR | DOUBLE_STAR | AMPERSAND
  private static boolean parameter_2_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_2_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    return result_;
  }

  // [COLON type_reference]
  private static boolean parameter_2_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_2_3")) return false;
    parameter_2_3_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean parameter_2_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_2_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ASSIGN expression]
  private static boolean parameter_2_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_2_4")) return false;
    parameter_2_4_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN expression
  private static boolean parameter_2_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_2_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AMPERSAND COLON type_union (COMMA type_union)* ARROW [type_union]
  private static boolean parameter_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, AMPERSAND, COLON);
    result_ = result_ && type_union(builder_, level_ + 1);
    result_ = result_ && parameter_3_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, ARROW);
    result_ = result_ && parameter_3_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA type_union)*
  private static boolean parameter_3_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_3_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!parameter_3_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parameter_3_3", pos_)) break;
    }
    return true;
  }

  // COMMA type_union
  private static boolean parameter_3_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_3_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && type_union(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [type_union]
  private static boolean parameter_3_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_3_5")) return false;
    type_union(builder_, level_ + 1);
    return true;
  }

  // AMPERSAND [COLON type_reference]
  private static boolean parameter_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, AMPERSAND);
    result_ = result_ && parameter_4_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COLON type_reference]
  private static boolean parameter_4_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_4_1")) return false;
    parameter_4_1_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean parameter_4_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_4_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LPAREN IDENTIFIER (COMMA IDENTIFIER)* RPAREN
  private static boolean parameter_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, LPAREN, IDENTIFIER);
    result_ = result_ && parameter_5_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA IDENTIFIER)*
  private static boolean parameter_5_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_5_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!parameter_5_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parameter_5_2", pos_)) break;
    }
    return true;
  }

  // COMMA IDENTIFIER
  private static boolean parameter_5_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_5_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, COMMA, IDENTIFIER);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bare_splat_separator | DOTDOTDOT | parameter
  static boolean parameter_item(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_item")) return false;
    boolean result_;
    result_ = bare_splat_separator(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, DOTDOTDOT);
    if (!result_) result_ = parameter(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // [parameter_item (COMMA NLS parameter_item)* [COMMA]]
  public static boolean parameter_list(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list")) return false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, PARAMETER_LIST, "<parameter list>");
    parameter_list_0(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, true, false, null);
    return true;
  }

  // parameter_item (COMMA NLS parameter_item)* [COMMA]
  private static boolean parameter_list_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = parameter_item(builder_, level_ + 1);
    result_ = result_ && parameter_list_0_1(builder_, level_ + 1);
    result_ = result_ && parameter_list_0_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA NLS parameter_item)*
  private static boolean parameter_list_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list_0_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!parameter_list_0_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "parameter_list_0_1", pos_)) break;
    }
    return true;
  }

  // COMMA NLS parameter_item
  private static boolean parameter_list_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && parameter_item(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COMMA]
  private static boolean parameter_list_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "parameter_list_0_2")) return false;
    consumeToken(builder_, COMMA);
    return true;
  }

  /* ********************************************************** */
  // PERCENT_LITERAL_BEGIN percent_literal_content* PERCENT_LITERAL_END
  public static boolean percent_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "percent_literal")) return false;
    if (!nextTokenIs(builder_, PERCENT_LITERAL_BEGIN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PERCENT_LITERAL_BEGIN);
    result_ = result_ && percent_literal_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, PERCENT_LITERAL_END);
    exit_section_(builder_, marker_, PERCENT_LITERAL, result_);
    return result_;
  }

  // percent_literal_content*
  private static boolean percent_literal_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "percent_literal_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!percent_literal_content(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "percent_literal_1", pos_)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // STRING_LITERAL | SYMBOL_LITERAL | REGEX_LITERAL | COMMAND_LITERAL | NEWLINE
  static boolean percent_literal_content(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "percent_literal_content")) return false;
    boolean result_;
    result_ = consumeToken(builder_, STRING_LITERAL);
    if (!result_) result_ = consumeToken(builder_, SYMBOL_LITERAL);
    if (!result_) result_ = consumeToken(builder_, REGEX_LITERAL);
    if (!result_) result_ = consumeToken(builder_, COMMAND_LITERAL);
    if (!result_) result_ = consumeToken(builder_, NEWLINE);
    return result_;
  }

  /* ********************************************************** */
  // POINTEROF LPAREN NLS (instance_var_access | class_var_access | variable_reference) NLS RPAREN
  public static boolean pointerof_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pointerof_expression")) return false;
    if (!nextTokenIs(builder_, POINTEROF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, POINTEROF_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, POINTEROF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, NLS(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, pointerof_expression_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, RPAREN) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // instance_var_access | class_var_access | variable_reference
  private static boolean pointerof_expression_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "pointerof_expression_3")) return false;
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
  // (IF | UNLESS | WHILE | UNTIL | RESCUE) expression
  public static boolean postfix_modifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_modifier")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, POSTFIX_MODIFIER, "<postfix modifier>");
    result_ = postfix_modifier_0(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // IF | UNLESS | WHILE | UNTIL | RESCUE
  private static boolean postfix_modifier_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_modifier_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IF);
    if (!result_) result_ = consumeToken(builder_, UNLESS);
    if (!result_) result_ = consumeToken(builder_, WHILE);
    if (!result_) result_ = consumeToken(builder_, UNTIL);
    if (!result_) result_ = consumeToken(builder_, RESCUE);
    return result_;
  }

  /* ********************************************************** */
  // NLS DOT (AS | AS_QUESTION | IS_A) LPAREN type_reference RPAREN
  //                      | NLS DOT (IDENTIFIER | CONSTANT | keyword_as_method) [call_args | bare_argument_list] [block]
  //                      | DOUBLE_COLON CONSTANT
  //                      | LBRACKET argument_list RBRACKET
  //                      | call_args [block]
  //                      | NLS DOT IDENTIFIER assign_op expression
  static boolean postfix_op(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = postfix_op_0(builder_, level_ + 1);
    if (!result_) result_ = postfix_op_1(builder_, level_ + 1);
    if (!result_) result_ = parseTokens(builder_, 0, DOUBLE_COLON, CONSTANT);
    if (!result_) result_ = postfix_op_3(builder_, level_ + 1);
    if (!result_) result_ = postfix_op_4(builder_, level_ + 1);
    if (!result_) result_ = postfix_op_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // NLS DOT (AS | AS_QUESTION | IS_A) LPAREN type_reference RPAREN
  private static boolean postfix_op_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOT);
    result_ = result_ && postfix_op_0_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, LPAREN);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // AS | AS_QUESTION | IS_A
  private static boolean postfix_op_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_0_2")) return false;
    boolean result_;
    result_ = consumeToken(builder_, AS);
    if (!result_) result_ = consumeToken(builder_, AS_QUESTION);
    if (!result_) result_ = consumeToken(builder_, IS_A);
    return result_;
  }

  // NLS DOT (IDENTIFIER | CONSTANT | keyword_as_method) [call_args | bare_argument_list] [block]
  private static boolean postfix_op_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, DOT);
    result_ = result_ && postfix_op_1_2(builder_, level_ + 1);
    result_ = result_ && postfix_op_1_3(builder_, level_ + 1);
    result_ = result_ && postfix_op_1_4(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT | keyword_as_method
  private static boolean postfix_op_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_1_2")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    if (!result_) result_ = keyword_as_method(builder_, level_ + 1);
    return result_;
  }

  // [call_args | bare_argument_list]
  private static boolean postfix_op_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_1_3")) return false;
    postfix_op_1_3_0(builder_, level_ + 1);
    return true;
  }

  // call_args | bare_argument_list
  private static boolean postfix_op_1_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_1_3_0")) return false;
    boolean result_;
    result_ = call_args(builder_, level_ + 1);
    if (!result_) result_ = bare_argument_list(builder_, level_ + 1);
    return result_;
  }

  // [block]
  private static boolean postfix_op_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_1_4")) return false;
    block(builder_, level_ + 1);
    return true;
  }

  // LBRACKET argument_list RBRACKET
  private static boolean postfix_op_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACKET);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACKET);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // call_args [block]
  private static boolean postfix_op_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = call_args(builder_, level_ + 1);
    result_ = result_ && postfix_op_4_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [block]
  private static boolean postfix_op_4_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_4_1")) return false;
    block(builder_, level_ + 1);
    return true;
  }

  // NLS DOT IDENTIFIER assign_op expression
  private static boolean postfix_op_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "postfix_op_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, DOT, IDENTIFIER);
    result_ = result_ && assign_op(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // unary_expression ((DOUBLE_STAR | WRAP_DOUBLE_STAR) NLS unary_expression)*
  static boolean power_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "power_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = unary_expression(builder_, level_ + 1);
    result_ = result_ && power_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((DOUBLE_STAR | WRAP_DOUBLE_STAR) NLS unary_expression)*
  private static boolean power_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "power_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!power_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "power_expression_1", pos_)) break;
    }
    return true;
  }

  // (DOUBLE_STAR | WRAP_DOUBLE_STAR) NLS unary_expression
  private static boolean power_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "power_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = power_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && unary_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // DOUBLE_STAR | WRAP_DOUBLE_STAR
  private static boolean power_expression_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "power_expression_1_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, DOUBLE_STAR);
    if (!result_) result_ = consumeToken(builder_, WRAP_DOUBLE_STAR);
    return result_;
  }

  /* ********************************************************** */
  // grouped_expression
  //                              | array_literal
  //                              | hash_literal
  //                              | tuple_literal
  //                              | proc_literal
  //                              | method_call_expression
  //                              | implicit_object_call
  //                              | literal
  //                              | instance_var_access
  //                              | class_var_access
  //                              | variable_reference
  //                              | typeof_expression
  //                              | sizeof_expression
  //                              | instance_sizeof_expression
  //                               | pointerof_expression
  //                              | offsetof_expression
  //                              | uninitialized_expression
  //                              | asm_expression
  //                              | yield_expression
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
    if (!result_) result_ = proc_literal(builder_, level_ + 1);
    if (!result_) result_ = method_call_expression(builder_, level_ + 1);
    if (!result_) result_ = implicit_object_call(builder_, level_ + 1);
    if (!result_) result_ = literal(builder_, level_ + 1);
    if (!result_) result_ = instance_var_access(builder_, level_ + 1);
    if (!result_) result_ = class_var_access(builder_, level_ + 1);
    if (!result_) result_ = variable_reference(builder_, level_ + 1);
    if (!result_) result_ = typeof_expression(builder_, level_ + 1);
    if (!result_) result_ = sizeof_expression(builder_, level_ + 1);
    if (!result_) result_ = instance_sizeof_expression(builder_, level_ + 1);
    if (!result_) result_ = pointerof_expression(builder_, level_ + 1);
    if (!result_) result_ = offsetof_expression(builder_, level_ + 1);
    if (!result_) result_ = uninitialized_expression(builder_, level_ + 1);
    if (!result_) result_ = asm_expression(builder_, level_ + 1);
    if (!result_) result_ = yield_expression(builder_, level_ + 1);
    if (!result_) result_ = if_statement(builder_, level_ + 1);
    if (!result_) result_ = unless_statement(builder_, level_ + 1);
    if (!result_) result_ = case_statement(builder_, level_ + 1);
    if (!result_) result_ = begin_statement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // ARROW LPAREN NLS parameter_list NLS RPAREN LBRACE statement_list RBRACE
  //                | ARROW LBRACE statement_list RBRACE
  //                | ARROW (IDENTIFIER | CONSTANT) [DOT (IDENTIFIER | CONSTANT)] [LPAREN type_reference (COMMA type_reference)* RPAREN]
  public static boolean proc_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal")) return false;
    if (!nextTokenIs(builder_, ARROW)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = proc_literal_0(builder_, level_ + 1);
    if (!result_) result_ = proc_literal_1(builder_, level_ + 1);
    if (!result_) result_ = proc_literal_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, PROC_LITERAL, result_);
    return result_;
  }

  // ARROW LPAREN NLS parameter_list NLS RPAREN LBRACE statement_list RBRACE
  private static boolean proc_literal_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ARROW, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, RPAREN, LBRACE);
    result_ = result_ && statement_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ARROW LBRACE statement_list RBRACE
  private static boolean proc_literal_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, ARROW, LBRACE);
    result_ = result_ && statement_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ARROW (IDENTIFIER | CONSTANT) [DOT (IDENTIFIER | CONSTANT)] [LPAREN type_reference (COMMA type_reference)* RPAREN]
  private static boolean proc_literal_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ARROW);
    result_ = result_ && proc_literal_2_1(builder_, level_ + 1);
    result_ = result_ && proc_literal_2_2(builder_, level_ + 1);
    result_ = result_ && proc_literal_2_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT
  private static boolean proc_literal_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    return result_;
  }

  // [DOT (IDENTIFIER | CONSTANT)]
  private static boolean proc_literal_2_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2_2")) return false;
    proc_literal_2_2_0(builder_, level_ + 1);
    return true;
  }

  // DOT (IDENTIFIER | CONSTANT)
  private static boolean proc_literal_2_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, DOT);
    result_ = result_ && proc_literal_2_2_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // IDENTIFIER | CONSTANT
  private static boolean proc_literal_2_2_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2_2_0_1")) return false;
    boolean result_;
    result_ = consumeToken(builder_, IDENTIFIER);
    if (!result_) result_ = consumeToken(builder_, CONSTANT);
    return result_;
  }

  // [LPAREN type_reference (COMMA type_reference)* RPAREN]
  private static boolean proc_literal_2_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2_3")) return false;
    proc_literal_2_3_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN type_reference (COMMA type_reference)* RPAREN
  private static boolean proc_literal_2_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && proc_literal_2_3_0_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA type_reference)*
  private static boolean proc_literal_2_3_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2_3_0_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!proc_literal_2_3_0_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "proc_literal_2_3_0_2", pos_)) break;
    }
    return true;
  }

  // COMMA type_reference
  private static boolean proc_literal_2_3_0_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "proc_literal_2_3_0_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
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
  // or_bitwise_expression [(DOTDOT | DOTDOTDOT) NLS [or_bitwise_expression]]
  static boolean range_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "range_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = or_bitwise_expression(builder_, level_ + 1);
    result_ = result_ && range_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [(DOTDOT | DOTDOTDOT) NLS [or_bitwise_expression]]
  private static boolean range_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "range_expression_1")) return false;
    range_expression_1_0(builder_, level_ + 1);
    return true;
  }

  // (DOTDOT | DOTDOTDOT) NLS [or_bitwise_expression]
  private static boolean range_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "range_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = range_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && range_expression_1_0_2(builder_, level_ + 1);
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

  // [or_bitwise_expression]
  private static boolean range_expression_1_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "range_expression_1_0_2")) return false;
    or_bitwise_expression(builder_, level_ + 1);
    return true;
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
  // RETURN postfix_modifier
  //                    | RETURN expression [postfix_modifier]
  //                    | RETURN
  public static boolean return_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "return_statement")) return false;
    if (!nextTokenIs(builder_, RETURN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = return_statement_0(builder_, level_ + 1);
    if (!result_) result_ = return_statement_1(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, RETURN);
    exit_section_(builder_, marker_, RETURN_STATEMENT, result_);
    return result_;
  }

  // RETURN postfix_modifier
  private static boolean return_statement_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "return_statement_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, RETURN);
    result_ = result_ && postfix_modifier(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // RETURN expression [postfix_modifier]
  private static boolean return_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "return_statement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, RETURN);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && return_statement_1_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [postfix_modifier]
  private static boolean return_statement_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "return_statement_1_2")) return false;
    postfix_modifier(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // SELECT NEWLINE+ select_when_clause+ [else_clause] END
  public static boolean select_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "select_statement")) return false;
    if (!nextTokenIs(builder_, SELECT)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SELECT);
    result_ = result_ && select_statement_1(builder_, level_ + 1);
    result_ = result_ && select_statement_2(builder_, level_ + 1);
    result_ = result_ && select_statement_3(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, END);
    exit_section_(builder_, marker_, SELECT_STATEMENT, result_);
    return result_;
  }

  // NEWLINE+
  private static boolean select_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "select_statement_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NEWLINE);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!consumeToken(builder_, NEWLINE)) break;
      if (!empty_element_parsed_guard_(builder_, "select_statement_1", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // select_when_clause+
  private static boolean select_statement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "select_statement_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = select_when_clause(builder_, level_ + 1);
    while (result_) {
      int pos_ = current_position_(builder_);
      if (!select_when_clause(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "select_statement_2", pos_)) break;
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [else_clause]
  private static boolean select_statement_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "select_statement_3")) return false;
    else_clause(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // WHEN statement then_clause statement_list
  public static boolean select_when_clause(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "select_when_clause")) return false;
    if (!nextTokenIs(builder_, WHEN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, WHEN);
    result_ = result_ && statement(builder_, level_ + 1);
    result_ = result_ && then_clause(builder_, level_ + 1);
    result_ = result_ && statement_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, SELECT_WHEN_CLAUSE, result_);
    return result_;
  }

  /* ********************************************************** */
  // additive_expression ((LSHIFT | RSHIFT) NLS additive_expression)*
  static boolean shift_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shift_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = additive_expression(builder_, level_ + 1);
    result_ = result_ && shift_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // ((LSHIFT | RSHIFT) NLS additive_expression)*
  private static boolean shift_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shift_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!shift_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "shift_expression_1", pos_)) break;
    }
    return true;
  }

  // (LSHIFT | RSHIFT) NLS additive_expression
  private static boolean shift_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "shift_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = shift_expression_1_0_0(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
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
  // SIZEOF LPAREN NLS type_reference NLS RPAREN
  public static boolean sizeof_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "sizeof_expression")) return false;
    if (!nextTokenIs(builder_, SIZEOF)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, SIZEOF_EXPRESSION, null);
    result_ = consumeTokens(builder_, 1, SIZEOF, LPAREN);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, NLS(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, type_reference(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, NLS(builder_, level_ + 1)) && result_;
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
  //             | select_statement
  //             | return_statement
  //             | break_statement
  //             | next_statement
  //             | yield_statement
  //             | with_yield_statement
  //             | include_statement
  //             | extend_statement
  //             | multi_assignment
  //             | property_declaration
  //             | assignment
  //             | constant_assignment
  //             | expression_statement
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
    if (!result_) result_ = select_statement(builder_, level_ + 1);
    if (!result_) result_ = return_statement(builder_, level_ + 1);
    if (!result_) result_ = break_statement(builder_, level_ + 1);
    if (!result_) result_ = next_statement(builder_, level_ + 1);
    if (!result_) result_ = yield_statement(builder_, level_ + 1);
    if (!result_) result_ = with_yield_statement(builder_, level_ + 1);
    if (!result_) result_ = include_statement(builder_, level_ + 1);
    if (!result_) result_ = extend_statement(builder_, level_ + 1);
    if (!result_) result_ = multi_assignment(builder_, level_ + 1);
    if (!result_) result_ = property_declaration(builder_, level_ + 1);
    if (!result_) result_ = assignment(builder_, level_ + 1);
    if (!result_) result_ = constant_assignment(builder_, level_ + 1);
    if (!result_) result_ = expression_statement(builder_, level_ + 1);
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
  // FUN IDENTIFIER [LPAREN NLS parameter_list NLS RPAREN] [COLON type_reference] method_body END
  public static boolean top_level_fun(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_level_fun")) return false;
    if (!nextTokenIs(builder_, FUN)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, TOP_LEVEL_FUN, null);
    result_ = consumeTokens(builder_, 1, FUN, IDENTIFIER);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, top_level_fun_2(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, top_level_fun_3(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, method_body(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [LPAREN NLS parameter_list NLS RPAREN]
  private static boolean top_level_fun_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_level_fun_2")) return false;
    top_level_fun_2_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN NLS parameter_list NLS RPAREN
  private static boolean top_level_fun_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_level_fun_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && parameter_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COLON type_reference]
  private static boolean top_level_fun_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_level_fun_3")) return false;
    top_level_fun_3_0(builder_, level_ + 1);
    return true;
  }

  // COLON type_reference
  private static boolean top_level_fun_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "top_level_fun_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
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
  //                               | top_level_fun
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
    if (!result_) result_ = top_level_fun(builder_, level_ + 1);
    if (!result_) result_ = annotation_definition(builder_, level_ + 1);
    if (!result_) result_ = method_definition(builder_, level_ + 1);
    if (!result_) result_ = macro_definition(builder_, level_ + 1);
    if (!result_) result_ = alias_definition(builder_, level_ + 1);
    if (!result_) result_ = visibility_modifier(builder_, level_ + 1);
    if (!result_) result_ = statement(builder_, level_ + 1);
    return result_;
  }

  /* ********************************************************** */
  // LBRACE NLS expression_list NLS RBRACE
  public static boolean tuple_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "tuple_literal")) return false;
    if (!nextTokenIs(builder_, LBRACE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && expression_list(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
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
  // type_reference | INTEGER_LITERAL
  static boolean type_argument(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_argument")) return false;
    boolean result_;
    result_ = type_reference(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, INTEGER_LITERAL);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN NLS type_argument (NLS COMMA NLS type_argument)* [NLS COMMA] NLS RPAREN
  public static boolean type_arguments(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_arguments")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && type_argument(builder_, level_ + 1);
    result_ = result_ && type_arguments_3(builder_, level_ + 1);
    result_ = result_ && type_arguments_4(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, TYPE_ARGUMENTS, result_);
    return result_;
  }

  // (NLS COMMA NLS type_argument)*
  private static boolean type_arguments_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_arguments_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_arguments_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_arguments_3", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS type_argument
  private static boolean type_arguments_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_arguments_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && type_argument(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [NLS COMMA]
  private static boolean type_arguments_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_arguments_4")) return false;
    type_arguments_4_0(builder_, level_ + 1);
    return true;
  }

  // NLS COMMA
  private static boolean type_arguments_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_arguments_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
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
  // [STAR] CONSTANT [ASSIGN type_reference]
  static boolean type_parameter_item(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameter_item")) return false;
    if (!nextTokenIs(builder_, "", CONSTANT, STAR)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type_parameter_item_0(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, CONSTANT);
    result_ = result_ && type_parameter_item_2(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [STAR]
  private static boolean type_parameter_item_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameter_item_0")) return false;
    consumeToken(builder_, STAR);
    return true;
  }

  // [ASSIGN type_reference]
  private static boolean type_parameter_item_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameter_item_2")) return false;
    type_parameter_item_2_0(builder_, level_ + 1);
    return true;
  }

  // ASSIGN type_reference
  private static boolean type_parameter_item_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameter_item_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ASSIGN);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // LPAREN NLS type_parameter_item (COMMA NLS type_parameter_item)* [COMMA] NLS RPAREN
  public static boolean type_parameters(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameters")) return false;
    if (!nextTokenIs(builder_, LPAREN)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && type_parameter_item(builder_, level_ + 1);
    result_ = result_ && type_parameters_3(builder_, level_ + 1);
    result_ = result_ && type_parameters_4(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, TYPE_PARAMETERS, result_);
    return result_;
  }

  // (COMMA NLS type_parameter_item)*
  private static boolean type_parameters_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameters_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_parameters_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_parameters_3", pos_)) break;
    }
    return true;
  }

  // COMMA NLS type_parameter_item
  private static boolean type_parameters_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameters_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && type_parameter_item(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [COMMA]
  private static boolean type_parameters_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_parameters_4")) return false;
    consumeToken(builder_, COMMA);
    return true;
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
  // type_union [ARROW [type_union]]
  //                  | ARROW [type_union]
  public static boolean type_reference(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_reference")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, TYPE_REFERENCE, "<type reference>");
    result_ = type_reference_0(builder_, level_ + 1);
    if (!result_) result_ = type_reference_1(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // type_union [ARROW [type_union]]
  private static boolean type_reference_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_reference_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type_union(builder_, level_ + 1);
    result_ = result_ && type_reference_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [ARROW [type_union]]
  private static boolean type_reference_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_reference_0_1")) return false;
    type_reference_0_1_0(builder_, level_ + 1);
    return true;
  }

  // ARROW [type_union]
  private static boolean type_reference_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_reference_0_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ARROW);
    result_ = result_ && type_reference_0_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [type_union]
  private static boolean type_reference_0_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_reference_0_1_0_1")) return false;
    type_union(builder_, level_ + 1);
    return true;
  }

  // ARROW [type_union]
  private static boolean type_reference_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_reference_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, ARROW);
    result_ = result_ && type_reference_1_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [type_union]
  private static boolean type_reference_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_reference_1_1")) return false;
    type_union(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // STAR type_path [type_arguments] [QUESTION]
  //                        | type_path [type_arguments] [DOT CLASS] [QUESTION] [STAR] [DOUBLE_STAR] [LBRACKET INTEGER_LITERAL RBRACKET]
  //                        | SELF [QUESTION]
  //                        | TYPEOF LPAREN NLS expression (COMMA NLS expression)* NLS RPAREN [QUESTION]
  //                        | LPAREN type_reference (COMMA type_reference)* RPAREN
  //                        | LBRACE NLS IDENTIFIER COLON type_reference (NLS COMMA NLS IDENTIFIER COLON type_reference)* NLS RBRACE
  //                        | LBRACE NLS type_reference (NLS COMMA NLS type_reference)* NLS RBRACE
  //                        | IDENTIFIER
  static boolean type_single(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type_single_0(builder_, level_ + 1);
    if (!result_) result_ = type_single_1(builder_, level_ + 1);
    if (!result_) result_ = type_single_2(builder_, level_ + 1);
    if (!result_) result_ = type_single_3(builder_, level_ + 1);
    if (!result_) result_ = type_single_4(builder_, level_ + 1);
    if (!result_) result_ = type_single_5(builder_, level_ + 1);
    if (!result_) result_ = type_single_6(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, IDENTIFIER);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // STAR type_path [type_arguments] [QUESTION]
  private static boolean type_single_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STAR);
    result_ = result_ && type_path(builder_, level_ + 1);
    result_ = result_ && type_single_0_2(builder_, level_ + 1);
    result_ = result_ && type_single_0_3(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [type_arguments]
  private static boolean type_single_0_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_0_2")) return false;
    type_arguments(builder_, level_ + 1);
    return true;
  }

  // [QUESTION]
  private static boolean type_single_0_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_0_3")) return false;
    consumeToken(builder_, QUESTION);
    return true;
  }

  // type_path [type_arguments] [DOT CLASS] [QUESTION] [STAR] [DOUBLE_STAR] [LBRACKET INTEGER_LITERAL RBRACKET]
  private static boolean type_single_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type_path(builder_, level_ + 1);
    result_ = result_ && type_single_1_1(builder_, level_ + 1);
    result_ = result_ && type_single_1_2(builder_, level_ + 1);
    result_ = result_ && type_single_1_3(builder_, level_ + 1);
    result_ = result_ && type_single_1_4(builder_, level_ + 1);
    result_ = result_ && type_single_1_5(builder_, level_ + 1);
    result_ = result_ && type_single_1_6(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [type_arguments]
  private static boolean type_single_1_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_1_1")) return false;
    type_arguments(builder_, level_ + 1);
    return true;
  }

  // [DOT CLASS]
  private static boolean type_single_1_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_1_2")) return false;
    parseTokens(builder_, 0, DOT, CLASS);
    return true;
  }

  // [QUESTION]
  private static boolean type_single_1_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_1_3")) return false;
    consumeToken(builder_, QUESTION);
    return true;
  }

  // [STAR]
  private static boolean type_single_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_1_4")) return false;
    consumeToken(builder_, STAR);
    return true;
  }

  // [DOUBLE_STAR]
  private static boolean type_single_1_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_1_5")) return false;
    consumeToken(builder_, DOUBLE_STAR);
    return true;
  }

  // [LBRACKET INTEGER_LITERAL RBRACKET]
  private static boolean type_single_1_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_1_6")) return false;
    parseTokens(builder_, 0, LBRACKET, INTEGER_LITERAL, RBRACKET);
    return true;
  }

  // SELF [QUESTION]
  private static boolean type_single_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SELF);
    result_ = result_ && type_single_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [QUESTION]
  private static boolean type_single_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_2_1")) return false;
    consumeToken(builder_, QUESTION);
    return true;
  }

  // TYPEOF LPAREN NLS expression (COMMA NLS expression)* NLS RPAREN [QUESTION]
  private static boolean type_single_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_3")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeTokens(builder_, 0, TYPEOF, LPAREN);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    result_ = result_ && type_single_3_4(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    result_ = result_ && type_single_3_7(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA NLS expression)*
  private static boolean type_single_3_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_3_4")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_single_3_4_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_single_3_4", pos_)) break;
    }
    return true;
  }

  // COMMA NLS expression
  private static boolean type_single_3_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_3_4_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [QUESTION]
  private static boolean type_single_3_7(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_3_7")) return false;
    consumeToken(builder_, QUESTION);
    return true;
  }

  // LPAREN type_reference (COMMA type_reference)* RPAREN
  private static boolean type_single_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_4")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && type_single_4_2(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA type_reference)*
  private static boolean type_single_4_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_4_2")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_single_4_2_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_single_4_2", pos_)) break;
    }
    return true;
  }

  // COMMA type_reference
  private static boolean type_single_4_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_4_2_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LBRACE NLS IDENTIFIER COLON type_reference (NLS COMMA NLS IDENTIFIER COLON type_reference)* NLS RBRACE
  private static boolean type_single_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_5")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, IDENTIFIER, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && type_single_5_5(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (NLS COMMA NLS IDENTIFIER COLON type_reference)*
  private static boolean type_single_5_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_5_5")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_single_5_5_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_single_5_5", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS IDENTIFIER COLON type_reference
  private static boolean type_single_5_5_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_5_5_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, IDENTIFIER, COLON);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LBRACE NLS type_reference (NLS COMMA NLS type_reference)* NLS RBRACE
  private static boolean type_single_6(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_6")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LBRACE);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && type_reference(builder_, level_ + 1);
    result_ = result_ && type_single_6_3(builder_, level_ + 1);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RBRACE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (NLS COMMA NLS type_reference)*
  private static boolean type_single_6_3(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_6_3")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_single_6_3_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_single_6_3", pos_)) break;
    }
    return true;
  }

  // NLS COMMA NLS type_reference
  private static boolean type_single_6_3_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_single_6_3_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = NLS(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, COMMA);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // type_single (PIPE NLS type_single)*
  static boolean type_union(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_union")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = type_single(builder_, level_ + 1);
    result_ = result_ && type_union_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (PIPE NLS type_single)*
  private static boolean type_union_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_union_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!type_union_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "type_union_1", pos_)) break;
    }
    return true;
  }

  // PIPE NLS type_single
  private static boolean type_union_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "type_union_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, PIPE);
    result_ = result_ && NLS(builder_, level_ + 1);
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
  // (PLUS | MINUS | TILDE | AMPERSAND | STAR | CARET) unary_expression
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

  // (PLUS | MINUS | TILDE | AMPERSAND | STAR | CARET) unary_expression
  private static boolean unary_expression_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unary_expression_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = unary_expression_0_0(builder_, level_ + 1);
    result_ = result_ && unary_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PLUS | MINUS | TILDE | AMPERSAND | STAR | CARET
  private static boolean unary_expression_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unary_expression_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PLUS);
    if (!result_) result_ = consumeToken(builder_, MINUS);
    if (!result_) result_ = consumeToken(builder_, TILDE);
    if (!result_) result_ = consumeToken(builder_, AMPERSAND);
    if (!result_) result_ = consumeToken(builder_, STAR);
    if (!result_) result_ = consumeToken(builder_, CARET);
    return result_;
  }

  /* ********************************************************** */
  // UNINITIALIZED type_reference
  public static boolean uninitialized_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "uninitialized_expression")) return false;
    if (!nextTokenIs(builder_, UNINITIALIZED)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UNINITIALIZED_EXPRESSION, null);
    result_ = consumeToken(builder_, UNINITIALIZED);
    pinned_ = result_; // pin = 1
    result_ = result_ && type_reference(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // UNLESS condition then_clause statement_list [else_clause] END
  public static boolean unless_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "unless_statement")) return false;
    if (!nextTokenIs(builder_, UNLESS)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UNLESS_STATEMENT, null);
    result_ = consumeToken(builder_, UNLESS);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, condition(builder_, level_ + 1));
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
  // UNTIL condition then_clause statement_list END
  public static boolean until_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "until_statement")) return false;
    if (!nextTokenIs(builder_, UNTIL)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, UNTIL_STATEMENT, null);
    result_ = consumeToken(builder_, UNTIL);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, condition(builder_, level_ + 1));
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
  //                       | (PRIVATE | PROTECTED) SELF DOT IDENTIFIER [call_args | bare_argument_list] [block]
  //                       | (PRIVATE | PROTECTED) &NEWLINE
  public static boolean visibility_modifier(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier")) return false;
    if (!nextTokenIs(builder_, "<visibility modifier>", PRIVATE, PROTECTED)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, VISIBILITY_MODIFIER, "<visibility modifier>");
    result_ = visibility_modifier_0(builder_, level_ + 1);
    if (!result_) result_ = visibility_modifier_1(builder_, level_ + 1);
    if (!result_) result_ = visibility_modifier_2(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, false, null);
    return result_;
  }

  // (PRIVATE | PROTECTED) (method_definition | macro_definition | class_definition | struct_definition | constant_assignment | statement)
  private static boolean visibility_modifier_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = visibility_modifier_0_0(builder_, level_ + 1);
    result_ = result_ && visibility_modifier_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PRIVATE | PROTECTED
  private static boolean visibility_modifier_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_0_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PRIVATE);
    if (!result_) result_ = consumeToken(builder_, PROTECTED);
    return result_;
  }

  // method_definition | macro_definition | class_definition | struct_definition | constant_assignment | statement
  private static boolean visibility_modifier_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_0_1")) return false;
    boolean result_;
    result_ = method_definition(builder_, level_ + 1);
    if (!result_) result_ = macro_definition(builder_, level_ + 1);
    if (!result_) result_ = class_definition(builder_, level_ + 1);
    if (!result_) result_ = struct_definition(builder_, level_ + 1);
    if (!result_) result_ = constant_assignment(builder_, level_ + 1);
    if (!result_) result_ = statement(builder_, level_ + 1);
    return result_;
  }

  // (PRIVATE | PROTECTED) SELF DOT IDENTIFIER [call_args | bare_argument_list] [block]
  private static boolean visibility_modifier_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = visibility_modifier_1_0(builder_, level_ + 1);
    result_ = result_ && consumeTokens(builder_, 0, SELF, DOT, IDENTIFIER);
    result_ = result_ && visibility_modifier_1_4(builder_, level_ + 1);
    result_ = result_ && visibility_modifier_1_5(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PRIVATE | PROTECTED
  private static boolean visibility_modifier_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_1_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PRIVATE);
    if (!result_) result_ = consumeToken(builder_, PROTECTED);
    return result_;
  }

  // [call_args | bare_argument_list]
  private static boolean visibility_modifier_1_4(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_1_4")) return false;
    visibility_modifier_1_4_0(builder_, level_ + 1);
    return true;
  }

  // call_args | bare_argument_list
  private static boolean visibility_modifier_1_4_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_1_4_0")) return false;
    boolean result_;
    result_ = call_args(builder_, level_ + 1);
    if (!result_) result_ = bare_argument_list(builder_, level_ + 1);
    return result_;
  }

  // [block]
  private static boolean visibility_modifier_1_5(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_1_5")) return false;
    block(builder_, level_ + 1);
    return true;
  }

  // (PRIVATE | PROTECTED) &NEWLINE
  private static boolean visibility_modifier_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_2")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = visibility_modifier_2_0(builder_, level_ + 1);
    result_ = result_ && visibility_modifier_2_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // PRIVATE | PROTECTED
  private static boolean visibility_modifier_2_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_2_0")) return false;
    boolean result_;
    result_ = consumeToken(builder_, PRIVATE);
    if (!result_) result_ = consumeToken(builder_, PROTECTED);
    return result_;
  }

  // &NEWLINE
  private static boolean visibility_modifier_2_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "visibility_modifier_2_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _AND_);
    result_ = consumeToken(builder_, NEWLINE);
    exit_section_(builder_, level_, marker_, result_, false, null);
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
  // WHILE condition then_clause statement_list END
  public static boolean while_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "while_statement")) return false;
    if (!nextTokenIs(builder_, WHILE)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, WHILE_STATEMENT, null);
    result_ = consumeToken(builder_, WHILE);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, condition(builder_, level_ + 1));
    result_ = pinned_ && report_error_(builder_, then_clause(builder_, level_ + 1)) && result_;
    result_ = pinned_ && report_error_(builder_, statement_list(builder_, level_ + 1)) && result_;
    result_ = pinned_ && consumeToken(builder_, END) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // WITH expression YIELD
  public static boolean with_yield_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "with_yield_statement")) return false;
    if (!nextTokenIs(builder_, WITH)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, WITH_YIELD_STATEMENT, null);
    result_ = consumeToken(builder_, WITH);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, expression(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, YIELD) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // and_bitwise_expression (CARET NLS and_bitwise_expression)*
  static boolean xor_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xor_expression")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = and_bitwise_expression(builder_, level_ + 1);
    result_ = result_ && xor_expression_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (CARET NLS and_bitwise_expression)*
  private static boolean xor_expression_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xor_expression_1")) return false;
    while (true) {
      int pos_ = current_position_(builder_);
      if (!xor_expression_1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "xor_expression_1", pos_)) break;
    }
    return true;
  }

  // CARET NLS and_bitwise_expression
  private static boolean xor_expression_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "xor_expression_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, CARET);
    result_ = result_ && NLS(builder_, level_ + 1);
    result_ = result_ && and_bitwise_expression(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // YIELD expression
  public static boolean yield_expression(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_expression")) return false;
    if (!nextTokenIs(builder_, YIELD)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, YIELD_EXPRESSION, null);
    result_ = consumeToken(builder_, YIELD);
    pinned_ = result_; // pin = 1
    result_ = result_ && expression(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // YIELD [LPAREN argument_list RPAREN | bare_argument_list] [postfix_modifier]
  public static boolean yield_statement(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_statement")) return false;
    if (!nextTokenIs(builder_, YIELD)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, YIELD_STATEMENT, null);
    result_ = consumeToken(builder_, YIELD);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, yield_statement_1(builder_, level_ + 1));
    result_ = pinned_ && yield_statement_2(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, result_, pinned_, null);
    return result_ || pinned_;
  }

  // [LPAREN argument_list RPAREN | bare_argument_list]
  private static boolean yield_statement_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_statement_1")) return false;
    yield_statement_1_0(builder_, level_ + 1);
    return true;
  }

  // LPAREN argument_list RPAREN | bare_argument_list
  private static boolean yield_statement_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_statement_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = yield_statement_1_0_0(builder_, level_ + 1);
    if (!result_) result_ = bare_argument_list(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // LPAREN argument_list RPAREN
  private static boolean yield_statement_1_0_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_statement_1_0_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, LPAREN);
    result_ = result_ && argument_list(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, RPAREN);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // [postfix_modifier]
  private static boolean yield_statement_2(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "yield_statement_2")) return false;
    postfix_modifier(builder_, level_ + 1);
    return true;
  }

}
