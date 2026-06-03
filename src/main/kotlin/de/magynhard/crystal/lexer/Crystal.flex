package de.magynhard.crystal.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import de.magynhard.crystal.psi.CrystalTypes;
import com.intellij.psi.TokenType;

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
  private boolean heredocRaw = false;

  // State stack for nested string interpolation
  private final java.util.ArrayDeque<Integer> stateStack = new java.util.ArrayDeque<>();
  private final java.util.ArrayDeque<Integer> depthStack = new java.util.ArrayDeque<>();

  // Regex disambiguation: regex literals can only start in operator position
  private boolean isRegexAllowed() {
    // Check the character immediately before the current token (skip whitespace already consumed)
    int pos = zzStartRead - 1;
    while (pos >= 0 && (zzBuffer.charAt(pos) == ' ' || zzBuffer.charAt(pos) == '\t')) pos--;
    if (pos < 0) return true; // start of file
    char c = zzBuffer.charAt(pos);
    // After identifiers, constants, numbers, ), ] — it's division, not regex
    if (Character.isLetterOrDigit(c) || c == '_' || c == ')' || c == ']' || c == '"' || c == '\'') return false;
    return true;
  }

  // Macro body state tracking
  private boolean macroHeaderSeen = false;
  private int macroBodyDepth = 0;
  private int macroNestingLevel = 0;
  private StringBuilder macroBodyBuffer = new StringBuilder();

  private void pushState(int newState) {
    stateStack.push(zzLexicalState);
    yybegin(newState);
  }

  private void popState() {
    if (!stateStack.isEmpty()) {
      yybegin(stateStack.pop());
    } else {
      yybegin(YYINITIAL);
    }
  }

  public int getInterpolationDepth() { return interpolationDepth; }
  public void setInterpolationDepth(int depth) { this.interpolationDepth = depth; }

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

%state STRING INTERPOLATION PERCENT_LITERAL HEREDOC_BODY HEREDOC_START_LINE MACRO_BODY MACRO_INTERPOLATION MACRO_CONTROL

%%

<YYINITIAL> {
  // Whitespace and comments
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  {NEWLINE}            { if (macroHeaderSeen) { macroHeaderSeen = false; macroBodyDepth = 0; yybegin(MACRO_BODY); } return CrystalTypes.NEWLINE; }
  {LINE_COMMENT}       { return CrystalTypes.LINE_COMMENT; }

  // Keywords (longest match first for keywords with ? suffix)
  "abstract"           { return CrystalTypes.ABSTRACT; }
  "alias"              { return CrystalTypes.ALIAS; }
  "annotation"         { return CrystalTypes.ANNOTATION; }
  "as?"                { return CrystalTypes.AS_QUESTION; }
  "as"                 { return CrystalTypes.AS; }
  "asm"                { return CrystalTypes.ASM; }
  "begin"              { return CrystalTypes.BEGIN; }
  "break"              { return CrystalTypes.BREAK; }
  "case"               { return CrystalTypes.CASE; }
  "class"              { return CrystalTypes.CLASS; }
  "def"                { return CrystalTypes.DEF; }
  "do"                 { return CrystalTypes.DO; }
  "else"               { return CrystalTypes.ELSE; }
  "elsif"              { return CrystalTypes.ELSIF; }
  "end"                { return CrystalTypes.END; }
  "ensure"             { return CrystalTypes.ENSURE; }
  "enum"               { return CrystalTypes.ENUM; }
  "extend"             { return CrystalTypes.EXTEND; }
  "false"              { return CrystalTypes.FALSE; }
  "for"                { return CrystalTypes.FOR; }
  "fun"                { return CrystalTypes.FUN; }
  "if"                 { return CrystalTypes.IF; }
  "in"                 { return CrystalTypes.IN; }
  "include"            { return CrystalTypes.INCLUDE; }
  "instance_sizeof"    { return CrystalTypes.INSTANCE_SIZEOF; }
  "is_a?"              { return CrystalTypes.IS_A; }
  "lib"                { return CrystalTypes.LIB; }
  "macro"              { macroHeaderSeen = true; return CrystalTypes.MACRO; }
  "module"             { return CrystalTypes.MODULE; }
  "next"               { return CrystalTypes.NEXT; }
  "nil?"               { return CrystalTypes.NIL_QUESTION; }
  "nil"                { return CrystalTypes.NIL; }
  "of"                 { return CrystalTypes.OF; }
  "offsetof"           { return CrystalTypes.OFFSETOF; }
  "out"                { return CrystalTypes.OUT; }
  "pointerof"          { return CrystalTypes.POINTEROF; }
  "private"            { return CrystalTypes.PRIVATE; }
  "protected"          { return CrystalTypes.PROTECTED; }
  "forall"             { return CrystalTypes.FORALL; }
  "require"            { return CrystalTypes.REQUIRE; }
  "rescue"             { return CrystalTypes.RESCUE; }
  "responds_to?"       { return CrystalTypes.RESPONDS_TO; }
  "return"             { return CrystalTypes.RETURN; }
  "select"             { return CrystalTypes.SELECT; }
  "self"               { return CrystalTypes.SELF; }
  "sizeof"             { return CrystalTypes.SIZEOF; }
  "struct"             { return CrystalTypes.STRUCT; }
  "super"              { return CrystalTypes.SUPER; }
  "then"               { return CrystalTypes.THEN; }
  "true"               { return CrystalTypes.TRUE; }
  "typeof"             { return CrystalTypes.TYPEOF; }
  "uninitialized"      { return CrystalTypes.UNINITIALIZED; }
  "union"              { return CrystalTypes.UNION; }
  "unless"             { return CrystalTypes.UNLESS; }
  "until"              { return CrystalTypes.UNTIL; }
  "verbatim"           { return CrystalTypes.VERBATIM; }
  "when"               { return CrystalTypes.WHEN; }
  "while"              { return CrystalTypes.WHILE; }
  "with"               { return CrystalTypes.WITH; }
  "yield"              { return CrystalTypes.YIELD; }

  // Literals
  {CHAR_LITERAL}       { return CrystalTypes.CHAR_LITERAL; }
  {SYMBOL}             { return CrystalTypes.SYMBOL_LITERAL; }

  // Numbers (float before int since float is more specific with dot)
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} "_f" ("32" | "64")                                                    { return CrystalTypes.FLOAT_LITERAL; }
  {INTEGER}            { return CrystalTypes.INTEGER_LITERAL; }

  // Heredoc start: <<-IDENTIFIER or <<-'IDENTIFIER'
  "<<-'" [A-Za-z_][A-Za-z0-9_]* "'"  {
                         String text = yytext().toString();
                         heredocId = text.substring(4, text.length() - 1);
                         heredocIndented = true;
                         heredocRaw = true;
                         yybegin(HEREDOC_START_LINE);
                         return CrystalTypes.HEREDOC_START;
                       }
  "<<-" [A-Za-z_][A-Za-z0-9_]*       {
                         String text = yytext().toString();
                         heredocId = text.substring(3);
                         heredocIndented = true;
                         heredocRaw = false;
                         yybegin(HEREDOC_START_LINE);
                         return CrystalTypes.HEREDOC_START;
                       }

  // Percent literals: %w(...), %i(...), %(...), %[...], %{...}, %<...>, %|...|
  "%w" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%i" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.SYMBOL_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%Q" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%r" [\(\[\{<|]     {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.REGEX_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTypes.PERCENT_LITERAL_BEGIN;
                       }
  "%" [\(\[\{<|]      {
                         char c = yycharat(yylength() - 1);
                         percentOpenChar = c;
                         percentCloseChar = closingChar(c);
                         percentDepth = 1;
                         percentTokenType = CrystalTypes.STRING_LITERAL;
                         yybegin(PERCENT_LITERAL);
                         return CrystalTypes.PERCENT_LITERAL_BEGIN;
                       }

  // String start
  \"                   { pushState(STRING); return CrystalTypes.STRING_LITERAL; }

  // Command literal
  "`" [^`]* "`"        { return CrystalTypes.COMMAND_LITERAL; }

  // Regex literal (only in operator position — not after identifiers, constants, literals, ) or ])
  "/" [^/\r\n]+ "/" [imx]* { if (isRegexAllowed()) { return CrystalTypes.REGEX_LITERAL; }
                             // Not regex context: put back everything except first /
                             yypushback(yylength() - 1); return CrystalTypes.SLASH; }

  // Multi-character operators (longest first)
  "<=>"                { return CrystalTypes.SPACESHIP; }
  "==="                { return CrystalTypes.CASE_EQ; }
  "**="                { return CrystalTypes.DOUBLE_STAR_ASSIGN; }
  "//="                { return CrystalTypes.DOUBLE_SLASH_ASSIGN; }
  "<<="                { return CrystalTypes.LSHIFT_ASSIGN; }
  ">>="                { return CrystalTypes.RSHIFT_ASSIGN; }
  "||="                { return CrystalTypes.OR_OR_ASSIGN; }
  "&&="                { return CrystalTypes.AND_AND_ASSIGN; }
  "..."                { return CrystalTypes.DOTDOTDOT; }
  "&**="               { return CrystalTypes.WRAP_DOUBLE_STAR_ASSIGN; }
  "&**"                { return CrystalTypes.WRAP_DOUBLE_STAR; }
  "&*="                { return CrystalTypes.WRAP_STAR_ASSIGN; }
  "&*"                 { return CrystalTypes.WRAP_STAR; }
  "&+="                { return CrystalTypes.WRAP_PLUS_ASSIGN; }
  "&+"                 { return CrystalTypes.WRAP_PLUS; }
  "&-="                { return CrystalTypes.WRAP_MINUS_ASSIGN; }
  "&-"                 { return CrystalTypes.WRAP_MINUS; }
  "**"                 { return CrystalTypes.DOUBLE_STAR; }
  "//"                 { return CrystalTypes.DOUBLE_SLASH; }
  "<<"                 { return CrystalTypes.LSHIFT; }
  ">>"                 { return CrystalTypes.RSHIFT; }
  "=="                 { return CrystalTypes.EQ; }
  "!="                 { return CrystalTypes.NEQ; }
  "<="                 { return CrystalTypes.LTE; }
  ">="                 { return CrystalTypes.GTE; }
  "&&"                 { return CrystalTypes.AND_AND; }
  "||"                 { return CrystalTypes.OR_OR; }
  "+="                 { return CrystalTypes.PLUS_ASSIGN; }
  "-="                 { return CrystalTypes.MINUS_ASSIGN; }
  "*="                 { return CrystalTypes.STAR_ASSIGN; }
  "/="                 { return CrystalTypes.SLASH_ASSIGN; }
  "%="                 { return CrystalTypes.PERCENT_ASSIGN; }
  "&="                 { return CrystalTypes.AMPERSAND_ASSIGN; }
  "|="                 { return CrystalTypes.PIPE_ASSIGN; }
  "^="                 { return CrystalTypes.CARET_ASSIGN; }
  ".."                 { return CrystalTypes.DOTDOT; }
  "->"                 { return CrystalTypes.ARROW; }
  "=>"                 { return CrystalTypes.DOUBLE_ARROW; }
  "::"                 { return CrystalTypes.DOUBLE_COLON; }

  // Single character operators
  "+"                  { return CrystalTypes.PLUS; }
  "-"                  { return CrystalTypes.MINUS; }
  "*"                  { return CrystalTypes.STAR; }
  "/"                  { return CrystalTypes.SLASH; }
  "%"                  { return CrystalTypes.PERCENT; }
  "&"                  { return CrystalTypes.AMPERSAND; }
  "|"                  { return CrystalTypes.PIPE; }
  "^"                  { return CrystalTypes.CARET; }
  "~"                  { return CrystalTypes.TILDE; }
  "<"                  { return CrystalTypes.LT; }
  ">"                  { return CrystalTypes.GT; }
  "!"                  { return CrystalTypes.BANG; }
  "=~"                 { return CrystalTypes.MATCH_OP; }
  "="                  { return CrystalTypes.ASSIGN; }
  "."                  { return CrystalTypes.DOT; }
  "?"                  { return CrystalTypes.QUESTION; }
  ":"                  { return CrystalTypes.COLON; }
  ";"                  { return CrystalTypes.SEMICOLON; }
  ","                  { return CrystalTypes.COMMA; }
  "@"                  { return CrystalTypes.AT; }

  // Delimiters
  "("                  { return CrystalTypes.LPAREN; }
  ")"                  { return CrystalTypes.RPAREN; }
  "["                  { return CrystalTypes.LBRACKET; }
  "]"                  { return CrystalTypes.RBRACKET; }
  "{"                  { return CrystalTypes.LBRACE; }
  "}"                  { return CrystalTypes.RBRACE; }

  // Identifiers (after keywords to ensure keywords take priority)
  {CLASS_VAR}          { return CrystalTypes.CLASS_VAR; }
  {INSTANCE_VAR}       { return CrystalTypes.INSTANCE_VAR; }
  {GLOBAL_VAR}         { return CrystalTypes.GLOBAL_VAR; }
  {CONSTANT}           { return CrystalTypes.CONSTANT; }
  {IDENTIFIER}         { return CrystalTypes.IDENTIFIER; }
}

<STRING> {
  \"                   { popState(); return CrystalTypes.STRING_LITERAL; }
  "#{"                 { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; }
  "\\" .               { return CrystalTypes.STRING_LITERAL; }
  [^\"\#\\]+           { return CrystalTypes.STRING_LITERAL; }
  "#"                  { return CrystalTypes.STRING_LITERAL; }
}

<INTERPOLATION> {
  "{"                  { interpolationDepth++; return CrystalTypes.LBRACE; }
  "}"                  { interpolationDepth--;
                         if (interpolationDepth == 0) {
                           interpolationDepth = depthStack.isEmpty() ? 0 : depthStack.pop();
                           popState();
                           return CrystalTypes.STRING_INTERPOLATION_END;
                         }
                         return CrystalTypes.RBRACE;
                       }
  // All normal tokens are valid inside interpolation
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  {NEWLINE}            { return CrystalTypes.NEWLINE; }
  {LINE_COMMENT}       { return CrystalTypes.LINE_COMMENT; }
  {SYMBOL}             { return CrystalTypes.SYMBOL_LITERAL; }
  {IDENTIFIER}         { return CrystalTypes.IDENTIFIER; }
  {CONSTANT}           { return CrystalTypes.CONSTANT; }
  {INSTANCE_VAR}       { return CrystalTypes.INSTANCE_VAR; }
  {CLASS_VAR}          { return CrystalTypes.CLASS_VAR; }
  {DEC_INT}            { return CrystalTypes.INTEGER_LITERAL; }
  \"                   { pushState(STRING); return CrystalTypes.STRING_LITERAL; }
  "."                  { return CrystalTypes.DOT; }
  "("                  { return CrystalTypes.LPAREN; }
  ")"                  { return CrystalTypes.RPAREN; }
  "["                  { return CrystalTypes.LBRACKET; }
  "]"                  { return CrystalTypes.RBRACKET; }
  ":"                  { return CrystalTypes.COLON; }
  "=="                 { return CrystalTypes.EQ; }
  "!="                 { return CrystalTypes.NEQ; }
  "<="                 { return CrystalTypes.LTE; }
  ">="                 { return CrystalTypes.GTE; }
  "&&"                 { return CrystalTypes.AND_AND; }
  "||"                 { return CrystalTypes.OR_OR; }
  "=>"                 { return CrystalTypes.DOUBLE_ARROW; }
  "+"                  { return CrystalTypes.PLUS; }
  "-"                  { return CrystalTypes.MINUS; }
  "*"                  { return CrystalTypes.STAR; }
  "/"                  { return CrystalTypes.SLASH; }
  "%"                  { return CrystalTypes.PERCENT; }
  "<"                  { return CrystalTypes.LT; }
  ">"                  { return CrystalTypes.GT; }
  "&"                  { return CrystalTypes.AMPERSAND; }
  "|"                  { return CrystalTypes.PIPE; }
  "^"                  { return CrystalTypes.CARET; }
  "~"                  { return CrystalTypes.TILDE; }
  "!"                  { return CrystalTypes.BANG; }
  "?"                  { return CrystalTypes.QUESTION; }
  "="                  { return CrystalTypes.ASSIGN; }
  ","                  { return CrystalTypes.COMMA; }
  [^]                  { return TokenType.BAD_CHARACTER; }
}

<PERCENT_LITERAL> {
  // Handle nested opening delimiters (except | which doesn't nest)
  .                    {
                         char c = yycharat(0);
                         if (c == percentCloseChar) {
                           percentDepth--;
                           if (percentDepth == 0) {
                             yybegin(YYINITIAL);
                             return CrystalTypes.PERCENT_LITERAL_END;
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
  {NEWLINE}            { yybegin(HEREDOC_BODY); return CrystalTypes.HEREDOC_CONTENT; }
  .+                   { return CrystalTypes.HEREDOC_START; }
}

<HEREDOC_BODY> {
  // Check for end marker (with optional leading whitespace if indented)
  ^[ \t]* {CONSTANT}  {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           yybegin(YYINITIAL);
                           return CrystalTypes.HEREDOC_END;
                         }
                         return CrystalTypes.HEREDOC_CONTENT;
                       }
  ^[ \t]* {IDENTIFIER} {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           yybegin(YYINITIAL);
                           return CrystalTypes.HEREDOC_END;
                         }
                         return CrystalTypes.HEREDOC_CONTENT;
                       }
  [^\r\n\#]+           { return CrystalTypes.HEREDOC_CONTENT; }
  "#{"                 { if (!heredocRaw) { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; } return CrystalTypes.HEREDOC_CONTENT; }
  "#"                  { return CrystalTypes.HEREDOC_CONTENT; }
  {NEWLINE}            { return CrystalTypes.HEREDOC_CONTENT; }
}

<MACRO_BODY> {
  "{{"                 { pushState(MACRO_INTERPOLATION); return CrystalTypes.MACRO_INTERPOLATION_BEGIN; }
  "{%"                 { pushState(MACRO_CONTROL); return CrystalTypes.MACRO_CONTROL_BEGIN; }
  "#{"                 { return CrystalTypes.MACRO_BODY_CONTENT; }
  // Track block openers to count depth for END detection
  // We need to detect 'end' at depth 0 as the macro's closing END
  "end"  / [ \t\r\n]  { if (macroBodyDepth == 0) { yybegin(YYINITIAL); return CrystalTypes.END; }
                         macroBodyDepth--; return CrystalTypes.MACRO_BODY_CONTENT; }
  "end"  / [\)\]\},;]  { if (macroBodyDepth == 0) { yybegin(YYINITIAL); return CrystalTypes.END; }
                         macroBodyDepth--; return CrystalTypes.MACRO_BODY_CONTENT; }
  // end at EOF
  "end"               { if (macroBodyDepth == 0) { yybegin(YYINITIAL); return CrystalTypes.END; }
                         macroBodyDepth--; return CrystalTypes.MACRO_BODY_CONTENT; }
  // Block openers increase depth
  ("def" | "class" | "module" | "struct" | "enum" | "lib" | "fun" | "macro" | "if" | "unless" | "while" | "until" | "case" | "begin" | "do" | "select" | "annotation") / [ \t\r\n(]
                       { macroBodyDepth++; return CrystalTypes.MACRO_BODY_CONTENT; }
  {NEWLINE}            { return CrystalTypes.NEWLINE; }
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  "#" [^\r\n{]*        { return CrystalTypes.MACRO_BODY_CONTENT; }
  [^ \t\r\n\{\}#]+    { return CrystalTypes.MACRO_BODY_CONTENT; }
  "{"                  { return CrystalTypes.MACRO_BODY_CONTENT; }
  "}"                  { return CrystalTypes.MACRO_BODY_CONTENT; }
  [^]                  { return CrystalTypes.MACRO_BODY_CONTENT; }
}

<MACRO_INTERPOLATION> {
  "}}"                 { popState(); return CrystalTypes.MACRO_INTERPOLATION_END; }
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  {IDENTIFIER}         { return CrystalTypes.IDENTIFIER; }
  {CONSTANT}           { return CrystalTypes.CONSTANT; }
  {INSTANCE_VAR}       { return CrystalTypes.INSTANCE_VAR; }
  {DEC_INT}            { return CrystalTypes.INTEGER_LITERAL; }
  \"                   { pushState(STRING); return CrystalTypes.STRING_LITERAL; }
  "."                  { return CrystalTypes.DOT; }
  "("                  { return CrystalTypes.LPAREN; }
  ")"                  { return CrystalTypes.RPAREN; }
  "["                  { return CrystalTypes.LBRACKET; }
  "]"                  { return CrystalTypes.RBRACKET; }
  ","                  { return CrystalTypes.COMMA; }
  "+"                  { return CrystalTypes.PLUS; }
  "-"                  { return CrystalTypes.MINUS; }
  "*"                  { return CrystalTypes.STAR; }
  "/"                  { return CrystalTypes.SLASH; }
  ":"                  { return CrystalTypes.COLON; }
  "=="                 { return CrystalTypes.EQ; }
  "!="                 { return CrystalTypes.NEQ; }
  "<"                  { return CrystalTypes.LT; }
  ">"                  { return CrystalTypes.GT; }
  "||"                 { return CrystalTypes.OR_OR; }
  "&&"                 { return CrystalTypes.AND_AND; }
  "|"                  { return CrystalTypes.PIPE; }
  "&"                  { return CrystalTypes.AMPERSAND; }
  "?"                  { return CrystalTypes.QUESTION; }
  "!"                  { return CrystalTypes.BANG; }
  [^]                  { return TokenType.BAD_CHARACTER; }
}

<MACRO_CONTROL> {
  "%}"                 { popState(); return CrystalTypes.MACRO_CONTROL_END; }
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  "if"                 { return CrystalTypes.IF; }
  "else"               { return CrystalTypes.ELSE; }
  "elsif"              { return CrystalTypes.ELSIF; }
  "end"                { return CrystalTypes.END; }
  "for"                { return CrystalTypes.FOR; }
  "in"                 { return CrystalTypes.IN; }
  "unless"             { return CrystalTypes.UNLESS; }
  "begin"              { return CrystalTypes.BEGIN; }
  "yield"              { return CrystalTypes.YIELD; }
  "true"               { return CrystalTypes.TRUE; }
  "false"              { return CrystalTypes.FALSE; }
  "nil"                { return CrystalTypes.NIL; }
  {CONSTANT}           { return CrystalTypes.CONSTANT; }
  {INSTANCE_VAR}       { return CrystalTypes.INSTANCE_VAR; }
  {CLASS_VAR}          { return CrystalTypes.CLASS_VAR; }
  {IDENTIFIER}         { return CrystalTypes.IDENTIFIER; }
  {DEC_INT}            { return CrystalTypes.INTEGER_LITERAL; }
  \"                   { pushState(STRING); return CrystalTypes.STRING_LITERAL; }
  "("                  { return CrystalTypes.LPAREN; }
  ")"                  { return CrystalTypes.RPAREN; }
  "["                  { return CrystalTypes.LBRACKET; }
  "]"                  { return CrystalTypes.RBRACKET; }
  ","                  { return CrystalTypes.COMMA; }
  "."                  { return CrystalTypes.DOT; }
  ":"                  { return CrystalTypes.COLON; }
  "=="                 { return CrystalTypes.EQ; }
  "!="                 { return CrystalTypes.NEQ; }
  "<="                 { return CrystalTypes.LTE; }
  ">="                 { return CrystalTypes.GTE; }
  "<"                  { return CrystalTypes.LT; }
  ">"                  { return CrystalTypes.GT; }
  "||"                 { return CrystalTypes.OR_OR; }
  "&&"                 { return CrystalTypes.AND_AND; }
  "|"                  { return CrystalTypes.PIPE; }
  "&"                  { return CrystalTypes.AMPERSAND; }
  "="                  { return CrystalTypes.ASSIGN; }
  "+"                  { return CrystalTypes.PLUS; }
  "-"                  { return CrystalTypes.MINUS; }
  "*"                  { return CrystalTypes.STAR; }
  "/"                  { return CrystalTypes.SLASH; }
  "?"                  { return CrystalTypes.QUESTION; }
  "!"                  { return CrystalTypes.BANG; }
  ".."                 { return CrystalTypes.DOTDOT; }
  "..."                { return CrystalTypes.DOTDOTDOT; }
  [^]                  { return TokenType.BAD_CHARACTER; }
}

[^]                    { return TokenType.BAD_CHARACTER; }
