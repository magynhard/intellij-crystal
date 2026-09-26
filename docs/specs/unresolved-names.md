# Unresolved Names

Spec for the `CrystalUnresolvedName` inspection and the matching hover text:
names that resolve to nothing report `Cannot find 'name'` instead of
silently falling back to `Any (Variable)` hovers.

Severity splits by cause: truly unknown names warn (`WARNING`), names indexed
only outside the current file's require closure warn weakly (`WEAK WARNING`).
Hover shows the same `Cannot find 'name'` text for both.

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
   implicit-self scope of the enclosing type), macros by existence. Type
   **aliases** are indexed declarations too (alias index): a require-visible
   or same-file alias counts as known wherever its name appears — as DOT
   receiver root, namespace root, or type-path segment. Member resolution
   through an alias is unjudgeable until the type session unwraps aliases,
   so calls on an alias receiver stay silent instead of flagging the method
   name.
4. The compiler-builtin or magic-name baseline: `sizeof`, `typeof`,
   `pointerof`, `instance_sizeof`, `offsetof`, `alignof`, `uninitialized`,
   `__DIR__`, `__FILE__`, `__LINE__`, `__END_LINE__`, `__METHOD__`, and the
   `require` pseudo-keyword. These have no `def`/`macro` in the index.
5. Same-file or require-visible constant declaration: a bare `CBA` with a
   `CBA = …` assignment in the current file (live PSI), or a constant
   declaration in the constant index whose file belongs to the current
   file's require closure. `private`/`protected` constants count only in
   their own file, exactly like the compiler.
6. DOT calls whose receiver resolves exactly and whose method set resolves
   (`Methods`, `ImplicitConstructor`, `RecordFallback`, `Accessor`).

## Silence Rules

No diagnostic when the name cannot be judged or the shape is not a plain
unresolved name — mirroring the suppression-first conventions of the
call-argument inspections:

- Require-closure consistency (same lens as dependency-aware completion): a
  name indexed only in files outside the current file's require closure is
  reported as a weak warning — from this file's program view it cannot be
  found. This is deliberately stricter than the Crystal compiler, whose
  top-level namespace is program-global: a file that relies on another entry
  point's transitive requires (without requiring the file itself) will warn
  weakly. Each file must require what it uses, exactly as completion already
  assumes. Truly unknown names (nothing indexed anywhere) warn strongly.
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
  fall back to name-only matches. The receiver root itself is judged with
  the same rules as constants: the prelude baseline applies (`Bytes.new`
  with no SDK is silent), and an alias receiver silences the whole call
  (unjudgeable members) instead of flagging the root or the method name.
  A visible receiver-owned macro heals a macro-only DOT call (`Apfel.essen`
  with `macro essen` in `Apfel` and no `def`): the call is valid but has no
  method resolution target.

## Hover

`Cannot find '<name>'` replaces the `Any (Variable)` fallback for bare
identifiers and bare constants that the inspection would flag, through the
same shared predicate. Resolved hovers are unchanged. Inspection highlights
additionally surface the message via the platform warning tooltip.

## Out Of Scope

- "Did you mean" quickfixes and require-insertion assists.
- `method_missing`-style dynamic dispatch: Crystal has none; macro-generated
  members are covered by the macro-uncertainty suppression above.
