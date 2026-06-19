## Context

The Crystal IntelliJ plugin's BNF grammar defines `bare_argument` for parsing arguments in bare (parenthesis-free) method calls. The current rule has a greedy consumption bug in its second alternative `[IDENTIFIER COLON] bare_expression`, which uses `parseTokens(builder_, 0, IDENTIFIER, COLON)` with pin=0.

With pin=0, `parseTokens` consumes IDENTIFIER even when COLON doesn't follow. The consumed IDENTIFIER is committed via `marker.done(null)`, and then `bare_expression` starts from the wrong position.

This is the same bug that was fixed in the `argument` rule (commit `fe40fed`) using `private named_argument ::= IDENTIFIER COLON expression {pin=2}`.

**Current `bare_argument` rule:**
```
bare_argument ::= IDENTIFIER COLON bare_expression [ASSIGN bare_expression]
                | [IDENTIFIER COLON] bare_expression
                | STAR bare_expression
                | DOUBLE_STAR bare_expression
                | OUT IDENTIFIER
```

**Current generated code for alternative 1:**
```java
private static boolean bare_argument_1_0(PsiBuilder builder_, int level_) {
    parseTokens(builder_, 0, IDENTIFIER, COLON);  // greedily consumes IDENTIFIER
    return true;  // always returns true
}
```

**Bug manifestation:** `Vector2D.new(x * scalar, y * scalar)` — the `x` in `x * scalar` is consumed by `parseTokens(0, IDENTIFIER, COLON)`, then `bare_expression` starts from `*`, parsing `* scalar` as unary star. The argument becomes `* scalar` instead of `x * scalar`.

## Goals / Non-Goals

**Goals:**
- Prevent greedy IDENTIFIER consumption in `bare_argument` when COLON doesn't follow
- Preserve correct parsing of named arguments (`host: "lol"`)
- Preserve correct parsing of splat arguments (`*args`, `**opts`)
- Add test coverage for bare DOT-calls with binary operators

**Non-Goals:**
- Fixing the `binary_op_lookahead` for bare method calls with variable multiplication (separate issue)
- Changing the `expression` rule behavior
- Refactoring the `bare_argument` rule structure beyond the pin fix

## Decisions

### Use `private named_bare_argument` with `{pin=2}`

Same approach as the `argument` fix. Create a private rule that requires both IDENTIFIER and COLON to match before committing. With pin=2, if COLON fails, the marker is rolled back and IDENTIFIER is NOT consumed.

**Why not expand `binary_op_lookahead`?** Expanding to block `*` followed by IDENTIFIER would also block legitimate `*args` splat arguments in bare calls. The `binary_op_lookahead` is checked at the `method_call_expression` level, not at the `bare_argument` level, so it can't distinguish between splat and multiplication.

**Why add `bare_expression` as fallback?** When `named_bare_argument` fails (no COLON) and `STAR bare_expression` / `DOUBLE_STAR bare_expression` also fail (first token is not STAR/DOUBLE_STAR), `bare_expression` handles positional arguments like `x * scalar`.

### Alternative order matters

```
bare_argument ::= named_bare_argument        // named: host: "lol"
                | STAR bare_expression        // splat: *args
                | DOUBLE_STAR bare_expression // double-splat: **opts
                | bare_expression             // positional: x * scalar, 42, "hello"
                | OUT IDENTIFIER              // out: out x
```

`bare_expression` MUST come after STAR/DOUBLE_STAR because `bare_expression` chains to `bare_unary_expression` which includes `(PLUS | MINUS | TILDE | AMPERSAND | STAR) bare_unary_expression`. If `bare_expression` were tried first, `*args` would match as bare_expression (unary star) instead of `STAR bare_expression`.

## Risks / Trade-offs

- **[Risk]** GrammarKit's `consumeTokens` with pin=2 behavior verified in `named_argument` — same pattern used here → **Mitigation**: identical implementation, tested
- **[Risk]** `bare_expression` fallback could match expressions that should be parsed differently → **Mitigation**: `bare_expression` is already defined as `bare_or_expression [QUESTION expression COLON expression]` which excludes blocks and assignments
- **[Risk]** Existing parser tests might have different AST structure → **Mitigation**: private rules don't create PSI elements, AST structure unchanged for named arguments
