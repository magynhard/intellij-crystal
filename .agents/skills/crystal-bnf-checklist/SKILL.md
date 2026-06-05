---
name: crystal-bnf-checklist
description: Checklist for adding a new token or rule to Crystal.bnf. Use when adding a new keyword, token type, or grammar rule to the Crystal parser. Triggers on add token, new keyword, BNF rule, modify grammar.
---

# Crystal BNF Token Checklist

When adding a new token/keyword to `Crystal.bnf`, check ALL of these:

## 1. Token Declaration
- Add the new token to the `tokens = [` block (e.g., `MY_TOKEN="MY_TOKEN"`)

## 2. Lexer
- Add the `"keyword"` → `CrystalTypes.MY_TOKEN` rule in `Crystal.flex` YYINITIAL
- If lexer states exist for this context, add the rule there too (STRING, INTERPOLATION, MACRO_INTERPOLATION, MACRO_CONTROL, PERCENT_LITERAL, HEREDOC_BODY, MACRO_BODY)

## 3. Parser Rules
Where should the new token be valid? Add it to every relevant rule:
- `primary_expression` / `bare_primary_expression` — if it's a valid expression
- `statement` — if it's a valid statement
- `top_level_statement` — if valid at top level
- `class_member` — if valid inside class/module bodies
- `lib_member` — if valid inside lib blocks
- `macro_body_element` — if valid inside macro bodies
- `macro_control_token` — if valid inside `{% ... %}` blocks
- `keyword_as_method` — if usable as method name after DOT
- `method_name` — if usable as method name in `def`
- `operator_method_name` — if it's an operator
- `postfix_op` / `bare_postfix_op` — if valid in postfix position
- `argument` / `bare_argument` — if valid as method argument
- Any nested expression chains (range, comparison, etc.)

## 4. Context-Dependent Keywords
If the keyword is **only valid in a specific context** (e.g., `type` inside `lib` blocks), do NOT add it as a global lexer keyword. Instead:
- Use `IDENTIFIER`-based rules in the parser (e.g., `lib_type_alias ::= IDENTIFIER CONSTANT ASSIGN type_reference`)
- The parser context ensures it's only matched where intended

## 5. Regenerate
```bash
./gradlew generateLexer generateParser
```

## 6. Golden Files
- Delete affected `.txt` golden files that will change
- Run the affected tests to regenerate them
- Verify `rg PsiErrorElement` returns no errors
- Re-run to confirm tests pass

## 7. Ordering (PEG)
In PEG alternatives, longer/more specific patterns must come first. Example: `IDENTIFIER COLON bare_expression [ASSIGN bare_expression]` before `[IDENTIFIER COLON] bare_expression`.
