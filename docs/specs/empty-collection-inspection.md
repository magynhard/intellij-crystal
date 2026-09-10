# Empty Collection Inspection + Tight Empty Brackets

## Background

`CrystalEmptyCollectionInspection` flags empty collection literals without a
type annotation: Crystal cannot infer the element type of `[]` / `{}` at the
declaration site, so it errors (`use '[] of Type'`). The inspection visits
`CrystalArrayLiteral` / `CrystalHashLiteral` PSI nodes and reports only when
the node has no `of` annotation and no children beyond brackets/newlines
(`hasElements`, `hasOfKeyword`). It offers a quick fix inserting
` of <space>` / ` of  => `.

## `X[]` is a zero-arity call, not an array literal (v14.x decision)

Reference: crystal `spec/std/number_spec.cr:398` — `ary = Int64[]` is valid and
creates `Array(Int64)`. Crystal's grammar turns `Int64[]` into the
zero-argument call `Int64.[]()`; `Number` declares `macro [](*nums)` in
`src/number.cr:86`, and a splat-args macro call accepts zero arguments.
`crystal tool format` accepts both `a = foo[]` (variable receiver) and
`a = Int64 []` (spaced). Variants like `foo[]?` (`?` directly after empty
brackets) are rejected by the real parser.

**Consequence:** only a standalone `[` `]` (no preceding expression tight
against it) is an empty array literal. Everything else must go through the
call/index postfix path.

## Grammar binding (Crystal.bnf)

The empty-bracket postfix is an **additive alternative**, placed after the
regular `LBRACKET argument_list RBRACKET` alternatives in both
`postfix_op` and `bare_postfix_op`:

```
| &<<isTokenTightAfterPreviousToken>> LBRACKET RBRACKET
```

- **Tight guard**: spaced `Int64 []` keeps its pre-existing binding (a call
  with an empty array literal as its bare argument — see
  `method_call_expression`/`bare_method_call_expression` array-argument
  alternatives). Only tight `Int64[]` / `foo[]` rebinds.
- **PEG ordering**: the non-empty `argument_list` alternative is tried first;
  the empty variant only applies when the brackets contain nothing.
- Standalone `[]` / `[] of T` / `{}` / `{} of K => V` still bind as
  collection literals at the primary level, so the inspection's original
  behavior is unchanged for those shapes.

Pictures: `ary = Int64[]` parses as
`assignment → expression → variable_reference(CONSTANT Int64) LBRACKET RBRACKET`
— no `CrystalArrayLiteral`, hence no inspection hit.

## Out of scope / pinned behavior

- Real Crystal also rejects `foo[]?` — no `[]?` empty-call support was added;
  if a future Crystal version adds it, extend the tight-empty alternative with
  the existing `[QUESTION]` suffix used by the index postfix.
- The zero-arity call currently produces plain tokens under the expression —
  there is no dedicated `CrystalEmptyCallAccess` composite with a reference;
  resolution (`Int64[]` → the `Number` `[]` macro definition) is NOT wired up.
  Called-out follow-up: navigation/hover for `X[]` would need the same shared
  exact DOT-target resolver the DOT-calls use. See `TODO.md`.

## Type inference for the Number `[]` macro family

`X[...]` on a `Number` family receiver is the class MACRO `Number#[](*nums)`
(stdlib `number.cr`), which expands to `Array(X).build(n)` with the elements
cast to `X` — with zero arguments included (`Int64[]` → `Array(Int64)`,
spec/std/number_spec.cr:398). `CrystalTypeSetResolver.bracketCallResolution`
recognizes the exact shape (constant type-root receiver + `LBRACKET`
[`ARGUMENT_LIST`] + `RBRACKET`, nothing else) and resolves:

- **Gate 1** — the receiver normalizes to exactly one type identity via
  `resolveTypeIdentity` (require-aware): variable receivers, `::`-ambiguous
  paths, and receivers with call arguments stay Unknown.
- **Gate 2** — `CrystalMethodHierarchy.reachesSuperclassName(identity,
  "Number")` walks the nominal superclass chain WITH the compiler-imposed
  primitive edges (`Int64 → Int → Number`, `Float64 → Float → Number`), so the
  macro typing applies to the whole Number family, including the `Int`/`Float`
  alias parents.
- Scopes: filled brackets type identically (`Int64[1, 2, 3]` → `Array(Int64)`
  — the macro casts mixed elements). Explicit real-stdlib verification passed
  (`int.cr` + `number.cr`, `require`-chain: `Array(Int64)`).

Non-Number receivers with their own `def self.[]` (`Env[]`, `Path[]`,
`Dir::Glob`-style, custom classes) and all variable receivers STAY Unknown —
honest instead of guessing, consistent with the "unsolved receivers stay
honest" rule. Known follow-ups (see `TODO.md`): `X[]` navigation to the macro
definition, and the `Slice`/`StaticArray` family (`Slice[1, 2]` is its own
stdlib `[]` family, not Number).

## Covered by

- Parser golden test: `src/test/testData/parser/EmptyCallBrackets.cr|.txt`
  (`CrystalParserTest.testEmptyCallBrackets`) — no `PsiErrorElement`.
- Inspection regression tests in `CrystalEmptyCollectionInspectionTest`
  (`testEmptyBrackets*`: not reported) plus the original reported/not-reported
  matrix (still intact).
- Inference tests in `CrystalTypeInferenceTest` (`*TypedBracketCall*`:
  hermetic compiler-imposed chain fixture, empty/filled/Float64/Number-gate/
  variable-receiver negatives).
- Stub version: PSI/index semantics changed →
  `CrystalParserDefinition.FILE.getStubVersion()` is 17.
