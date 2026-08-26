# Blocks and Empty Collection Literals

## Summary

How brace blocks (`{ … }`) and `do … end` blocks bind after method callees, and how this
interacts with the "Empty collection literal" inspection. In Crystal, a `{` **directly
after a method name always starts a block**, never a hash-literal argument. Braces that
appear in *argument position* (after an opening parenthesis, or after a comma in a bare
argument list) are hash literals.

## Binding Rules (verified against the real Crystal compiler)

| Source form | Real Crystal binding | Plugin binding |
|---|---|---|
| `Thread.new { }` | empty block | empty block |
| `spawn { }` | empty block | empty block |
| `f { }` / `f do end` / `f do\nend` | empty block | empty block |
| `f {"a" => 1}` | block (then syntax error at `=>`) | block (statement-level error) |
| `f({"a" => 1})` | hash argument | hash argument |
| `f 1, {"a" => 2}` (after comma) | hash argument | hash argument |
| `h = {} of String => Int32` | standalone typed hash | standalone typed hash |

Verification snippets used (`/usr/bin/crystal run`):

- `f {"a" => 1}` → `Error: unexpected token: "=>"` — Crystal commits to the block, so
  even a syntactically valid hash entry cannot appear here unparenthesized.
- `p f 1, {"a" => 2}` → prints `{1, {"a" => 2}}` — mid-list braces stay hash arguments.
- `p f 1, { }` → `Error: for empty hashes use '{} of KeyType => ValueType'` — an empty
  mid-list `{ }` is still parsed as a hash literal, so flagging it remains correct.
- Empty bodies (`T.new { }`, `T.new do end`) compile and run.

## Grammar Implementation

All changes live in `src/main/kotlin/de/magynhard/crystal/parser/Crystal.bnf`
(PEG semantics: ordered alternatives, no cross-alternative backtracking; lookaheads like
`!LBRACE` decide which alternative may commit).

1. **Empty block bodies** — `block ::= DO [PIPE … PIPE] [statement_list] rescue_clause* … END
   | LBRACE [PIPE … PIPE] [statement_list] RBRACE`. The statement list is optional in
   both variants; an empty body produces a `CrystalBlockImpl` with an empty
   `CrystalStatementListImpl`.

2. **`!LBRACE` guard on bare-argument alternatives.** Without it, an empty `{ }` matched
   the optional inner content of `hash_literal` and was committed as a zero-entry hash
   argument, starving the trailing `[block]`. Applied to all call forms that accept bare
   arguments plus a trailing block:
   - `method_call_expression`: alternative 2 guard became `!DOT !LBRACKET !LBRACE
     !binary_op_lookahead bare_argument_list [block]`.
   - `dot_call_access`: `[call_args | !DOT !LBRACKET !LBRACE bare_argument_list] [block]`
     — covers `obj.method { }`, `Klass.method { }`, chained receiver calls
     (`Benchmark.measure do … spawn { } … end`).
   - `macro_interpolation_call`: same guard added to keep macro-body call parsing mirrored.

3. **Reordering was rejected.** Moving the `(IDENTIFIER | CONSTANT) block` alternatives
   before the bare-argument alternative would break combined forms such as
   `describe App5 do … end`: PEG would commit the block-only alternative without
   consuming the preceding bare argument list, leaving dangling tokens. The lookahead
   guard keeps both paths working because it only rejects the *leading* brace position;
   braces after a comma remain consumable as `bare_primary_expression → hash_literal`.

Note on non-empty leading hashes: before this fix, `.map { |e| yield e }` already bound
as a block because pipes made the content invalid as `hash_entry_list`, causing the
bare-argument attempt to fail. Only forms whose brace content is a valid empty hash
(`{}`, `{ }`) were misbound — exactly the cases users saw reported by the inspection.

## Inspection Interaction

`CrystalEmptyCollectionInspection` reports `CrystalHashLiteral`/`CrystalArrayLiteral`
elements lacking elements or an `of` clause. With corrected block binding, empty braces
in callee position parse as `CrystalBlockImpl` instead of `CrystalHashLiteralImpl`, so:

- `Thread.new { }`, `spawn { }`, `f do end` → no longer flagged (false positive fixed).
- `f 1, { }` → still flagged — matching real Crystal's own error for typeless empty
  hashes in argument position.
- Standalone `h = {}` → still flagged.

Quick fixes are unchanged; they insert ` of Type` / ` of KeyType => ValueType` after the
literal.

## PSI Shape

```
METHOD_CALL_EXPRESSION  spawn
  BLOCK                 { }
    LBRACE              '{'
    STATEMENT_LIST      <empty>
    RBRACE              '}'

DOT_CALL_ACCESS         .new        (receiver = prevSibling 'Thread')
BLOCK                   { }
```

## Tests

- Parser: `src/test/testData/parser/EmptyBlocks.cr` (+ golden `.txt`, must contain no
  `PsiErrorElement`) covers every row of the table above, nested
  `Benchmark.measure do / n.times do / spawn { }`, chained `.to_s` after an empty
  block, and pipe-block regression coverage.
- Inspection: `CrystalEmptyCollectionInspectionTest` gained negative cases
  (`testEmptyBraceBlockNotReported`, `testSpawnEmptyBlockNotReported`,
  `testNestedEmptyBlocksNotReported`) and the positive
  `testMidListEmptyHashStillReported`.
