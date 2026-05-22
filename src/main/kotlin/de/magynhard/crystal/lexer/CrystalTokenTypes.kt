package de.magynhard.crystal.lexer

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import de.magynhard.crystal.CrystalLanguage

class CrystalTokenType(debugName: String) : IElementType(debugName, CrystalLanguage) {
    override fun toString(): String = "CrystalTokenType.${super.toString()}"
}

object CrystalTokenTypes {
    // Keywords
    @JvmField val ABSTRACT = CrystalTokenType("ABSTRACT")
    @JvmField val ALIAS = CrystalTokenType("ALIAS")
    @JvmField val ANNOTATION = CrystalTokenType("ANNOTATION")
    @JvmField val AS = CrystalTokenType("AS")
    @JvmField val AS_QUESTION = CrystalTokenType("AS_QUESTION")
    @JvmField val ASM = CrystalTokenType("ASM")
    @JvmField val BEGIN = CrystalTokenType("BEGIN")
    @JvmField val BREAK = CrystalTokenType("BREAK")
    @JvmField val CASE = CrystalTokenType("CASE")
    @JvmField val CLASS = CrystalTokenType("CLASS")
    @JvmField val DEF = CrystalTokenType("DEF")
    @JvmField val DO = CrystalTokenType("DO")
    @JvmField val ELSE = CrystalTokenType("ELSE")
    @JvmField val ELSIF = CrystalTokenType("ELSIF")
    @JvmField val END = CrystalTokenType("END")
    @JvmField val ENSURE = CrystalTokenType("ENSURE")
    @JvmField val ENUM = CrystalTokenType("ENUM")
    @JvmField val EXTEND = CrystalTokenType("EXTEND")
    @JvmField val FALSE = CrystalTokenType("FALSE")
    @JvmField val FOR = CrystalTokenType("FOR")
    @JvmField val FUN = CrystalTokenType("FUN")
    @JvmField val IF = CrystalTokenType("IF")
    @JvmField val IN = CrystalTokenType("IN")
    @JvmField val INCLUDE = CrystalTokenType("INCLUDE")
    @JvmField val INSTANCE_SIZEOF = CrystalTokenType("INSTANCE_SIZEOF")
    @JvmField val IS_A = CrystalTokenType("IS_A")
    @JvmField val LIB = CrystalTokenType("LIB")
    @JvmField val MACRO = CrystalTokenType("MACRO")
    @JvmField val MODULE = CrystalTokenType("MODULE")
    @JvmField val NEXT = CrystalTokenType("NEXT")
    @JvmField val NIL = CrystalTokenType("NIL")
    @JvmField val NIL_QUESTION = CrystalTokenType("NIL_QUESTION")
    @JvmField val OF = CrystalTokenType("OF")
    @JvmField val OFFSETOF = CrystalTokenType("OFFSETOF")
    @JvmField val OUT = CrystalTokenType("OUT")
    @JvmField val POINTEROF = CrystalTokenType("POINTEROF")
    @JvmField val PRIVATE = CrystalTokenType("PRIVATE")
    @JvmField val PROTECTED = CrystalTokenType("PROTECTED")
    @JvmField val REQUIRE = CrystalTokenType("REQUIRE")
    @JvmField val RESCUE = CrystalTokenType("RESCUE")
    @JvmField val RESPONDS_TO = CrystalTokenType("RESPONDS_TO")
    @JvmField val RETURN = CrystalTokenType("RETURN")
    @JvmField val SELECT = CrystalTokenType("SELECT")
    @JvmField val SELF = CrystalTokenType("SELF")
    @JvmField val SIZEOF = CrystalTokenType("SIZEOF")
    @JvmField val STRUCT = CrystalTokenType("STRUCT")
    @JvmField val SUPER = CrystalTokenType("SUPER")
    @JvmField val THEN = CrystalTokenType("THEN")
    @JvmField val TRUE = CrystalTokenType("TRUE")
    @JvmField val TYPEOF = CrystalTokenType("TYPEOF")
    @JvmField val UNINITIALIZED = CrystalTokenType("UNINITIALIZED")
    @JvmField val UNION = CrystalTokenType("UNION")
    @JvmField val UNLESS = CrystalTokenType("UNLESS")
    @JvmField val UNTIL = CrystalTokenType("UNTIL")
    @JvmField val VERBATIM = CrystalTokenType("VERBATIM")
    @JvmField val WHEN = CrystalTokenType("WHEN")
    @JvmField val WHILE = CrystalTokenType("WHILE")
    @JvmField val WITH = CrystalTokenType("WITH")
    @JvmField val YIELD = CrystalTokenType("YIELD")

    // Literals
    @JvmField val INTEGER_LITERAL = CrystalTokenType("INTEGER_LITERAL")
    @JvmField val FLOAT_LITERAL = CrystalTokenType("FLOAT_LITERAL")
    @JvmField val CHAR_LITERAL = CrystalTokenType("CHAR_LITERAL")
    @JvmField val STRING_LITERAL = CrystalTokenType("STRING_LITERAL")
    @JvmField val STRING_INTERPOLATION_BEGIN = CrystalTokenType("STRING_INTERPOLATION_BEGIN")
    @JvmField val STRING_INTERPOLATION_END = CrystalTokenType("STRING_INTERPOLATION_END")
    @JvmField val COMMAND_LITERAL = CrystalTokenType("COMMAND_LITERAL")
    @JvmField val REGEX_LITERAL = CrystalTokenType("REGEX_LITERAL")
    @JvmField val SYMBOL_LITERAL = CrystalTokenType("SYMBOL_LITERAL")
    @JvmField val HEREDOC_START = CrystalTokenType("HEREDOC_START")
    @JvmField val HEREDOC_CONTENT = CrystalTokenType("HEREDOC_CONTENT")
    @JvmField val HEREDOC_END = CrystalTokenType("HEREDOC_END")

    // Identifiers
    @JvmField val IDENTIFIER = CrystalTokenType("IDENTIFIER")
    @JvmField val CONSTANT = CrystalTokenType("CONSTANT")
    @JvmField val INSTANCE_VAR = CrystalTokenType("INSTANCE_VAR")
    @JvmField val CLASS_VAR = CrystalTokenType("CLASS_VAR")
    @JvmField val GLOBAL_VAR = CrystalTokenType("GLOBAL_VAR")

    // Operators
    @JvmField val PLUS = CrystalTokenType("PLUS")
    @JvmField val MINUS = CrystalTokenType("MINUS")
    @JvmField val STAR = CrystalTokenType("STAR")
    @JvmField val SLASH = CrystalTokenType("SLASH")
    @JvmField val DOUBLE_SLASH = CrystalTokenType("DOUBLE_SLASH")
    @JvmField val PERCENT = CrystalTokenType("PERCENT")
    @JvmField val AMPERSAND = CrystalTokenType("AMPERSAND")
    @JvmField val PIPE = CrystalTokenType("PIPE")
    @JvmField val CARET = CrystalTokenType("CARET")
    @JvmField val TILDE = CrystalTokenType("TILDE")
    @JvmField val DOUBLE_STAR = CrystalTokenType("DOUBLE_STAR")
    @JvmField val LSHIFT = CrystalTokenType("LSHIFT")
    @JvmField val RSHIFT = CrystalTokenType("RSHIFT")
    @JvmField val EQ = CrystalTokenType("EQ")
    @JvmField val NEQ = CrystalTokenType("NEQ")
    @JvmField val LT = CrystalTokenType("LT")
    @JvmField val GT = CrystalTokenType("GT")
    @JvmField val LTE = CrystalTokenType("LTE")
    @JvmField val GTE = CrystalTokenType("GTE")
    @JvmField val SPACESHIP = CrystalTokenType("SPACESHIP")
    @JvmField val CASE_EQ = CrystalTokenType("CASE_EQ")
    @JvmField val BANG = CrystalTokenType("BANG")
    @JvmField val AND_AND = CrystalTokenType("AND_AND")
    @JvmField val OR_OR = CrystalTokenType("OR_OR")
    @JvmField val ASSIGN = CrystalTokenType("ASSIGN")
    @JvmField val PLUS_ASSIGN = CrystalTokenType("PLUS_ASSIGN")
    @JvmField val MINUS_ASSIGN = CrystalTokenType("MINUS_ASSIGN")
    @JvmField val STAR_ASSIGN = CrystalTokenType("STAR_ASSIGN")
    @JvmField val SLASH_ASSIGN = CrystalTokenType("SLASH_ASSIGN")
    @JvmField val PERCENT_ASSIGN = CrystalTokenType("PERCENT_ASSIGN")
    @JvmField val AMPERSAND_ASSIGN = CrystalTokenType("AMPERSAND_ASSIGN")
    @JvmField val PIPE_ASSIGN = CrystalTokenType("PIPE_ASSIGN")
    @JvmField val CARET_ASSIGN = CrystalTokenType("CARET_ASSIGN")
    @JvmField val DOUBLE_STAR_ASSIGN = CrystalTokenType("DOUBLE_STAR_ASSIGN")
    @JvmField val LSHIFT_ASSIGN = CrystalTokenType("LSHIFT_ASSIGN")
    @JvmField val RSHIFT_ASSIGN = CrystalTokenType("RSHIFT_ASSIGN")
    @JvmField val OR_OR_ASSIGN = CrystalTokenType("OR_OR_ASSIGN")
    @JvmField val AND_AND_ASSIGN = CrystalTokenType("AND_AND_ASSIGN")
    @JvmField val DOT = CrystalTokenType("DOT")
    @JvmField val DOTDOT = CrystalTokenType("DOTDOT")
    @JvmField val DOTDOTDOT = CrystalTokenType("DOTDOTDOT")
    @JvmField val ARROW = CrystalTokenType("ARROW")
    @JvmField val DOUBLE_ARROW = CrystalTokenType("DOUBLE_ARROW")
    @JvmField val QUESTION = CrystalTokenType("QUESTION")
    @JvmField val COLON = CrystalTokenType("COLON")
    @JvmField val DOUBLE_COLON = CrystalTokenType("DOUBLE_COLON")
    @JvmField val SEMICOLON = CrystalTokenType("SEMICOLON")
    @JvmField val COMMA = CrystalTokenType("COMMA")
    @JvmField val HASH = CrystalTokenType("HASH")
    @JvmField val AT = CrystalTokenType("AT")

    // Delimiters
    @JvmField val LPAREN = CrystalTokenType("LPAREN")
    @JvmField val RPAREN = CrystalTokenType("RPAREN")
    @JvmField val LBRACKET = CrystalTokenType("LBRACKET")
    @JvmField val RBRACKET = CrystalTokenType("RBRACKET")
    @JvmField val LBRACE = CrystalTokenType("LBRACE")
    @JvmField val RBRACE = CrystalTokenType("RBRACE")

    // Comments & Whitespace
    @JvmField val LINE_COMMENT = CrystalTokenType("LINE_COMMENT")
    @JvmField val WHITE_SPACE = com.intellij.psi.TokenType.WHITE_SPACE
    @JvmField val BAD_CHARACTER = com.intellij.psi.TokenType.BAD_CHARACTER
    @JvmField val NEWLINE = CrystalTokenType("NEWLINE")

    // Token sets
    @JvmField val KEYWORDS = TokenSet.create(
        ABSTRACT, ALIAS, ANNOTATION, AS, AS_QUESTION, ASM,
        BEGIN, BREAK, CASE, CLASS, DEF, DO,
        ELSE, ELSIF, END, ENSURE, ENUM, EXTEND,
        FALSE, FOR, FUN, IF, IN, INCLUDE, INSTANCE_SIZEOF, IS_A,
        LIB, MACRO, MODULE, NEXT, NIL, NIL_QUESTION,
        OF, OFFSETOF, OUT, POINTEROF, PRIVATE, PROTECTED,
        REQUIRE, RESCUE, RESPONDS_TO, RETURN,
        SELECT, SELF, SIZEOF, STRUCT, SUPER,
        THEN, TRUE, TYPEOF, UNINITIALIZED, UNION, UNLESS, UNTIL,
        VERBATIM, WHEN, WHILE, WITH, YIELD
    )

    @JvmField val COMMENTS = TokenSet.create(LINE_COMMENT)

    @JvmField val STRINGS = TokenSet.create(
        STRING_LITERAL, CHAR_LITERAL, COMMAND_LITERAL,
        HEREDOC_CONTENT, HEREDOC_START, HEREDOC_END
    )

    @JvmField val NUMBERS = TokenSet.create(INTEGER_LITERAL, FLOAT_LITERAL)

    @JvmField val OPERATORS = TokenSet.create(
        PLUS, MINUS, STAR, SLASH, DOUBLE_SLASH, PERCENT,
        AMPERSAND, PIPE, CARET, TILDE, DOUBLE_STAR, LSHIFT, RSHIFT,
        EQ, NEQ, LT, GT, LTE, GTE, SPACESHIP, CASE_EQ, BANG,
        AND_AND, OR_OR, ASSIGN,
        PLUS_ASSIGN, MINUS_ASSIGN, STAR_ASSIGN, SLASH_ASSIGN,
        PERCENT_ASSIGN, AMPERSAND_ASSIGN, PIPE_ASSIGN, CARET_ASSIGN,
        DOUBLE_STAR_ASSIGN, LSHIFT_ASSIGN, RSHIFT_ASSIGN,
        OR_OR_ASSIGN, AND_AND_ASSIGN,
        DOT, DOTDOT, DOTDOTDOT, ARROW, DOUBLE_ARROW,
        QUESTION, COLON, DOUBLE_COLON
    )
}
