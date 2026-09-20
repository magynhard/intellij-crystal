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
  private boolean percentWordArray = false;
  private boolean percentInterpolation = false;
  // Raw percent literals (%q, %w, %i) have no escape sequences: the compiler
  // creates them with allow_escapes: false, so a backslash is literal content
  // and the char after it lexes normally (a `)` still closes, a `(` still
  // nests). Set at each percent-literal opener alongside the other percent* flags.
  private boolean percentAllowEscapes = true;
  private String heredocId = "";
  private boolean heredocIndented = false;
  private boolean heredocRaw = false;

  // Same-line delimited heredocs queue up (multi-heredoc headers like
  // f(a, <<-X, <<-Y)): delimiter text is consumed silently, and each queued
  // entry opens its own body at the next newline, so everything in between
  // lexes as ordinary code (commas, numbers, strings, closers).
  private static final class PendingHeredoc {
    final String id;
    final boolean raw;
    PendingHeredoc(String id, boolean raw) { this.id = id; this.raw = raw; }
  }
  private final java.util.ArrayDeque<PendingHeredoc> pendingHeredocs = new java.util.ArrayDeque<>();

  // State stack for nested string interpolation
  private final java.util.ArrayDeque<Integer> stateStack = new java.util.ArrayDeque<>();
  private final java.util.ArrayDeque<Integer> depthStack = new java.util.ArrayDeque<>();

  // Regex disambiguation: regex literals can only start in operator position
  private boolean isRegexAllowed() {
    // A slash tightly glued to a preceding colon starts an operator symbol
    // (`:/`), never a regex — unless that colon is a label colon after an
    // identifier or constant (`{a:/re/}`, `f(x:/re/)`), where the regex
    // reading wins exactly like the compiler (which only folds via the
    // parser-driven wants_symbol flag, never after a key). Anything else
    // (`? b :/re/`, `1:/re/`, `"a":/re/`) folds and fails downstream, also
    // exactly like the compiler.
    if (zzStartRead > 0 && zzBuffer.charAt(zzStartRead - 1) == ':') {
      int identEnd = zzStartRead - 2;
      while (identEnd >= 0) {
        char b = zzBuffer.charAt(identEnd);
        if (b != ' ' && b != '\t' && b != '\r' && b != '\n') break;
        identEnd--;
      }
      int identStart = identEnd;
      while (identStart >= 0) {
        char b = zzBuffer.charAt(identStart);
        if (!Character.isLetterOrDigit(b) && b != '_') break;
        identStart--;
      }
      if (identStart < identEnd) {
        int before = identStart;
        while (before >= 0) {
          char b = zzBuffer.charAt(before);
          if (b != ' ' && b != '\t' && b != '\r' && b != '\n') break;
          before--;
        }
        if (before >= 0) {
          char b = zzBuffer.charAt(before);
          if (b == '(' || b == '{' || b == '[' || b == ',') return true;
        }
      }
      return false;
    }
    // Check the character immediately before the current token (skip whitespace already consumed)
    int pos = zzStartRead - 1;
    boolean separatedByWhitespace = false;
    while (pos >= 0 && (zzBuffer.charAt(pos) == ' ' || zzBuffer.charAt(pos) == '\t')) {
      separatedByWhitespace = true;
      pos--;
    }
    if (pos < 0) return true; // start of file
    char c = zzBuffer.charAt(pos);
    if (Character.isLetterOrDigit(c) || c == '_' || c == '?' || c == '!') {
      if (!separatedByWhitespace) return false;
      // Requiring non-whitespace regex content and a closing slash keeps chained
      // division in operator position.
      if (zzMarkedPos >= zzEndRead || Character.isWhitespace(zzBuffer.charAt(zzMarkedPos))) return false;
      if (!hasRegexTerminator()) return false;
      // A whitespace-separated slash after a DOT method name is a bare regex
      // argument: `range.match /pattern/`.
      if (hasDottedReceiverBefore(pos)) return true;
      // After a keyword the slash starts a regex operand, never division:
      // `when /^get_(\w+)$/`, `if /re/`, `return /re/`.
      int wordStart = identifierStart(pos);
      if (wordStart >= 0 && isRegexOperandKeyword(wordStart, pos)) return true;
      // A nested bare callee takes regex arguments too: `x.should match /pattern/`
      // — `match` is the callee of the dot-call `.should`'s bare argument list.
      if (wordStart > 0) {
        int before = wordStart - 1;
        while (before >= 0 && (zzBuffer.charAt(before) == ' ' || zzBuffer.charAt(before) == '\t')) before--;
        if (before >= 0 && hasDottedReceiverBefore(before)) return true;
      }
      return false;
    }
    // After identifiers, constants, numbers, ), ] — it's division, not regex.
    if (c == ')' || c == ']' || c == '"' || c == '\'') return false;
    return true;
  }

  /** Returns the start index of the identifier ending at [end] (inclusive), or -1. */
  private int identifierStart(int end) {
    int start = end;
    while (start >= 0) {
      char c = zzBuffer.charAt(start);
      if (!Character.isLetterOrDigit(c) && c != '_' && c != '?' && c != '!') break;
      start--;
    }
    return start < end ? start + 1 : -1;
  }

  private boolean isRegexOperandKeyword(int start, int end) {
    String word = zzBuffer.subSequence(start, end + 1).toString();
    switch (word) {
      case "when":
      case "if":
      case "unless":
      case "elsif":
      case "while":
      case "until":
      case "return":
        return true;
      default:
        return false;
    }
  }

  private boolean hasDottedReceiverBefore(int identifierEnd) {
    int pos = identifierEnd;
    while (pos >= 0) {
      char c = zzBuffer.charAt(pos);
      if (!Character.isLetterOrDigit(c) && c != '_' && c != '?' && c != '!') break;
      pos--;
    }
    return pos >= 0 && zzBuffer.charAt(pos) == '.';
  }

  private boolean hasRegexTerminator() {
    boolean escaped = false;
    for (int pos = zzMarkedPos; pos < zzEndRead; pos++) {
      char c = zzBuffer.charAt(pos);
      if (!escaped && c == '/') return true;
      if (!escaped && c == '\\') {
        escaped = true;
      } else {
        escaped = false;
      }
    }
    return false;
  }

  // Backtick method-name disambiguation (macros.cr defines `def \`(command) : MacroId`):
  // a backtick directly after the `def` keyword is a METHOD NAME, not a command literal.
  private boolean isBacktickMethodName() {
    int pos = zzStartRead - 1;
    while (pos >= 0 && (zzBuffer.charAt(pos) == ' ' || zzBuffer.charAt(pos) == '\t')) pos--;
    if (pos < 3) return false;
    if (zzBuffer.charAt(pos) == 'f'
        && zzBuffer.charAt(pos - 1) == 'e'
        && zzBuffer.charAt(pos - 2) == 'd'
        && (pos == 3 || !Character.isLetterOrDigit(zzBuffer.charAt(pos - 4)))) return true;
    return false;
  }

  /** A macro body starts only after a declaration at the beginning of a line. */
  private boolean isMacroDefinitionStart() {
    int pos = zzStartRead - 1;
    while (pos >= 0 && zzBuffer.charAt(pos) != '\n' && zzBuffer.charAt(pos) != '\r') pos--;
    pos++;
    while (pos < zzStartRead && (zzBuffer.charAt(pos) == ' ' || zzBuffer.charAt(pos) == '\t')) pos++;

    if (matchesWordAt(pos, "private")) {
      pos += 7;
      while (pos < zzStartRead && (zzBuffer.charAt(pos) == ' ' || zzBuffer.charAt(pos) == '\t')) pos++;
    } else if (matchesWordAt(pos, "protected")) {
      pos += 9;
      while (pos < zzStartRead && (zzBuffer.charAt(pos) == ' ' || zzBuffer.charAt(pos) == '\t')) pos++;
    }
    return pos == zzStartRead;
  }

  private boolean matchesWordAt(int start, String word) {
    int end = start + word.length();
    return end <= zzBuffer.length()
        && zzBuffer.subSequence(start, end).toString().equals(word)
        && (start == 0 || !Character.isLetterOrDigit(zzBuffer.charAt(start - 1)))
        && (end == zzBuffer.length() || !Character.isLetterOrDigit(zzBuffer.charAt(end)));
  }

  /** Macro signatures may continue over newlines; wait for their closing parenthesis. */
  private boolean macroHeaderHasOpenParenthesis() {
    int depth = 0;
    for (int pos = macroHeaderStart; pos < zzStartRead; pos++) {
      char c = zzBuffer.charAt(pos);
      if (c == '(') depth++;
      else if (c == ')' && depth > 0) depth--;
    }
    return depth > 0;
  }

  // Macro body state tracking
  private boolean macroHeaderSeen = false;
  private int macroHeaderStart = -1;
  private int macroBodyDepth = 0;
  private boolean macroBodyAtLineStart = false;

  // Tracks `{% %}` block depth (for/if/unless/... bodies lex following code
  // with macro interpolation active inside strings). Balanced inline tags net
  // to zero; the counter never drops below zero. Single `{{ }}` interpolation
  // never touches it (different delimiters).
  private int macroControlDepth = 0;

  /**
   * Classifies the `{% ... %}` tag closing at the current position by scanning
   * back to its `{%` opener and reading the first word. Block openers nest
   * macro code whose strings interpolate `{{ }}`; `end` closes one level;
   * anything else (else/elsif/inline statements) leaves the depth unchanged.
   * A `{%` inside a tag string or comment misclassifies — accepted limitation
   * (bounded blast radius: string `{{` gating only, fail-open to literal).
   */
  private void updateMacroControlDepth() {
    int i = zzStartRead - 1;
    int openPos = -1;
    while (i >= 1) {
      if (zzBuffer.charAt(i - 1) == '{' && zzBuffer.charAt(i) == '%') { openPos = i - 1; break; }
      i--;
    }
    if (openPos < 0) return;
    int w = openPos + 2;
    int len = zzBuffer.length();
    while (w < len) {
      char c = zzBuffer.charAt(w);
      if (c != ' ' && c != '\t' && c != '\r' && c != '\n') break;
      w++;
    }
    if (w < len && zzBuffer.charAt(w) == '#') return;
    int start = w;
    while (w < len) {
      char c = zzBuffer.charAt(w);
      if (!Character.isLetterOrDigit(c) && c != '_') break;
      w++;
    }
    if (start >= w) return;
    String word = zzBuffer.subSequence(start, w).toString();
    switch (word) {
      case "for":
      case "if":
      case "unless":
      case "while":
      case "until":
      case "begin":
      case "case":
        macroControlDepth++;
        break;
      case "end":
        if (macroControlDepth > 0) macroControlDepth--;
        break;
      default:
        break;
    }
  }
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

  /**
   * Fresh-variable admission for the YYINITIAL `%ident` form. `%var{key} = ...`
   * appears between macro control tags (`{% begin %}`/`{% for %}` bodies in
   * iterator.cr, json/from_yaml.cr) — Crystal's own lexer emits :MACRO_VAR only
   * with an active macro state. We approximate the macro-context gate at
   * operator level: `%ident` is a fresh variable unless the immediately
   * preceding significant token can be a left operand of a binary `%`
   * (identifier, constant, var, number, string/char/symbol literal, closing
   * bracket, chain end). Identifiers `x %val` therefore keep the modulo
   * reading, while line starts, postfix-modifier keywords, and argument
   * positions take the fresh variable.
   */
  private boolean freshVariableAllowed() {
    int i = zzStartRead - 1;
    while (i >= 0 && (zzBuffer.charAt(i) == ' ' || zzBuffer.charAt(i) == '\t')) i--;
    if (i < 0) return true;
    char c = zzBuffer.charAt(i);
    switch (c) {
      case '\n': case '\r': case '(': case '[': case '{': case ',':
      case '=': case '+': case '-': case '*': case '/': case '<': case '>':
      case '?': case ':': case '|': case '&': case '^': case '~': case '!':
      case '%':
        return true;
      case '.': case ')': case ']': case '}': case '@': case '"': case '\'':
        return false;
      default:
        // Word predecessors are variables/constants (left operands of the
        // modulo operator) except the postfix-modifier keywords, after which a
        // fresh variable starts an operand again (`return stop if %value{i}`).
        if (Character.isLetter(c) || Character.isDigit(c) || c == '_' || c == '?' || c == '!') {
          int end = i + 1;
          while (i >= 0 && (Character.isLetterOrDigit(zzBuffer.charAt(i)) || zzBuffer.charAt(i) == '_'
                            || zzBuffer.charAt(i) == '?' || zzBuffer.charAt(i) == '!')) i--;
          String word = zzBuffer.subSequence(i + 1, end).toString();
          return word.equals("if") || word.equals("unless") || word.equals("while")
              || word.equals("until") || word.equals("return");
        }
        return false;
    }
  }

  /**
   * `%(`-style input is a percent literal in expression position, but an operator
   * METHOD NAME after `def` or `.` (`def %(other)`, `def self.%(...)`). Crystal
   * disambiguates by context; we look backwards at the raw buffer.
   */
  private boolean percentStartsMethodName() {
    int i = zzStartRead - 1;
    while (i >= 0 && Character.isWhitespace(zzBuffer.charAt(i))) i--;
    if (i < 0) return false;
    if (zzBuffer.charAt(i) == '.') return true;
    int end = i + 1;
    while (i >= 0 && (Character.isLetterOrDigit(zzBuffer.charAt(i)) || zzBuffer.charAt(i) == '_')) i--;
    String word = zzBuffer.subSequence(i + 1, end).toString();
    return word.equals("def");
  }

  /**
   * Emits the `{SYMBOL}` match as a single SYMBOL_LITERAL, except that the
   * trailing `=` belongs to a following `==` operator (`:foo==` is `:foo` +
   * `==`, never the symbol `:foo=`). Crystal's consume_symbol only folds the
   * `=` into the symbol when no further `=` follows.
   */
  private IElementType symbolLiteral() {
    int len = yylength();
    if (len >= 1 && yycharat(len - 1) == '=' &&
        zzMarkedPos < zzBuffer.length() && zzBuffer.charAt(zzMarkedPos) == '=') {
      yypushback(1);
    }
    return CrystalTypes.SYMBOL_LITERAL;
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
GLOBAL_VAR = "$" ({IDENTIFIER} | {DIGIT}+ | "~" | "?")

// Character literal escape sequences
CHAR_ESCAPE = "\\" ( [abefnrtv\\'0] | "x" {HEX_DIGIT}{2} | "u" "{" {HEX_DIGIT}+ "}" | "u" {HEX_DIGIT}{4} | {OCT_DIGIT}{1,3} )
CHAR_LITERAL = "'" ( [^'\\] | {CHAR_ESCAPE} ) "'"

// Symbol (simple forms only — :"string" handled separately for interpolation support)
// A trailing `=` is part of the symbol (`:color=`); `==` is not (`:foo==`
// is `:foo` + `==`, see consume_symbol in the compiler lexer). The optional
// `=` therefore always matches greedily and symbolLiteral() pushes it back
// when another `=` follows immediately.
SYMBOL = ":" ( {IDENTIFIER} | {CONSTANT} ) "="?

%state STRING INTERPOLATION REGEX BACKTICK PERCENT_LITERAL HEREDOC_BODY HEREDOC_PREAMBLE MACRO_BODY MACRO_INTERPOLATION MACRO_CONTROL

%%

<YYINITIAL> {
  // Whitespace and comments
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  "\\" (\r\n | \r | \n) { return TokenType.WHITE_SPACE; }
  // Escaped macro forms: `\{% stmt %}` / `\{{ expr }}` (Crystal treats the
  // backslash as VERBATIM data; primitives.cr:101/146). Inner states mirror
  // the ordinary `{%`/`{{` openings; only the begin token differs.
  "\\" "{%"           { pushState(MACRO_CONTROL); return CrystalTypes.MACRO_CONTROL_ESCAPED_BEGIN; }
  "\\" "{{"           { pushState(MACRO_INTERPOLATION); return CrystalTypes.MACRO_INTERPOLATION_ESCAPED_BEGIN; }
  {NEWLINE}            { PendingHeredoc ph = pendingHeredocs.pollFirst();
                         if (ph != null) { heredocId = ph.id; heredocRaw = ph.raw; yybegin(HEREDOC_BODY); return CrystalTypes.HEREDOC_START; } // body opener
                         if (macroHeaderSeen && !macroHeaderHasOpenParenthesis()) { macroHeaderSeen = false; macroHeaderStart = -1; macroBodyDepth = 0; macroBodyAtLineStart = true; yybegin(MACRO_BODY); }
                         return CrystalTypes.NEWLINE; }
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
  "macro"              { if (isMacroDefinitionStart()) { macroHeaderSeen = true; macroHeaderStart = zzStartRead; } return CrystalTypes.MACRO; }
  "module"             { return CrystalTypes.MODULE; }
  "next"               { return CrystalTypes.NEXT; }
  "nil?"               { return CrystalTypes.NIL_QUESTION; }
  "nil"                { return CrystalTypes.NIL; }
  "of"                 { return CrystalTypes.OF; }
  "offsetof"           { return CrystalTypes.OFFSETOF; }
  "out"                { return CrystalTypes.OUT; }
  "pointerof"          { return CrystalTypes.POINTEROF; }
  "previous_def"       { return CrystalTypes.PREVIOUS_DEF; }
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

  // Invalid single-quote string (more than one character between quotes)
  // This rule must come before CHAR_LITERAL because JFlex longest-match wins.
  // It matches 'xx...x' with 2+ characters inside, producing a single BAD_CHARACTER token.
  "'" [^'\\] [^'\r\n] [^'\r\n]* "'" { return TokenType.BAD_CHARACTER; }

  // Literals
  {CHAR_LITERAL}       { return CrystalTypes.CHAR_LITERAL; }
  ":\"" / [^]          { pushState(STRING); return CrystalTypes.SYMBOL_COLON; }
  {SYMBOL}             { return symbolLiteral(); }

  // Numbers (float before int since float is more specific with dot)
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} "_"? "f" ("32" | "64")                                                { return CrystalTypes.FLOAT_LITERAL; }
  {INTEGER}            { return CrystalTypes.INTEGER_LITERAL; }

  // Heredoc header: <<-IDENTIFIER or <<-'IDENTIFIER'. Emits a MARKER token
  // (delimiter span) that lives inside the argument list; the remaining header
  // line lexes as ordinary code (commas, numbers, closers, further markers).
  // Bodies are queued and open at the next newline via HEREDOC_START drain.
  "<<-'" [A-Za-z_][A-Za-z0-9_]* "'"  {
                         String text = yytext().toString();
                         pendingHeredocs.addLast(new PendingHeredoc(text.substring(4, text.length() - 1), true));
                         return CrystalTypes.HEREDOC_START;
                       }
  "<<-" [A-Za-z_][A-Za-z0-9_]*       {
                         String text = yytext().toString();
                         pendingHeredocs.addLast(new PendingHeredoc(text.substring(3), false));
                         return CrystalTypes.HEREDOC_START;
                       }

  // Macro control at top level: {% ... %}
  "{%"                 { pushState(MACRO_CONTROL); return CrystalTypes.MACRO_CONTROL_BEGIN; }
  // Macro interpolation at top level: {{ ... }}
  "{{"                 { pushState(MACRO_INTERPOLATION); return CrystalTypes.MACRO_INTERPOLATION_BEGIN; }

  // Percent literals: %w(...), %W(...), %i(...), %I(...), %(...), %[...], %{...}, %<...>, %|...|
  "%w" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = false;
                          percentWordArray = true;
                          percentAllowEscapes = false;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_WORD_ARRAY_BEGIN;
                        }
  "%W" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = true;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_WORD_ARRAY_BEGIN;
                        }
  "%i" [\(\[\{<|]     {
                           char c = yycharat(yylength() - 1);
                           percentOpenChar = c;
                           percentCloseChar = closingChar(c);
                           percentDepth = 1;
                           percentTokenType = CrystalTypes.SYMBOL_LITERAL;
                           percentInterpolation = false;
                           percentWordArray = false;
                           percentAllowEscapes = false;
                           pushState(PERCENT_LITERAL);
                           return CrystalTypes.PERCENT_SYMBOL_BEGIN;
                         }
  "%I" [\(\[\{<|]     {
                           char c = yycharat(yylength() - 1);
                           percentOpenChar = c;
                           percentCloseChar = closingChar(c);
                           percentDepth = 1;
                           percentTokenType = CrystalTypes.SYMBOL_LITERAL;
                           percentInterpolation = true;
                           percentWordArray = false;
                           percentAllowEscapes = true;
                           pushState(PERCENT_LITERAL);
                           return CrystalTypes.PERCENT_SYMBOL_BEGIN;
                         }
  "%q" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = false;
                          percentWordArray = false;
                          percentAllowEscapes = false;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  "%Q" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  "%r" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.REGEX_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  "%x" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.COMMAND_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  "%" [\(\[\{<|]      {
                          if (percentStartsMethodName()) {
                            yypushback(1);
                            return CrystalTypes.PERCENT;
                          }
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }

  // Macro fresh variables: `%value{i} = ...` between macro control tags
  // (iterator.cr, json/from_json.cr) and bare reads (`%val` in math_spec).
  // Crystal's lexer emits :MACRO_VAR only while the macro state is active, so
  // in normal code `%ident` stays behind the modulo-operator decision unless
  // the previous significant token cannot be a left operand (line start, open
  // bracket, comma, assign, operator, or the tail `%}` of a control tag).
  // Longest-match keeps the percent literals ahead: `%q(` matches three
  // characters where the fresh-var rule matches only two.
  "%" {IDENTIFIER}     { if (freshVariableAllowed()) { return CrystalTypes.MACRO_FRESH_VAR; }
                          return CrystalTypes.PERCENT; }

  // String start
  \"                   { pushState(STRING); return CrystalTypes.STRING_LITERAL; }

  // Command literal
  "`"                    { if (isBacktickMethodName()) { return CrystalTypes.BACKTICK; }
                           pushState(BACKTICK); return CrystalTypes.COMMAND_BEGIN; }

  // Regex literal (only in operator position — not after identifiers, constants, literals, ) or ])
  "/"                    { if (isRegexAllowed()) { pushState(REGEX); return CrystalTypes.REGEX_BEGIN; }
                           return CrystalTypes.SLASH; }

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
  // `&->(x) { }` block-pass proc literals: `&-` must not swallow the arrow.
  // Push the '-' back so the scanner re-lexes `->` as ARROW.
  "&-"                 { if (zzMarkedPos < zzBuffer.length() && zzBuffer.charAt(zzMarkedPos) == '>') {
                           yypushback(1);
                           return CrystalTypes.AMPERSAND;
                         }
                         return CrystalTypes.WRAP_MINUS; }
  "**"                 { return CrystalTypes.DOUBLE_STAR; }
  "//"                 { return CrystalTypes.DOUBLE_SLASH; }
  "<<"                 { return CrystalTypes.LSHIFT; }
  ">>"                 { return CrystalTypes.RSHIFT; }
  "=="                 { return CrystalTypes.EQ; }
  "!="                 { return CrystalTypes.NEQ; }
  "!~"                 { return CrystalTypes.BANG_TILDE; }
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
  // `{{` inside strings interpolates only in macro code (`{% %}` block bodies
  // and the like — verified against the compiler): in plain code it stays
  // literal text. An escape always consumes the first brace first (the `\`
  // catch-all below), so `\{{` can never reach this rule.
  "{{"                 { if (macroControlDepth > 0) { pushState(MACRO_INTERPOLATION); return CrystalTypes.MACRO_INTERPOLATION_BEGIN; } return CrystalTypes.STRING_LITERAL; }
  "\\" [abefnrtv\\\"\\'0]  { return CrystalTypes.STRING_ESCAPE; }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { return CrystalTypes.STRING_ESCAPE; }
  "\\" "u" {HEX_DIGIT}{4}        { return CrystalTypes.STRING_ESCAPE; }
  "\\" "x" {HEX_DIGIT}{1,2}      { return CrystalTypes.STRING_ESCAPE; }
  "\\" {OCT_DIGIT}{1,3}          { return CrystalTypes.STRING_ESCAPE; }
  "\\\n"               { return CrystalTypes.STRING_ESCAPE; }
  "\\\r\n"             { return CrystalTypes.STRING_ESCAPE; }
  "\\" .               { return CrystalTypes.STRING_ESCAPE; }
  [^\"\#\\{]+          { return CrystalTypes.STRING_LITERAL; }
  "{"                  { return CrystalTypes.STRING_LITERAL; }
  "#"                  { return CrystalTypes.STRING_LITERAL; }
}

<REGEX> {
  "#{"                 { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; }
  "#"                  { return CrystalTypes.REGEX_LITERAL; }
  "\\" [abefnrtv\\\"\\'0\/]  { return CrystalTypes.STRING_ESCAPE; }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { return CrystalTypes.STRING_ESCAPE; }
  "\\" "u" {HEX_DIGIT}{4}        { return CrystalTypes.STRING_ESCAPE; }
  "\\" "x" {HEX_DIGIT}{1,2}      { return CrystalTypes.STRING_ESCAPE; }
  "\\" {OCT_DIGIT}{1,3}          { return CrystalTypes.STRING_ESCAPE; }
  "\\" .               { return CrystalTypes.STRING_ESCAPE; }
  "/" [imx]*            { popState(); return CrystalTypes.REGEX_END; }
  [^\/\#\\]+            { return CrystalTypes.REGEX_LITERAL; }
}

<BACKTICK> {
  "#{"                 { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; }
  "#"                  { return CrystalTypes.COMMAND_LITERAL; }
  "\\" [abefnrtv\\\"\\'0]  { return CrystalTypes.STRING_ESCAPE; }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { return CrystalTypes.STRING_ESCAPE; }
  "\\" "u" {HEX_DIGIT}{4}        { return CrystalTypes.STRING_ESCAPE; }
  "\\" "x" {HEX_DIGIT}{1,2}      { return CrystalTypes.STRING_ESCAPE; }
  "\\" {OCT_DIGIT}{1,3}          { return CrystalTypes.STRING_ESCAPE; }
  "\\\n"               { return CrystalTypes.STRING_ESCAPE; }
  "\\\r\n"             { return CrystalTypes.STRING_ESCAPE; }
  "\\" .               { return CrystalTypes.STRING_ESCAPE; }
  "`"                  { popState(); return CrystalTypes.COMMAND_END; }
  {NEWLINE}            { return CrystalTypes.COMMAND_LITERAL; }
  [^\`#\\]+            { return CrystalTypes.COMMAND_LITERAL; }
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
  ":\"" / [^]          { pushState(STRING); return CrystalTypes.SYMBOL_COLON; }
  {SYMBOL}             { return symbolLiteral(); }
  "if"                 { return CrystalTypes.IF; }
  "unless"             { return CrystalTypes.UNLESS; }
  "while"              { return CrystalTypes.WHILE; }
  "until"              { return CrystalTypes.UNTIL; }
  // do/end blocks inside string interpolations (`"#{list.map do |x| x end}"`
  // is compiler-valid): reserved words, so no identifier lexing can change.
  "do"                 { return CrystalTypes.DO; }
  "end"                { return CrystalTypes.END; }
  "rescue"             { return CrystalTypes.RESCUE; }
  "require"            { return CrystalTypes.REQUIRE; }
  {IDENTIFIER}         { return CrystalTypes.IDENTIFIER; }
  {CONSTANT}           { return CrystalTypes.CONSTANT; }
  {INSTANCE_VAR}       { return CrystalTypes.INSTANCE_VAR; }
  {CLASS_VAR}          { return CrystalTypes.CLASS_VAR; }
  {GLOBAL_VAR}         { return CrystalTypes.GLOBAL_VAR; }
  // Numbers (float before int since float is more specific with dot) —
  // mirror YYINITIAL so interpolated floats (`"#{1f32}"`) lex identically.
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} "_"? "f" ("32" | "64")                                                { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT}            { return CrystalTypes.INTEGER_LITERAL; }
  // Invalid multi-character single-quote string (same guard as YYINITIAL)
  "'" [^'\\] [^'\r\n] [^'\r\n]* "'" { return TokenType.BAD_CHARACTER; }
  {CHAR_LITERAL}       { return CrystalTypes.CHAR_LITERAL; }
  \"                   { pushState(STRING); return CrystalTypes.STRING_LITERAL; }
  "."                  { return CrystalTypes.DOT; }
  "("                  { return CrystalTypes.LPAREN; }
  ")"                  { return CrystalTypes.RPAREN; }
  "["                  { return CrystalTypes.LBRACKET; }
  "]"                  { return CrystalTypes.RBRACKET; }
  "::"                 { return CrystalTypes.DOUBLE_COLON; }
  ":"                  { return CrystalTypes.COLON; }
  "=="                 { return CrystalTypes.EQ; }
  "!="                 { return CrystalTypes.NEQ; }
  "!~"                 { return CrystalTypes.BANG_TILDE; }
  "<="                 { return CrystalTypes.LTE; }
  ">="                 { return CrystalTypes.GTE; }
  "&&"                 { return CrystalTypes.AND_AND; }
  "||"                 { return CrystalTypes.OR_OR; }
  // Integer division inside interpolations (`"#{number // 10_000}"`):
  // longest-match prefers this over the SLASH below.
  "//"                 { return CrystalTypes.DOUBLE_SLASH; }
  "=>"                 { return CrystalTypes.DOUBLE_ARROW; }
  // Ranges inside string interpolations: `#{code[2...-2]}` (ameba heredoc_indent).
  "..."                { return CrystalTypes.DOTDOTDOT; }
  ".."                 { return CrystalTypes.DOTDOT; }
  "+"                  { return CrystalTypes.PLUS; }
  "-"                  { return CrystalTypes.MINUS; }
  "*"                  { return CrystalTypes.STAR; }
  "/"                  { return CrystalTypes.SLASH; }
  "%"                  { return CrystalTypes.PERCENT; }
  // Percent literals inside string interpolations: `"#{ %(a) if b }"`
  // (generate_grapheme_break_specs.cr). Without this, `%(` splits into
  // PERCENT plus LPAREN and the interpolation fails with "<expression>
  // expected, got '%'". Mirrors the MACRO_INTERPOLATION rule; like there,
  // a tight `obj.%(...)` method call keeps its old shape (documented
  // boundary, consistent across both interpolation states).
  "%" [\(\[\{<|]      {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
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
  "#{"                 { if (percentInterpolation) { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; } return percentTokenType; }
  "#"                  { return percentTokenType; }
  // Handle nested opening delimiters (except | which doesn't nest)
  .                    {
                          char c = yycharat(0);
                          if (c == percentCloseChar) {
                            percentDepth--;
                            if (percentDepth == 0) {
                              popState();
                              if (percentWordArray) {
                                return CrystalTypes.PERCENT_WORD_ARRAY_END;
                              }
                              if (percentTokenType == CrystalTypes.SYMBOL_LITERAL) {
                                return CrystalTypes.PERCENT_SYMBOL_END;
                              }
                              return CrystalTypes.PERCENT_LITERAL_END;
                            }
                            return percentTokenType;
                          } else if (c == percentOpenChar && percentOpenChar != '|') {
                            percentDepth++;
                            return percentTokenType;
                          }
                          return percentTokenType;
                        }
  "\\" .               {
                           if (!percentAllowEscapes && !percentWordArray && percentTokenType == CrystalTypes.STRING_LITERAL) {
                             // Raw %q literal: the backslash is literal content and the
                             // char after it lexes normally, so `)` still closes the
                             // literal and `(` still nests (compiler allow_escapes:
                             // false). Push back the second char to re-lex it.
                             yypushback(1);
                             return percentTokenType;
                           }
                           if (percentTokenType == CrystalTypes.STRING_LITERAL || percentTokenType == CrystalTypes.COMMAND_LITERAL) { return CrystalTypes.STRING_ESCAPE; } return percentTokenType;
                         }
  {NEWLINE}            { return percentTokenType; }
}

<HEREDOC_PREAMBLE> {
  // Newline following a finished body when further delimiters were queued on
  // the original line: it opens the next literal.
  {NEWLINE}            { yybegin(HEREDOC_BODY); return CrystalTypes.HEREDOC_START; }
}

<HEREDOC_BODY> {
  // Check for end marker (with optional leading whitespace if indented)
  ^[ \t]* {CONSTANT}  {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           PendingHeredoc ph = pendingHeredocs.pollFirst();
                           if (ph != null) {
                             heredocId = ph.id;
                             heredocRaw = ph.raw;
                             yybegin(HEREDOC_PREAMBLE);
                           } else {
                             yybegin(YYINITIAL);
                           }
                           return CrystalTypes.HEREDOC_END;
                         }
                         return CrystalTypes.HEREDOC_CONTENT;
                       }
  ^[ \t]* {IDENTIFIER} {
                         String text = yytext().toString().trim();
                         if (text.equals(heredocId)) {
                           PendingHeredoc ph = pendingHeredocs.pollFirst();
                           if (ph != null) {
                             heredocId = ph.id;
                             heredocRaw = ph.raw;
                             yybegin(HEREDOC_PREAMBLE);
                           } else {
                             yybegin(YYINITIAL);
                           }
                           return CrystalTypes.HEREDOC_END;
                         }
                         return CrystalTypes.HEREDOC_CONTENT;
                       }
  [^\r\n\#\\]+         { return CrystalTypes.HEREDOC_CONTENT; }
  "\\" [abefnrtv\\\"\\'0]  { if (!heredocRaw) { return CrystalTypes.STRING_ESCAPE; } return CrystalTypes.HEREDOC_CONTENT; }
  "\\" "u" "{" {HEX_DIGIT}+ "}"  { if (!heredocRaw) { return CrystalTypes.STRING_ESCAPE; } return CrystalTypes.HEREDOC_CONTENT; }
  "\\" "u" {HEX_DIGIT}{4}        { if (!heredocRaw) { return CrystalTypes.STRING_ESCAPE; } return CrystalTypes.HEREDOC_CONTENT; }
  "\\" "x" {HEX_DIGIT}{1,2}      { if (!heredocRaw) { return CrystalTypes.STRING_ESCAPE; } return CrystalTypes.HEREDOC_CONTENT; }
  "\\" {OCT_DIGIT}{1,3}          { if (!heredocRaw) { return CrystalTypes.STRING_ESCAPE; } return CrystalTypes.HEREDOC_CONTENT; }
  "\\" .               { if (!heredocRaw) { return CrystalTypes.STRING_ESCAPE; } return CrystalTypes.HEREDOC_CONTENT; }
  "#{"                 { if (!heredocRaw) { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; } return CrystalTypes.HEREDOC_CONTENT; }
  "#"                  { return CrystalTypes.HEREDOC_CONTENT; }
  "\\"                 { return CrystalTypes.HEREDOC_CONTENT; }
  {NEWLINE}            { return CrystalTypes.HEREDOC_CONTENT; }
}

<MACRO_BODY> {
  "{{"                 { pushState(MACRO_INTERPOLATION); return CrystalTypes.MACRO_INTERPOLATION_BEGIN; }
  "{%"                 { pushState(MACRO_CONTROL); return CrystalTypes.MACRO_CONTROL_BEGIN; }
  "#{"                 { return CrystalTypes.MACRO_BODY_CONTENT; }
  // Track block openers to count depth for END detection
  // We need to detect 'end' at depth 0 as the macro's closing END
  "end"  / [ \t\r\n]  { macroBodyAtLineStart = false; if (macroBodyDepth == 0) { yybegin(YYINITIAL); return CrystalTypes.END; }
                         macroBodyDepth--; return CrystalTypes.MACRO_BODY_CONTENT; }
  "end"  / [\)\]\},;]  { macroBodyAtLineStart = false; if (macroBodyDepth == 0) { yybegin(YYINITIAL); return CrystalTypes.END; }
                         macroBodyDepth--; return CrystalTypes.MACRO_BODY_CONTENT; }
  // Chained call on a block value (`end.should ...` in spec/helpers): the
  // greedy content rule below would swallow `end.foo` whole and the depth
  // would never decrement, so the macro never closes. Matching the whole run
  // wins the longest-match tie against the content rule; the span stays one
  // opaque content token exactly like the other end followers. At depth 0 the
  // end still closes the macro and the pushed-back suffix lexes as real code.
  "end" "." [^ \t\r\n\{\}#]* { macroBodyAtLineStart = false; if (macroBodyDepth == 0) { yypushback(yylength() - 3); yybegin(YYINITIAL); return CrystalTypes.END; }
                          macroBodyDepth--; return CrystalTypes.MACRO_BODY_CONTENT; }
  // end at EOF
  "end"               { macroBodyAtLineStart = false; if (macroBodyDepth == 0) { yybegin(YYINITIAL); return CrystalTypes.END; }
                         macroBodyDepth--; return CrystalTypes.MACRO_BODY_CONTENT; }
  // Block openers that always start blocks (never postfix)
  ("def" | "class" | "module" | "struct" | "enum" | "lib" | "fun" | "macro" | "case" | "begin" | "do" | "select" | "annotation") / [ \t\r\n(]
                       { macroBodyDepth++; macroBodyAtLineStart = false; return CrystalTypes.MACRO_BODY_CONTENT; }
  // Keywords that can be postfix modifiers — only count as block openers at line start
  ("if" | "unless" | "while" | "until") / [ \t\r\n(]
                       { if (macroBodyAtLineStart) { macroBodyDepth++; } macroBodyAtLineStart = false; return CrystalTypes.MACRO_BODY_CONTENT; }
  {NEWLINE}            { macroBodyAtLineStart = true; return CrystalTypes.NEWLINE; }
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  "#" [^\r\n{]*        { macroBodyAtLineStart = false; return CrystalTypes.MACRO_BODY_CONTENT; }
  "%" {IDENTIFIER}     { macroBodyAtLineStart = false; return CrystalTypes.MACRO_FRESH_VAR; }
  [^ \t\r\n\{\}#]+    { macroBodyAtLineStart = false; return CrystalTypes.MACRO_BODY_CONTENT; }
  "{"                  { macroBodyAtLineStart = false; return CrystalTypes.MACRO_BODY_CONTENT; }
  "}"                  { macroBodyAtLineStart = false; return CrystalTypes.MACRO_BODY_CONTENT; }
  [^]                  { macroBodyAtLineStart = false; return CrystalTypes.MACRO_BODY_CONTENT; }
}

<MACRO_INTERPOLATION> {
  "}}?"                { popState(); return CrystalTypes.MACRO_INTERPOLATION_END; }
  "}}!"                { popState(); return CrystalTypes.MACRO_INTERPOLATION_END; }
  "}}"                 { popState(); return CrystalTypes.MACRO_INTERPOLATION_END; }
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  {NEWLINE}            { return CrystalTypes.NEWLINE; }
  {SYMBOL}             { return symbolLiteral(); }
  ":\"" / [^]          { pushState(STRING); return CrystalTypes.SYMBOL_COLON; }
  "if"                 { return CrystalTypes.IF; }
  "unless"             { return CrystalTypes.UNLESS; }
  "while"              { return CrystalTypes.WHILE; }
  "until"              { return CrystalTypes.UNTIL; }
  // do/end blocks inside macro interpolations (`{{ properties.map do
  // |field| ... end.splat }}` in macros.cr `record`): mirror MACRO_CONTROL
  // so block bodies lex with real structure instead of collapsing to
  // IDENTIFIERs. `do`/`end` are reserved words, so no identifier lexing
  // can change.
  "do"                 { return CrystalTypes.DO; }
  "end"                { return CrystalTypes.END; }
  "rescue"             { return CrystalTypes.RESCUE; }
  "require"            { return CrystalTypes.REQUIRE; }
  {IDENTIFIER}         { return CrystalTypes.IDENTIFIER; }
  {CONSTANT}           { return CrystalTypes.CONSTANT; }
  {INSTANCE_VAR}       { return CrystalTypes.INSTANCE_VAR; }
  {GLOBAL_VAR}         { return CrystalTypes.GLOBAL_VAR; }
  // Numbers (float before int since float is more specific with dot) —
  // mirror YYINITIAL: macro bodies compute with hex literals (`0x20b`),
  // suffixed integers (`27_u32`) and floats exactly like plain code.
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} "_"? "f" ("32" | "64")                                                { return CrystalTypes.FLOAT_LITERAL; }
  {INTEGER}            { return CrystalTypes.INTEGER_LITERAL; }
  "'" [^'\\] [^'\r\n] [^'\r\n]* "'" { return TokenType.BAD_CHARACTER; }
  {CHAR_LITERAL}       { return CrystalTypes.CHAR_LITERAL; }
  \"                   { pushState(STRING); return CrystalTypes.STRING_LITERAL; }
  "`"                  { pushState(BACKTICK); return CrystalTypes.COMMAND_BEGIN; }
  "/"                  { if (isRegexAllowed()) { pushState(REGEX); return CrystalTypes.REGEX_BEGIN; } return CrystalTypes.SLASH; }
  "->"                 { return CrystalTypes.ARROW; }
  "=>"                 { return CrystalTypes.DOUBLE_ARROW; }
  // Ranges inside interpolations: `#{code[2...-2]}` (ameba heredoc_indent).
  "..."                { return CrystalTypes.DOTDOTDOT; }
  ".."                 { return CrystalTypes.DOTDOT; }
  "@["                 { return CrystalTypes.ANNOTATION; }
  "@"                  { return CrystalTypes.AT; }
  "."                  { return CrystalTypes.DOT; }
  "("                  { return CrystalTypes.LPAREN; }
  ")"                  { return CrystalTypes.RPAREN; }
  "["                  { return CrystalTypes.LBRACKET; }
  "]"                  { return CrystalTypes.RBRACKET; }
  "{"                  { return CrystalTypes.LBRACE; }
  // Nested macro interpolation inside {% ... %}: `@{{ method.id }}` generated
  // accessors (kemal param_parser) mirror the <MACRO_INTERPOLATION> state.
  "{{"                 { pushState(MACRO_INTERPOLATION); return CrystalTypes.MACRO_INTERPOLATION_BEGIN; }
  "}"                  { return CrystalTypes.RBRACE; }
  ","                  { return CrystalTypes.COMMA; }
  ";"                  { return CrystalTypes.SEMICOLON; }
  "#"                  { return CrystalTypes.HASH; }
  "<=>"                { return CrystalTypes.SPACESHIP; }
  "<="                 { return CrystalTypes.LTE; }
  ">="                 { return CrystalTypes.GTE; }
  "**"                 { return CrystalTypes.DOUBLE_STAR; }
  "<<"                 { return CrystalTypes.LSHIFT; }
  ">>"                 { return CrystalTypes.RSHIFT; }
  "^"                  { return CrystalTypes.CARET; }
  "~"                  { return CrystalTypes.TILDE; }
  "%"                  { return CrystalTypes.PERCENT; }
  "="                  { return CrystalTypes.ASSIGN; }
  "+"                  { return CrystalTypes.PLUS; }
  "-"                  { return CrystalTypes.MINUS; }
  "*"                  { return CrystalTypes.STAR; }
  "//="                { return CrystalTypes.DOUBLE_SLASH_ASSIGN; }
  "//"                 { return CrystalTypes.DOUBLE_SLASH; }
   "/"                  { return CrystalTypes.SLASH; }
   "::"                 { return CrystalTypes.DOUBLE_COLON; }
   ":"                  { return CrystalTypes.COLON; }
   "=="                 { return CrystalTypes.EQ; }
  "!="                 { return CrystalTypes.NEQ; }
  "=~"                 { return CrystalTypes.MATCH_OP; }
  "!~"                 { return CrystalTypes.BANG_TILDE; }
  "<"                  { return CrystalTypes.LT; }
  ">"                  { return CrystalTypes.GT; }
   "||"                 { return CrystalTypes.OR_OR; }
   "&&"                 { return CrystalTypes.AND_AND; }
   "|"                  { return CrystalTypes.PIPE; }
   "&"                  { return CrystalTypes.AMPERSAND; }
   "?"                  { return CrystalTypes.QUESTION; }
   "!"                  { return CrystalTypes.BANG; }
   // Percent literals inside macro interpolations: `{{ ch.join(%( or )) }}`
   // (hexfloat.cr check_ch macro). Without this, `%(` splits into PERCENT
   // plus LPAREN and the interpolation fails to close.
   "%" [\(\[\{<|]      {
                           char c = yycharat(yylength() - 1);
                           percentOpenChar = c;
                           percentCloseChar = closingChar(c);
                           percentDepth = 1;
                           percentTokenType = CrystalTypes.STRING_LITERAL;
                           percentInterpolation = true;
                           percentWordArray = false;
                           percentAllowEscapes = true;
                           pushState(PERCENT_LITERAL);
                           return CrystalTypes.PERCENT_LITERAL_BEGIN;
                         }
   [^]                  { return TokenType.BAD_CHARACTER; }
}

<MACRO_CONTROL> {
  "%}"                 { updateMacroControlDepth(); popState(); return CrystalTypes.MACRO_CONTROL_END; }
  {WHITE_SPACE}        { return TokenType.WHITE_SPACE; }
  {NEWLINE}            { return CrystalTypes.NEWLINE; }
  {SYMBOL}             { return symbolLiteral(); }
  ":\"" / [^]          { pushState(STRING); return CrystalTypes.SYMBOL_COLON; }
  "verbatim"           { return CrystalTypes.VERBATIM; }
  "if"                 { return CrystalTypes.IF; }
  "else"               { return CrystalTypes.ELSE; }
  "elsif"              { return CrystalTypes.ELSIF; }
  "end"                { return CrystalTypes.END; }
  "for"                { return CrystalTypes.FOR; }
  "in"                 { return CrystalTypes.IN; }
  "unless"             { return CrystalTypes.UNLESS; }
  "while"              { return CrystalTypes.WHILE; }
  "until"              { return CrystalTypes.UNTIL; }
  "rescue"             { return CrystalTypes.RESCUE; }
  "begin"              { return CrystalTypes.BEGIN; }
  "yield"              { return CrystalTypes.YIELD; }
  "require"            { return CrystalTypes.REQUIRE; }
  "true"               { return CrystalTypes.TRUE; }
  "false"              { return CrystalTypes.FALSE; }
  "nil"                { return CrystalTypes.NIL; }
  {CONSTANT}           { return CrystalTypes.CONSTANT; }
  {INSTANCE_VAR}       { return CrystalTypes.INSTANCE_VAR; }
  {CLASS_VAR}          { return CrystalTypes.CLASS_VAR; }
  {GLOBAL_VAR}         { return CrystalTypes.GLOBAL_VAR; }
  {IDENTIFIER}         { return CrystalTypes.IDENTIFIER; }
  // Numbers (float before int since float is more specific with dot) —
  // mirror YYINITIAL: macro conditions compute with hex literals, suffixed
  // integers and floats exactly like plain code (`{% if x == 0x20 %}`).
  {DEC_INT} "." {DEC_INT} (("e" | "E") ("+" | "-")? {DEC_INT})? {FLOAT_SUFFIX}  { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} ("e" | "E") ("+" | "-")? {DEC_INT} {FLOAT_SUFFIX}                    { return CrystalTypes.FLOAT_LITERAL; }
  {DEC_INT} "_"? "f" ("32" | "64")                                                { return CrystalTypes.FLOAT_LITERAL; }
  {INTEGER}            { return CrystalTypes.INTEGER_LITERAL; }
  "'" [^'\\] [^'\r\n] [^'\r\n]* "'" { return TokenType.BAD_CHARACTER; }
  {CHAR_LITERAL}       { return CrystalTypes.CHAR_LITERAL; }
  \"                   { pushState(STRING); return CrystalTypes.STRING_LITERAL; }
  "`"                  { pushState(BACKTICK); return CrystalTypes.COMMAND_BEGIN; }
  "//="                { return CrystalTypes.DOUBLE_SLASH_ASSIGN; }
  "//"                 { return CrystalTypes.DOUBLE_SLASH; }
  "/"                  { if (isRegexAllowed()) { pushState(REGEX); return CrystalTypes.REGEX_BEGIN; } return CrystalTypes.SLASH; }
  "->"                 { return CrystalTypes.ARROW; }
  "=>"                 { return CrystalTypes.DOUBLE_ARROW; }
  "@["                 { return CrystalTypes.ANNOTATION; }
  "@"                  { return CrystalTypes.AT; }
  "("                  { return CrystalTypes.LPAREN; }
  ")"                  { return CrystalTypes.RPAREN; }
  "["                  { return CrystalTypes.LBRACKET; }
  "]"                  { return CrystalTypes.RBRACKET; }
  "{"                  { return CrystalTypes.LBRACE; }
  "}"                  { return CrystalTypes.RBRACE; }
  ","                  { return CrystalTypes.COMMA; }
  "."                  { return CrystalTypes.DOT; }
  ":"                  { return CrystalTypes.COLON; }
  ";"                  { return CrystalTypes.SEMICOLON; }
  "#"                  { return CrystalTypes.HASH; }
  "=="                 { return CrystalTypes.EQ; }
  "!="                 { return CrystalTypes.NEQ; }
  "=~"                 { return CrystalTypes.MATCH_OP; }
  "!~"                 { return CrystalTypes.BANG_TILDE; }
  "<="                 { return CrystalTypes.LTE; }
  ">="                 { return CrystalTypes.GTE; }
  "<"                  { return CrystalTypes.LT; }
  ">"                  { return CrystalTypes.GT; }
  "||"                 { return CrystalTypes.OR_OR; }
  "&&"                 { return CrystalTypes.AND_AND; }
  "|"                  { return CrystalTypes.PIPE; }
  "&"                  { return CrystalTypes.AMPERSAND; }
  "<=>"                { return CrystalTypes.SPACESHIP; }
  "<="                 { return CrystalTypes.LTE; }
  ">="                 { return CrystalTypes.GTE; }
  "**"                 { return CrystalTypes.DOUBLE_STAR; }
  "<<"                 { return CrystalTypes.LSHIFT; }
  ">>"                 { return CrystalTypes.RSHIFT; }
  "^"                  { return CrystalTypes.CARET; }
  "~"                  { return CrystalTypes.TILDE; }
  "%"                  { return CrystalTypes.PERCENT; }
  "="                  { return CrystalTypes.ASSIGN; }
  "+"                  { return CrystalTypes.PLUS; }
  "-"                  { return CrystalTypes.MINUS; }
  "*"                  { return CrystalTypes.STAR; }
  "//="                { return CrystalTypes.DOUBLE_SLASH_ASSIGN; }
  "//"                 { return CrystalTypes.DOUBLE_SLASH; }
  "/"                  { return CrystalTypes.SLASH; }
  "?"                  { return CrystalTypes.QUESTION; }
  "!"                  { return CrystalTypes.BANG; }
  ".."                 { return CrystalTypes.DOTDOT; }
  "..."                { return CrystalTypes.DOTDOTDOT; }
  "::"                 { return CrystalTypes.DOUBLE_COLON; }
  "%"                  { return CrystalTypes.PERCENT; }
  // Percent literals inside macro control tags: `{% for op in %w(+ - * /) %}`
  // (raytracer.cr). Without these, `%w(` splits and a later `/` after an
  // operator (`* /`) lexes as REGEX_BEGIN via isRegexAllowed, derailing the
  // tag and everything after it. Mirrors the YYINITIAL family (same token
  // types, same PERCENT_LITERAL state handling); like the MACRO_INTERPOLATION
  // rule there is no percentStartsMethodName guard. Longest-match keeps
  // `%w ==` (modulo plus variable) and `%` (modulo) on their old paths.
  "%w" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = false;
                          percentWordArray = true;
                          percentAllowEscapes = false;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_WORD_ARRAY_BEGIN;
                        }
  "%W" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = true;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_WORD_ARRAY_BEGIN;
                        }
  "%i" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.SYMBOL_LITERAL;
                          percentInterpolation = false;
                          percentWordArray = false;
                          percentAllowEscapes = false;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_SYMBOL_BEGIN;
                        }
  "%I" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.SYMBOL_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_SYMBOL_BEGIN;
                        }
  "%q" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = false;
                          percentWordArray = false;
                          percentAllowEscapes = false;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  "%Q" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  "%r" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.REGEX_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  "%x" [\(\[\{<|]     {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.COMMAND_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  "%" [\(\[\{<|]      {
                          char c = yycharat(yylength() - 1);
                          percentOpenChar = c;
                          percentCloseChar = closingChar(c);
                          percentDepth = 1;
                          percentTokenType = CrystalTypes.STRING_LITERAL;
                          percentInterpolation = true;
                          percentWordArray = false;
                          percentAllowEscapes = true;
                          pushState(PERCENT_LITERAL);
                          return CrystalTypes.PERCENT_LITERAL_BEGIN;
                        }
  [^]                  { return TokenType.BAD_CHARACTER; }
}

[^]                    { return TokenType.BAD_CHARACTER; }
