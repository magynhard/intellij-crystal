# Heredocs in Calls — Marker Representation (v12)

## Summary

How heredoc headers (`<<-ID`) as call arguments are represented in PSI, how the
string bodies bind, and why this shape was chosen. Applies to parenthesized
calls, bare calls, dot-calls and grouped expressions alike.

## Real Crystal semantics (verified with `/usr/bin/crystal`)

- The header line declares ALL arguments and closes the call: the `)` after the
  last delimiter belongs to the call — the string bodies start on the NEXT line.
- Multiple heredocs in one call are valid:
  `assert_diff(<<-FIRST, <<-SECOND)` with two sequential bodies.
- Mixed forms are valid: `process_data(<<-INPUT, 42, <<-EXPECTED, true)`.
- The terminator (e.g. `CRYSTAL`) must stand ALONE on its line. Postfix chains
  after a body (`TEXT).upcase`) are INVALID — the compiler reports
  "Unterminated heredoc".

## Lexer (v12)

1. On `<<-ID` (also `<<-'ID'`): emit a `HEREDOC_START` MARKER token spanning the
   delimiter, enqueue `{id, raw}` into `pendingHeredocs`, and CONTINUE in
   `YYINITIAL` — the remaining header line lexes as ordinary code (commas,
   numbers, strings, further delimiters, the real `RPAREN`).
2. On the next `YYINITIAL` newline: if the queue is non-empty, poll it, switch
   to `<HEREDOC_BODY>` and emit the BODY-OPENER token (also typed
   `HEREDOC_START`, spanning the newline). Otherwise the ordinary newline path
   (incl. macro-body handling) runs.
3. `<HEREDOC_BODY>` consumes content until the line-start terminator matches
   the polled id; on success, further queued entries chain via
   `<HEREDOC_PREAMBLE>` (the newline after the terminator opens the next body).

The old greedy `.+` fold (single second-START token containing the tail) and
the `HEREDOC_TAIL_CLOSE`/`HEREDOC_TAIL_MISC` experiments are REMOVED. The
`HEREDOC_START_LINE` state no longer exists.

## Grammar (Crystal.bnf)

- **Marker inside argument lists:** `private heredoc_marker ::= HEREDOC_START`,
  referenced from `literal` (thus reachable through `argument`, bare arguments
  and grouped expressions). A marker argument's `argument` node contains ONLY
  the `HEREDOC_START` token.
- **Bodies:** `heredoc_literal ::= HEREDOC_START content* [HEREDOC_END]` — the
  class name is kept so existing consumers (type inference "String", hover)
  continue to see `CrystalHerdDocLiteral` for bodies.
- **Attachment points:**
  - Parenthesized calls: `call_args ::= LPAREN NLS argument_list heredoc_bodies? NLS RPAREN`
    — bodies tile BETWEEN the list and the closer (the body opener IS the
    newline after the header line).
  - Bare/assignment statements: `[heredoc_bodies]` suffix on
    `expression_statement` and `assignment`.
- The whole former closeless machinery (`heredoc_closeless_*`, guards,
  recursive pairs) is DELETED: with the real `RPAREN` present, every heredoc
  call parses through the strict paths.

## PSI contract

```
METHOD_CALL_EXPRESSION(assert_diff)
  CALL_ARGS
    ARGUMENT_LIST
      ARGUMENT(rule_a)
      ARGUMENT → EXPRESSION → HEREDOC_START('<<-FIRST')   // marker arg
      ARGUMENT → EXPRESSION → HEREDOC_START('<<-SECOND')
      RPAREN
  HEREDOC_BODIES
    HEREDOC_LITERAL(opener '\n', content…, END 'FIRST')
    HEREDOC_LITERAL(opener '\n', content…, END 'SECOND')
```

**Consumer contract (`CrystalPsiCallArguments`):** read arguments via
`getArguments(callArgs)` — marker arguments are ordinary `CrystalArgument`
nodes, so counting, splat/named handling and type resolution work uniformly.
`CrystalTypeSetResolver` maps a raw `HEREDOC_START` expression to `String`.

## Highlighting (PSI-enforced, v12.2)

- `HEREDOC_CONTENT` → string color; `HEREDOC_START` / `HEREDOC_END` → key
  `CRYSTAL_HEREDOC_DELIMITER` (default: parameter-style; user chose
  "body=string color, delimiters variable-like"). Configurable via Settings
  color page entry "Heredoc delimiter".
- **Enforcement layer:** these colors are applied by `CrystalAnnotator` with
  `enforcedTextAttributes` resolved from the active scheme. The layer lexer's
  incremental restarts lose the expected terminator id (renames, structural
  edits), which previously left bodies/terminators painted as ordinary code
  until a full re-parse (file reopen). PSI-enforced attributes are immune:
  every reparse produces fresh nodes and the annotator re-asserts colors.
- Interpolations inside bodies keep their own colors: enforcement only covers
  `HEREDOC_CONTENT`/`STRING_ESCAPE` leaves whose parent is the
  `CrystalHerdDocLiteral`; interpolation internals (BEGIN/END + inner
  expression) are skipped by the annotator scanner.
- While a header/terminator pair is temporarily MISMATCHED (mid-rename), the
  region honestly renders as code until the pair is consistent again — after
  the second rename colors snap back without reopening the file.

## Chained bodies (multi-heredoc)

Each queued delimiter opens its own body: at every body's terminator the lexer
polls the next entry, sets `heredocId`/`heredocRaw`, and enters
`HEREDOC_PREAMBLE`; the following newline emits the next body opener.
`heredoc_bodies` (a `heredoc_literal+` run) therefore contains one
`CrystalHerdDocLiteral` per header, in argument order — verified up to four
bodies (`MultiHeredocBodies` fixture mirrors the original report).

## Known limitations

- IDE incremental relexes inside/below a multi-heredoc header chain currently
  restart with an EMPTY delimiter queue (queue state is not encoded in the
  int lexer state), so remaining bodies can be lexed as ordinary code until a
  full re-parse. Batch/parser behavior is correct; tracked in TODO.md.

- Postfix chains after a heredoc body are impossible in Crystal itself; the
  completion coverage therefore uses a variable holding the heredoc
  (see `CrystalCompletionTest.testDirectAndGroupedScalarLiteralCompletion`).
- `compute(x = 5, y = 6)` (two comma-separated assignments inside parens) is a
  PRE-EXISTING grammar gap — tracked separately in TODO.md.

## Tests

- `HeredocClosingParenCalls.cr/.txt` — matrix incl. MULTI (two same-line
  delimiters) and MIXED (heredoc + normal args + closer) rows; zero
  `PsiErrorElement`.
- `SpecModulesWithHeredocs.cr/.txt` — real spec-file layout regression.
- `GroupedAssignmentParens.cr/.txt` — grouped-expression route for assignments.
- `CrystalArgumentCountInspectionTest` — complete closeless-style call unflagged;
  missing arg still reported; MULTI heredoc counted.
- `CrystalTypeCheckInspectionTest` — heredoc marker matches `String`; mismatch
  highlights the marker token with "got 'String'".
- `CrystalLexerTest` — EOL-heredoc state hygiene regression.
