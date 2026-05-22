package de.magynhard.crystal.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import de.magynhard.crystal.lexer.CrystalTokenTypes;

%%

%class CrystalLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

%{
  private int interpolationDepth = 0;
  private int percentDepth = 0;
  private char percentOpenChar = 0;
  private char percentCloseChar = 0;
  private IElementType percentTokenType = null;
  private String heredocId = "";
  private boolean heredocIndented = false;

  private static char closingChar(char open) {
    switch (open) {
      case '(': return ')';
      case '[': return ']';
      case '{': return '}';
      case '<': return '>';
      case '|': return '|';
      default: return open;
    }
  }
%}

// Macros
DIGIT = [0-9]
HEX_DIGIT = [0-9a-fA-F]
OCT_DIGIT = [0-7]
BIN_DIGIT = [01]
ID_CHAR = [a-zA-Z0-9_]

WHITE_SPACE = [ \t\f]+
NEWLINE = \r\n | \r | \n
LINE_COMMENT = "#" [^\r\n]*

// Numbers
DEC_INT = {DIGIT} ({DIGIT} | "_")*
HEX_INT = "0x" ({HEX_DIGIT} | "_")+
OCT_INT = "0o" ({OCT_DIGIT} | "_")+
BIN_INT = "0b" ({BIN_DIGIT} | "_")+
INT_SUFFIX = ("_"? ("i" | "u") ("8" | "16" | "32" | "64" | "128"))?
INTEGER = ({DEC_INT} | {HEX_INT} | {OCT_INT} | {BIN_INT}) {INT_SUFFIX}

FLOAT_SUFFIX = ("_"? "f" ("32" | "64"))?

// Identifiers
IDENTIFIER = [a-z_] {ID_CHAR}* [?!]?
CONSTANT = [A-Z] {ID_CHAR}*
INSTANCE_VAR = "@" {IDENTIFIER}
CLASS_VAR = "@@" {IDENTIFIER}
GLOBAL_VAR = "$" ({IDENTIFIER} | {DIGIT}+ | "~")

// Character literal escape sequences
CHAR_ESCAPE = "\\" ( [abefnrtv\\'0] | "u" "{" {HEX_DIGIT}+ "}" | "u" {HEX_DIGIT}{4} | {OCT_DIGIT}{1,3} )
CHAR_LITERAL = "'" ( [^'\\] | {CHAR_ESCAPE} ) "'"

// Symbol
SYMBOL = ":" ( {IDENTIFIER} | {CONSTANT} | "\"" [^\"]* "\"" )

%state STRING INTERPOLATION PERCENT_LITERAL HEREDOC_BODY HEREDOC_START_LINE

%%

<YYINITIAL> {
  // Whitespace and comments
  {WHITE_SPACE}        { return CrystalTokenTypes.WHITE_SPACE; }
  {NEWLINE}            { return CrystalTokenTypes.NEWLINE; }
  {LINE_COMMENT}       { return CrystalTokenTypes.LINE_COMMENT; }

  // Keywords (longest match first for keywords with ? suffix)
  "abstract"           { return CrystalTokenTypes.ABSTRACT; }
  "alias"              { return CrystalTokenTypes.ALIAS; }
  "annotation"         { return CrystalTokenTypes.ANNOTATION; }
  "as?"                { return CrystalTokenTypes.AS_QUESTION; }
  "as"                 { return CrystalTokenTypes.AS; }
  "asm"                { return CrystalTokenTypes.ASM; }
  "begin"              { return CrystalTokenTypes.BEGIN; }
  "break"              { return CrystalTokenTypes.BREAK; }
  "case"               { return CrystalTokenTypes.CASE; }
  "class"              { return CrystalTokenTypes.CLASS; }
  "def"                { return CrystalTokenTypes.DEF; }
  "do"                 { return CrystalTokenTypes.DO; }
  "else"               { return CrystalTokenTypes.ELSE; }
  "elsif"              { return CrystalTokenTypes.ELSIF; }
  "end"                { return CrystalTokenTypes.END; }
  "ensure"             { return CrystalTokenTypes.ENSURE; }
  "enum"               { return CrystalTokenTypes.ENUM; }
  "extend"             { return CrystalTokenTypes.EXTEND; }
  "false"              { return CrystalTokenTypes.FALSE; }
  "for"                { return CrystalTokenTypes.FOR; }
  "fun"                { return CrystalTokenTypes.FUN; }
  "if"                 { return CrystalTokenTypes.IF; }
  "in"                 { return CrystalTokenTypes.IN; }
  "include"            { return CrystalTokenTypes.INCLUDE; }
  "instance_sizeof"    { return CrystalTokenTypes.INSTANCE_SIZEOF; }
  "is_a?"              { return CrystalTokenTypes.IS_A; }
  "lib"                { return CrystalTokenTypes.LIB; }
  "macro"              { return CrystalTokenTypes.MACRO; }
  "module"             { return CrystalTokenTypes.MODULE; }
  "next"               { return CrystalTokenTypes.NEXT; }
  "nil?"               { return CrystalTokenTypes.NIL_QUESTION; }
  "nil"                { return CrystalTokenTypes.NIL; }
  "of"                 { return CrystalTokenTypes.OF; }
  "offsetof"           { return CrystalTokenTypes.OFFSETOF; }
  "out"                { return CrystalTokenTypes.OUT; }
  "pointerof"          { return CrystalTokenTypes.POINTEROF; }
  "private"            { return CrystalTokenTypes.PRIVATE; }
  "protected"          { return CrystalTokenTypes.PROTECTED; }
  "require"            { return CrystalTokenTypes.REQUIRE; }
  "rescue"             { return CrystalTokenTypes.RESCUE; }
  "responds_to?"       { return CrystalTokenTypes.RESPONDS_TO; }
  "return"             { return CrystalTokenTypes.RETURN; }
  "select"             { return CrystalTokenTypes.SELECT; }
  "self"               { return CrystalTokenTypes.SELF; }
  "sizeof"             { return CrystalTokenTypes.SIZEOF; }
  "struct"             { return CrystalTokenTypes.STRUCT; }
  "super"              { return CrystalTokenTypes.SUPER; }
  "then"               { return CrystalTokenTypes.THEN; }
  "true"               { return CrystalTokenTypes.TRUE; }
  "typeof"             { return CrystalTokenTypes.TYPEOF; }
  "uninitialized"      { return CrystalTokenTypes.UNINITIALIZED; }
  "union"              { return CrystalTokenTypes.UNION; }
  "unless"             { return CrystalTokenTypes.UNLESS; }
  "until"              { return CrystalTokenTypes.UNTIL; }
  "verbatim"           { return CrystalTokenTypes.VERBATIM; }
  "when"               { return CrystalTokenTypes.WHEN; }
  "while"              { return CrystalTokenTypes.WHILE; }
  "with"               { return CrystalTokenTypes.WITH; }
  "yield"              { return CrystalTokenTypes.YIELD; }

  // Literals
  {CHAR_LITERAL}       { return CrystalTokenTypes.CHAR_LITERAL; }
  {SYMBOL}             { return CrystalTokenTypes.SYMBOL_LITERAL; }

  // Numbers (float before int since float is more specific with dot)
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return CrystalTokenTypes.FLOAT_LITERAL; }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return CrystalTokenTypes.FLOAT_LITERAL; }
  {DEC_INT} "_f" ("32" | "64")                                                    { return CrystalTokenTypes.FLOAT_LITERAL; }
  {INTEGER}            { return CrystalTokenTypes.INTEGER_LITERAL; }

  // Heredoc start: <<-IDENTIFIER or <<-'IDENTIFIER'
  "<<-'" [A-Za-z_][A-Za-z0-9_]* "'"  {
                         String text = yytext().toString();
                         heredocId = text.substring(4, text.length() - 1);
                         heredocIndented = true;
                         yybegin(HEREDOC_START_LINE);
                         return CrystalTokenTypes.HEREDOC_START;
                       }
  "<<-" [A-Za-z_][A-Za-z0-9_]*       {
                         String text = yytext().toString();
                         heredocId = text.substring(3);
                         heredocIndented = true;
                         yybegin(HEREDOC_START_LINE);
                         return CrystalTokenTypes.HEREDOC_START;
                       }

  // Percent literals: %w(...), %i(...), %(...), %[...], %{...}, %<...>, %|...|
  "%w" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTokenTypes.STRING_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTokenTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%i" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTokenTypes.SYMBOL_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTokenTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTokenTypes.STRING_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTokenTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%Q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTokenTypes.STRING_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTokenTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%r" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTokenTypes.REGEX_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTokenTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%" [\(\[\{<|]      {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTokenTypes.STRING_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTokenTypes.PERCENT_LITERAL_BEGIN;
                       }

  // String start
  \"                   { yybegin(STRING); return CrystalTokenTypes.STRING_LITERAL; }

  // Command literal
  "`" [^`]* "`"        { return CrystalTokenTypes.COMMAND_LITERAL; }

  // Regex literal (simple heuristic - at least one char between slashes)
  "/" [^/\r\n]+ "/" [imx]* { return CrystalTokenTypes.REGEX_LITERAL; }

  // Multi-character operators (longest first)
  "<=>"                { return CrystalTokenTypes.SPACESHIP; }
  "==="                { return CrystalTokenTypes.CASE_EQ; }
  "**="                { return CrystalTokenTypes.DOUBLE_STAR_ASSIGN; }
  "<<="                { return CrystalTokenTypes.LSHIFT_ASSIGN; }
  ">>="                { return CrystalTokenTypes.RSHIFT_ASSIGN; }
  "||="                { return CrystalTokenTypes.OR_OR_ASSIGN; }
  "&&="                { return CrystalTokenTypes.AND_AND_ASSIGN; }
  "..."                { return CrystalTokenTypes.DOTDOTDOT; }
  "**"                 { return CrystalTokenTypes.DOUBLE_STAR; }
  "//"                 { return CrystalTokenTypes.DOUBLE_SLASH; }
  "<<"                 { return CrystalTokenTypes.LSHIFT; }
  ">>"                 { return CrystalTokenTypes.RSHIFT; }
  "=="                 { return CrystalTokenTypes.EQ; }
  "!="                 { return CrystalTokenTypes.NEQ; }
  "<="                 { return CrystalTokenTypes.LTE; }
  ">="                 { return CrystalTokenTypes.GTE; }
  "&&"                 { return CrystalTokenTypes.AND_AND; }
  "||"                 { return CrystalTokenTypes.OR_OR; }
  "+="                 { return CrystalTokenTypes.PLUS_ASSIGN; }
  "-="                 { return CrystalTokenTypes.MINUS_ASSIGN; }
  "*="                 { return CrystalTokenTypes.STAR_ASSIGN; }
  "/="                 { return CrystalTokenTypes.SLASH_ASSIGN; }
  "%="                 { return CrystalTokenTypes.PERCENT_ASSIGN; }
  "&="                 { return CrystalTokenTypes.AMPERSAND_ASSIGN; }
  "|="                 { return CrystalTokenTypes.PIPE_ASSIGN; }
  "^="                 { return CrystalTokenTypes.CARET_ASSIGN; }
  ".."                 { return CrystalTokenTypes.DOTDOT; }
  "->"                 { return CrystalTokenTypes.ARROW; }
  "=>"                 { return CrystalTokenTypes.DOUBLE_ARROW; }
  "::"                 { return CrystalTokenTypes.DOUBLE_COLON; }

  // Single character operators
  "+"                  { return CrystalTokenTypes.PLUS; }
  "-"                  { return CrystalTokenTypes.MINUS; }
  "*"                  { return CrystalTokenTypes.STAR; }
  "/"                  { return CrystalTokenTypes.SLASH; }
  "%"                  { return CrystalTokenTypes.PERCENT; }
  "&"                  { return CrystalTokenTypes.AMPERSAND; }
  "|"                  { return CrystalTokenTypes.PIPE; }
  "^"                  { return CrystalTokenTypes.CARET; }
  "~"                  { return CrystalTokenTypes.TILDE; }
  "<"                  { return CrystalTokenTypes.LT; }
  ">"                  { return CrystalTokenTypes.GT; }
  "!"                  { return CrystalTokenTypes.BANG; }
  "="                  { return CrystalTokenTypes.ASSIGN; }
  "."                  { return CrystalTokenTypes.DOT; }
  "?"                  { return CrystalTokenTypes.QUESTION; }
  ":"                  { return CrystalTokenTypes.COLON; }
  ";"                  { return CrystalTokenTypes.SEMICOLON; }
  ","                  { return CrystalTokenTypes.COMMA; }
  "@"                  { return CrystalTokenTypes.AT; }

  // Delimiters
  "("                  { return CrystalTokenTypes.LPAREN; }
  ")"                  { return CrystalTokenTypes.RPAREN; }
  "["                  { return CrystalTokenTypes.LBRACKET; }
  "]"                  { return CrystalTokenTypes.RBRACKET; }
  "{"                  { return CrystalTokenTypes.LBRACE; }
  "}"                  { return CrystalTokenTypes.RBRACE; }

  // Identifiers (after keywords to ensure keywords take priority)
  {CLASS_VAR}          { return CrystalTokenTypes.CLASS_VAR; }
  {INSTANCE_VAR}       { return CrystalTokenTypes.INSTANCE_VAR; }
  {GLOBAL_VAR}         { return CrystalTokenTypes.GLOBAL_VAR; }
  {CONSTANT}           { return CrystalTokenTypes.CONSTANT; }
  {IDENTIFIER}         { return CrystalTokenTypes.IDENTIFIER; }
}

<STRING> {
  \"                   { yybegin(YYINITIAL); return CrystalTokenTypes.STRING_LITERAL; }
  "#{"                 { interpolationDepth++; yybegin(INTERPOLATION); return CrystalTokenTypes.STRING_INTERPOLATION_BEGIN; }
  "\\" .               { return CrystalTokenTypes.STRING_LITERAL; }
  [^\"\#\\]+           { return CrystalTokenTypes.STRING_LITERAL; }
  "#"                  { return CrystalTokenTypes.STRING_LITERAL; }
}

<INTERPOLATION> {
  "{"                  { interpolationDepth++; return CrystalTokenTypes.LBRACE; }
  "}"                  { interpolationDepth--;
                         if (interpolationDepth == 0) {
                           yybegin(STRING);
                           return CrystalTokenTypes.STRING_INTERPOLATION_END;
                         }
                         return CrystalTokenTypes.RBRACE;
                       }
  // All normal tokens are valid inside interpolation
  {WHITE_SPACE}        { return CrystalTokenTypes.WHITE_SPACE; }
  {NEWLINE}            { return CrystalTokenTypes.NEWLINE; }
  {LINE_COMMENT}       { return CrystalTokenTypes.LINE_COMMENT; }
  {IDENTIFIER}         { return CrystalTokenTypes.IDENTIFIER; }
  {CONSTANT}           { return CrystalTokenTypes.CONSTANT; }
  {INSTANCE_VAR}       { return CrystalTokenTypes.INSTANCE_VAR; }
  {CLASS_VAR}          { return CrystalTokenTypes.CLASS_VAR; }
  {DEC_INT}            { return CrystalTokenTypes.INTEGER_LITERAL; }
  \"                   { yybegin(STRING); return CrystalTokenTypes.STRING_LITERAL; }
  "."                  { return CrystalTokenTypes.DOT; }
  "("                  { return CrystalTokenTypes.LPAREN; }
  ")"                  { return CrystalTokenTypes.RPAREN; }
  "["                  { return CrystalTokenTypes.LBRACKET; }
  "]"                  { return CrystalTokenTypes.RBRACKET; }
  "+"                  { return CrystalTokenTypes.PLUS; }
  "-"                  { return CrystalTokenTypes.MINUS; }
  "*"                  { return CrystalTokenTypes.STAR; }
  ","                  { return CrystalTokenTypes.COMMA; }
  [^]                  { return CrystalTokenTypes.BAD_CHARACTER; }
}

<PERCENT_LITERAL> {
  // Handle nested opening delimiters (except | which doesn't nest)
  .                    {
                         char c = yycharat(0);
                         if (c == percentCloseChar) {
                           percentDepth--;
                           if (percentDepth == 0) {
                             yybegin(YYINITIAL);
                             return CrystalTokenTypes.PERCENT_LITERAL_END;
                           }
                           return percentTokenType;
                         } else if (c == percentOpenChar && percentOpenChar != '|') {
                           percentDepth++;
                           return percentTokenType;
                         }
                         return percentTokenType;
                       }
  "\\" .               { return percentTokenType; }
  {NEWLINE}            { return percentTokenType; }
}

<HEREDOC_START_LINE> {
  // Consume the rest of the line after <<-ID (could have more code on same line)
  {NEWLINE}            { yybegin(HEREDOC_BODY); return CrystalTokenTypes.NEWLINE; }
  .+                   { return CrystalTokenTypes.HEREDOC_START; }
}

<HEREDOC_BODY> {
  // Check for end marker (with optional leading whitespace if indented)
  ^[ \t]* {CONSTANT}  {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           yybegin(YYINITIAL);
                           return CrystalTokenTypes.HEREDOC_END;
                         }
                         return CrystalTokenTypes.HEREDOC_CONTENT;
                       }
  ^[ \t]* {IDENTIFIER} {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           yybegin(YYINITIAL);
                           return CrystalTokenTypes.HEREDOC_END;
                         }
                         return CrystalTokenTypes.HEREDOC_CONTENT;
                       }
  [^\r\n]+             { return CrystalTokenTypes.HEREDOC_CONTENT; }
  {NEWLINE}            { return CrystalTokenTypes.HEREDOC_CONTENT; }
}

[^]                    { return CrystalTokenTypes.BAD_CHARACTER; }
