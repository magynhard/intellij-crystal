## 1. BNF Grammar Fix

- [x] 1.1 Add `private named_bare_argument ::= IDENTIFIER COLON bare_expression [ASSIGN bare_expression] {pin=2}` to Crystal.bnf
- [x] 1.2 Update `bare_argument` rule to use `named_bare_argument | STAR bare_expression | DOUBLE_STAR bare_expression | bare_expression | OUT IDENTIFIER`
- [x] 1.3 Regenerate parser with `./gradlew generateParser`

## 2. Tests

- [x] 2.1 Add test: bare DOT-call with binary multiplication in arguments (`Vector2D.new(x * scalar, y * scalar)`)
- [x] 2.2 Add test: bare DOT-call with named arguments (`Config.new host: "localhost", port: 8080`)
- [x] 2.3 Add test: bare call with splat argument (`add *args`)
- [x] 2.4 Run full test suite: `./gradlew test`

## 3. Verification

- [x] 3.1 Verify no new PSI elements created (private rule)
- [x] 3.2 Verify parser test golden files unchanged for named arguments
- [x] 3.3 Verify all pre-existing tests still pass (same 11 pre-existing failures)
