# Unresolved Names

Spec for the `CrystalUnresolvedName` inspection (`WARNING`) and the matching
hover text: names that resolve to nothing report `Cannot find 'name'` instead
of silently falling back to `Any (Variable)` hovers.

## Rule

A bare identifier, call callee, DOT method name, constant, or type-path
segment is **known** when any of the following holds; otherwise the
inspection reports `Cannot find '<name>'` on the name element and hovering
shows the same text. Type paths (`x : Helper`, `Array(Helper)`, unions) use
the constant rule per segment, with the root rule for qualified paths.
1. A local binding is visible: preceding assignment, `for` variable, `rescue`
   binding, grouped `(x = …)` or multi `x, y = …` target, enclosing `def` /
   `macro` parameter, block or proc-literal parameter. Forward references do
   not bind (locals), except hoisted top-level methods.
2. A prelude baseline entry: prelude types (`String`, `Int32`, … — the same
   core list as type completion), prelude top-level methods (`puts`,
   `print`, `p`, `pp`, `gets`, `loop`, `spawn`, `sleep`, `raise`, `exit`,
   …, verified against the distribution's `prelude.cr`), and prelude macros
   (`record`, `property`, `getter`, `setter`, `delegate`, `spawn`, … from
   `macros.cr` and `object/properties.cr`). Always known, even without a
   configured SDK.
3. An indexed declaration is visible through the current file's require
   closure: types via exact file membership, unqualified calls via
   `visibleMethods` + `callableUnqualified` (top-level methods and the
   implicit-self scope of the enclosing type), macros by existence.
4. The compiler-builtin or magic-name baseline: `sizeof`, `typeof`,
   `pointerof`, `instance_sizeof`, `offsetof`, `alignof`, `uninitialized`,
   `__DIR__`, `__FILE__`, `__LINE__`, `__END_LINE__`, `__METHOD__`, and the
   `require` pseudo-keyword. These have no `def`/`macro` in the index.
5. Same-file constant assignment: a bare `CBA` with a `CBA = …` assignment in
   the current file (live PSI). Cross-file non-type constants stay out of
   scope until the grammar separates constant definitions from statement
   assignment (see `indexed-navigation.md` and the `TODO.md` index follow-up).
6. DOT calls whose receiver resolves exactly and whose method set resolves
   (`Methods`, `ImplicitConstructor`, `RecordFallback`, `Accessor`).

## Silence Rules

No diagnostic when the name cannot be judged or the shape is not a plain
unresolved name — mirroring the suppression-first conventions of the
call-argument inspections:

- Require-closure consistency (same lens as dependency-aware completion): a
  name indexed only in files outside the current file's require closure IS
  reported — from this file's program view it cannot be found. This is
  deliberately stricter than the Crystal compiler, whose top-level namespace
  is program-global: a file that relies on another entry point's transitive
  requires (without requiring the file itself) will warn. Each file must
  require what it uses, exactly as completion already assumes.
- Require-gated stdlib names unknown to the index (no SDK: `JSON` with no
  indexed declaration): silent. The index cannot prove absence.
- Incomplete or suppressed DOT receivers, macro-uncertain enclosing types
  (named-method collection incomplete for an implicit-self bare call),
  macro-interpolated callees or receivers: silent.
- Non-code positions: definition names, DOT/DOUBLE_COLON-adjacent segments
  (except the flagged last namespace segment), symbol keys (`sym:`), string
  literals, `require` paths, annotations, `case ... in` patterns (owned by
  `CrystalInvalidInPattern`), macro contexts, macro-call blocks, and arguments
  of macro invocations (`record Config`, `property name` declare API surface
  through expansion), `lib/` and other non-project sources, ECR-lenient
  fragments.
- DOT receivers are never flagged, only DOT method names with exact
  receivers. Receiver-aware resolution stays exact: unknown receivers never
  fall back to name-only matches.

## Hover

`Cannot find '<name>'` replaces the `Any (Variable)` fallback for bare
identifiers and bare constants that the inspection would flag, through the
same shared predicate. Resolved hovers are unchanged. Inspection highlights
additionally surface the message via the platform warning tooltip.

## Out Of Scope

- Cross-file non-type constants (blocked on the constant-declaration index).
- "Did you mean" quickfixes and require-insertion assists.
- `method_missing`-style dynamic dispatch: Crystal has none; macro-generated
  members are covered by the macro-uncertainty suppression above.
