## Why

The `bare_argument` BNF rule has the same greedy consumption bug that was fixed in the `argument` rule (commit `fe40fed`). The second alternative `[IDENTIFIER COLON] bare_expression` uses `parseTokens(0, IDENTIFIER, COLON)` which consumes IDENTIFIER even when COLON doesn't follow, causing binary expressions like `x * scalar` to be misparsed.

This affects DOT-calls with bare arguments: `Vector2D.new(x * scalar, y * scalar)` is parsed as a bare method call `x` with arguments `* scalar, y * scalar` instead of two separate arguments `x * scalar` and `y * scalar`.

## What Changes

- Fix `bare_argument` rule: replace `[IDENTIFIER COLON] bare_expression` with `private named_bare_argument` using `{pin=2}`
- Add `bare_expression` as a fallback alternative for positional arguments
- Add test coverage for bare DOT-calls with binary operators in arguments

## Capabilities

### New Capabilities

_None — this is a bug fix._

### Modified Capabilities

- `bare-argument-parsing`: Bare argument parsing now correctly handles binary expressions like `x * scalar` by preventing greedy IDENTIFIER consumption when COLON is absent.

## Impact

- `Crystal.bnf` — `bare_argument` rule change
- `CrystalParser.java` — regenerated parser
- `CrystalArgumentCountInspectionTest.kt` — new tests for bare DOT-calls
