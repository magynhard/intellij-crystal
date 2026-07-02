# String Interpolation Spec

## Overview

Crystal supports `#{expression}` interpolation inside certain string-like literals. The plugin must lex and parse the interpolated expression as a full Crystal expression, with proper `STRING_INTERPOLATION_BEGIN` / `STRING_INTERPOLATION_END` tokens and a transition to the `INTERPOLATION` lexer state.

The `INTERPOLATION` state (Crystal.flex:393) handles `{`/`}` brace-depth tracking and returns to the parent state when done. This mechanism is stateless with respect to the parent — it works identically whether entered from `STRING`, `HEREDOC_BODY`, or (after implementation) `PERCENT_LITERAL`.

---

## All Literal Types & Interpolation Support

### Fully Implemented (no changes needed)

| # | Crystal Syntax | Lexer State | BNF Rule | Interpolation? |
|---|---|---|---|---|
| 1 | `"hello #{name}"` | `STRING` (line 382) | `string_expression` (line 773) | ✅ Yes |
| 2 | `:"hello #{name}"` | `STRING` via push (line 193) | `symbol_string_expression` (line 783) | ✅ Yes |
| 3 | `<<-ID ... #{name} ... ID` | `HEREDOC_BODY` (line 502) | `heredoc_literal` (line 776) | ✅ Yes |
| 4 | `<<-'ID' ... #{name} ... ID` | `HEREDOC_BODY` (line 502) | `heredoc_literal` (line 776) | ❌ Raw (correct) |

### Percent Literals — Interpolation

| # | Crystal Syntax | Lexer State | BNF Rule | Interpolation? | Status |
|---|---|---|---|---|---|
| 5 | `%(hello #{name})` | `PERCENT_LITERAL` | `percent_literal` | ✅ Done | ✅ Done |
| 6 | `%Q(hello #{name})` | `PERCENT_LITERAL` | `percent_literal` | ✅ Done | ✅ Done |
| 7 | `%r(pattern #{name})` | `PERCENT_LITERAL` | `percent_literal` | ✅ Done | ✅ Done |
| 8 | `%x(echo #{name})` | `PERCENT_LITERAL` | `percent_literal` | ✅ Done | ✅ Done |

### Percent Literals — No Interpolation (correct by design)

| # | Crystal Syntax | Lexer State | BNF Rule | Interpolation? | Status |
|---|---|---|---|---|---|
| 9 | `%q(hello #{name})` | `PERCENT_LITERAL` | `percent_literal` | ❌ Correct | No change |
| 10 | `%w(hello #{name})` | `PERCENT_LITERAL` | `percent_literal` | ❌ Correct | No change |
| 11 | `%i(hello #{name})` | `PERCENT_LITERAL` | `percent_literal` | ❌ Correct | No change |

### Other Literal Types — Interpolation

| # | Crystal Syntax | Lexer State | BNF Rule | Interpolation? | Status |
|---|---|---|---|---|---|
| 12 | `/pattern #{name}/` | `REGEX` | `regex_expression` | ✅ Done | ✅ Done |
| 13 | `` `echo #{name}` `` | `BACKTICK` | `command_expression` | ✅ Done | ✅ Done |

### Not Supported by Crystal

| # | Crystal Syntax | Status |
|---|---|---|
| 14 | `%W(hello #{name})` | ❌ Does not exist in Crystal (syntax error) |
| 15 | `%I(hello #{name})` | ❌ Does not exist in Crystal (syntax error) |

### Macro Context

| # | Crystal Syntax | Lexer State | Interpolation? | Status |
|---|---|---|---|---|
| 16 | Macro body `#{}` | `MACRO_BODY` (line 511) | ❌ Returns `MACRO_BODY_CONTENT` | ✅ Correct (macros use `{{ }}` / `{% %}`) |

---

## Detailed Behavior by Type

### 1. `"hello #{name}"` — Double-quoted string

**Lexer (STRING state, line 382):**
```jflex
"#{"  { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; }
```

**Parser (string_expression, line 773):**
```bnf
string_expression ::= (STRING_LITERAL | STRING_ESCAPE | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END)+
```

**Token flow for `"hello #{name}":**
```
STRING_LITERAL("hello ")
STRING_INTERPOLATION_BEGIN(#{)
IDENTIFIER(name)
STRING_INTERPOLATION_END(})
```

### 2. `:"hello #{name}"` — Interpolating symbol

Same as double-quoted string — enters `STRING` state via `pushState(STRING)` (line 193). Parser rule: `symbol_string_expression` (line 783).

### 3. `<<-ID ... #{name} ... ID` — Non-raw heredoc

**Lexer (HEREDOC_BODY state, line 502):**
```jflex
"#{"  { if (!heredocRaw) { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; } return CrystalTypes.HEREDOC_CONTENT; }
```

**Parser (heredoc_literal, line 776):**
```bnf
heredoc_literal ::= HEREDOC_START (HEREDOC_CONTENT | STRING_ESCAPE | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END)* HEREDOC_END?
```

### 4. `<<-'ID' ... #{name} ... ID` — Raw heredoc (no interpolation)

`heredocRaw = true` (set at line 207). The `#{` rule on line 502 is guarded by `!heredocRaw`, so it returns `HEREDOC_CONTENT` instead of `STRING_INTERPOLATION_BEGIN`. Correct behavior.

### 5-8. `%(…)` / `%Q(…)` / `%r(…)` / `%x(…)` — Percent literals with interpolation

**Lexer (PERCENT_LITERAL state, lines 447-469):**
```jflex
<PERCENT_LITERAL> {
  .                    { ... return percentTokenType; }  // catch-all: no #{ handling
  "\\" .               { ... }                           // escapes
  {NEWLINE}            { return percentTokenType; }
}
```

**Problem:** The catch-all `.` rule on line 449 matches `#` and `{` individually. No `#{}` rule exists.

**Parser (percent_literal_content, line 780):**
```bnf
private percent_literal_content ::= STRING_LITERAL | STRING_ESCAPE | SYMBOL_LITERAL | REGEX_LITERAL | COMMAND_LITERAL | NEWLINE
```

**Problem:** No `STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END` alternative.

**Token flow for `%(hello #{name})` (current, broken):**
```
PERCENT_LITERAL_BEGIN(%()
STRING_LITERAL(h)
STRING_LITERAL(e)
STRING_LITERAL(l)
STRING_LITERAL(l)
STRING_LITERAL(o)
STRING_LITERAL( )     ← #{ is not recognized
STRING_LITERAL(#)
STRING_LITERAL({)
STRING_LITERAL(n)
STRING_LITERAL(a)
STRING_LITERAL(m)
STRING_LITERAL(e)
STRING_LITERAL(})
PERCENT_LITERAL_END())
```

### 9. `%q(…)` — Non-interpolating string (correct)

Crystal `%q(...)` is equivalent to single-quoted strings — no interpolation. The lexer currently treats it identically to `%(…)` (no `#{}` handling), which is correct. No change needed.

### 10-11. `%w(…)` / `%i(…)` — Word/Symbol arrays (correct)

Non-interpolating by design in Crystal. `%w` uses `STRING_LITERAL` content, `%i` uses `SYMBOL_LITERAL` content. Neither supports interpolation. Verified:
```crystal
x = "world"
%w(hello #{x})  # => ["hello", "#{x}"]
%i(hello #{x})  # => [:hello, :"\#{x}"]
```

### 12. `/pattern #{name}/` — Regex literal

**Lexer (YYINITIAL, lines 296-299):**
```jflex
"/" [^/\r\n]+ "/" [imx]* { if (isRegexAllowed()) { return CrystalTypes.REGEX_LITERAL; }
                            yypushback(yylength() - 1); return CrystalTypes.SLASH; }
```

**Problem:** The entire `/…/` including `#{…}` is consumed as a single opaque `REGEX_LITERAL` token. No lexer state for regex bodies. No `#{}` → INTERPOLATION transition.

**Parser (regex_expression, line 774):**
```bnf
regex_expression ::= REGEX_LITERAL
```

**Impact:** No highlighting, no reference resolution, no type checking inside regex patterns.

**Verification:** Crystal supports regex interpolation:
```crystal
x = "test"
/hello #{x}/  # => /(?-imsx:hello test)/
```

### 13. `` `echo #{name}` `` — Backtick command

**Lexer (YYINITIAL, line 294):**
```jflex
"`" [^`]* "`"  { return CrystalTypes.COMMAND_LITERAL; }
```

**Problem:** The entire `` `…` `` including `#{…}` is consumed as a single opaque `COMMAND_LITERAL` token. No lexer state for command bodies. No `#{}` → INTERPOLATION transition.

**Verification:** Crystal supports command interpolation:
```crystal
x = "WORLD"
`echo hello #{x}`  # => "hello WORLD\n"
```

### 14-15. `%W(…)` / `%I(…)` — Not Crystal

Verified — `%W(...)` and `%I(...)` produce syntax errors in Crystal. Not part of the language. No implementation needed.

---

## Edge Cases

### Escaped `#{}`

Inside interpolating string literals, `\#{` produces a literal `#{` without interpolation:

```crystal
"hello \#{world}"  # => "hello #{world}"
```

**Current behavior:** The `STRING` state has `"#{"` rule on line 382 that matches `#{` before `\\` can escape it. The `\\` escape rules (lines 383-388) match `\\` followed by a specific character, but the sequence `\\#{` is: first `\\` matches `"\\" .` (line 388), then `#{` matches line 382. So the current lexer handles `\\` correctly but `\#` is not handled — `\#` would be consumed by line 388 as a generic escape. This is a pre-existing limitation.

**For percent literals (when implemented):** The same `\#{` behavior should apply.

### Nested interpolation

```crystal
x = "world"
"hello #{"#{x}"}"  # => "hello world"
```

Handled correctly by the `INTERPOLATION` state's `{`/`}` depth tracking. When the inner `#{}` opens, `interpolationDepth` increments; when it closes, it decrements. When depth reaches 0, `STRING_INTERPOLATION_END` is emitted and the state pops back to the parent.

### Braces inside percent literals

```crystal
x = 1
%({hello #{x}})  # => "{hello 1}"
```

The `PERCENT_LITERAL` state tracks brace depth via `percentOpenChar`/`percentCloseChar`. For `%({…})`, `{` increments `percentDepth` and `}` decrements it. But this conflicts with `#{…}` — the `{` after `#` would be matched by the catch-all `.` rule and would increment `percentDepth` if the opener is `{`.

**Fix needed:** The `#{}` rule must be added **before** the catch-all `.` rule in `PERCENT_LITERAL`, so that `#{` is consumed as an interpolation unit, not two separate characters.

### `#{}` inside non-interpolating percent literals

```crystal
%q(#{x})   # => "\#{x}" (literal text)
%w(#{x})   # => ["\#{x}"] (literal text)
```

The `#{}` rule in `PERCENT_LITERAL` must be guarded so it only fires for interpolating types (`%`, `%Q`, `%r`, `%x`), not for `%q`, `%w`, `%i`.

### Multiline percent literals

```crystal
%(
  hello
  #{name}
)
```

Percent literals span multiple lines. Newlines are returned as `percentTokenType` (line 468). The `#{}` rule should work identically across lines.

### Escape sequences inside `%x()` and `%r()`

```crystal
%x(echo #{name}\n)    # \n should be STRING_ESCAPE
%r(pattern#{name}\d)  # \d should be STRING_ESCAPE (for %r)
```

The existing escape rule on line 467 already handles `\\` → `STRING_ESCAPE` for `STRING_LITERAL` and `COMMAND_LITERAL`. After the `#{}` fix, escapes inside interpolation regions will be handled by the `INTERPOLATION` state naturally.

---

## Implementation Plan

### Phase 1: Percent literal interpolation (simple)

**Files:** `Crystal.flex`, `Crystal.bnf`

1. **Lexer — add `percentInterpolation` flag:**
   - New `boolean percentInterpolation` field in `CrystalLexer`
   - Set in each YYINITIAL percent literal rule:
     - `%` → `true`
     - `%Q` → `true`
     - `%r` → `true`
     - `%x` → `true`
     - `%q` → `false`
     - `%w` → `false`
     - `%i` → `false`

2. **Lexer — add `#{` rule in `<PERCENT_LITERAL>` state (before line 449):**
   ```jflex
   "#{"  { if (percentInterpolation) { depthStack.push(interpolationDepth); interpolationDepth = 1; pushState(INTERPOLATION); return CrystalTypes.STRING_INTERPOLATION_BEGIN; } return percentTokenType; }
   "#"   { return percentTokenType; }
   ```

3. **Parser — extend `percent_literal_content` (line 780):**
   ```bnf
   private percent_literal_content ::= STRING_LITERAL | STRING_ESCAPE | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END | SYMBOL_LITERAL | REGEX_LITERAL | COMMAND_LITERAL | NEWLINE
   ```

4. **Regenerate:** `./gradlew generateLexer generateParser`

5. **Tests:** Create `PercentLiteralInterpolation.cr` parser test with:
   - `%(hello #{final} world)` — interpolating
   - `%Q(hello #{final})` — interpolating
   - `%r(pattern #{final})` — interpolating
   - `%x(echo #{final})` — interpolating
   - `%q(no #{interpolation})` — non-interpolating (content tokens, no STRING_INTERPOLATION_BEGIN)
   - `%w(word #{interpolation})` — non-interpolating
   - `%i(sym #{interpolation})` — non-interpolating
   - `%({nested #{x}})` — braces with interpolation
   - `%(multi\nline #{x})` — multiline

### Phase 2: `/regex/` interpolation (complex — new lexer state)

**Files:** `Crystal.flex`, `Crystal.bnf`

1. **Lexer — new `REGEX` state:**
   - Enter from `YYINITIAL` on `/` when `isRegexAllowed()`
   - Handle `#{}` → `INTERPOLATION` (same as STRING)
   - Handle `\\` → `STRING_ESCAPE`
   - Handle closing `/` → exit to `YYINITIAL`
   - Handle `[imx]*` flags after closing `/`

2. **Parser — extend `regex_expression`:**
   ```bnf
   regex_expression ::= REGEX_LITERAL
                      | REGEX_LITERAL_BEGIN (STRING_LITERAL | STRING_ESCAPE | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END)* REGEX_LITERAL_END [REGEX_FLAGS]
   ```

   Alternatively, reuse existing tokens and add a new PSI composite.

3. **Tests:** Create `RegexInterpolation.cr` parser test

### Phase 3: Backtick command interpolation (complex — new lexer state) ✅ DONE

**Files:** `Crystal.flex`, `Crystal.bnf`

1. **Lexer — new `BACKTICK` state:**
   - Enter from `YYINITIAL` on `` ` ``
   - Handle `#{}` → `INTERPOLATION` (with depth stack)
   - Handle `\\` → `STRING_ESCAPE` (including `\\\n` / `\\\r\n` line continuation)
   - Handle `#` → `COMMAND_LITERAL` (lone hash)
   - Handle `` ` `` → exit to `YYINITIAL` → `COMMAND_END`
   - Handle `{NEWLINE}` → `COMMAND_LITERAL` (multiline backtick commands)
   - Handle `[^\`#\\]+` → `COMMAND_LITERAL`

2. **Parser — new `command_expression` rule:**
   ```bnf
   command_expression ::= COMMAND_BEGIN (COMMAND_LITERAL | STRING_ESCAPE | STRING_INTERPOLATION_BEGIN expression STRING_INTERPOLATION_END)* COMMAND_END
   ```
   Updated `literal` to reference `command_expression` instead of the old `COMMAND_LITERAL` token.
   Renamed from `command_literal` to `command_expression` to avoid GrammarKit naming collision (`CrystalElementType("COMMAND_LITERAL")` vs `CrystalTokenType("COMMAND_LITERAL")`).

3. **Tests:** `CommandInterpolation.cr` parser test — simple `` `ls -la` `` and interpolated `` `echo #{name}` ``

---

## File Locations

| File | Path |
|---|---|
| Lexer | `src/main/kotlin/de/magynhard/crystal/lexer/Crystal.flex` |
| Parser | `src/main/kotlin/de/magynhard/crystal/parser/Crystal.bnf` |
| Token types | `src/main/kotlin/de/magynhard/crystal/lexer/CrystalTokenTypes.kt` |
| Generated lexer | `src/main/gen/de/magynhard/crystal/lexer/CrystalLexer.java` |
| Generated parser | `src/main/gen/de/magynhard/crystal/parser/CrystalParser.java` |
| Parser tests | `src/test/kotlin/de/magynhard/crystal/parser/CrystalParserTest.kt` |
| Test data | `src/test/testData/parser/` |
| Plugin XML | `src/main/resources/META-INF/plugin.xml` |
