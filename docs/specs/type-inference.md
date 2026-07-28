# Type Inference

## Ownership

`de.magynhard.crystal.analysis` is the single owner of PSI-based type resolution. A
`CrystalTypeResolutionSession` returns either an ordered `CrystalTypeResolution.Known` set or
`Unknown`. Completion, expression inspections, PSI references, and legacy string APIs adapt this
result; they do not call each other to infer types.

Each session owns PSI memoization, method-return memoization and recursion guards, exact type and
method caches, and one cached `CrystalMethodHierarchy`. Runtime lookup uses `CrystalIndexService`
and never scans project files.

## Sequential Variable Flow

Variable lookup composes source-ordered PSI from the active lexical boundary to the use site. Each
statement produces a binding with provenance, fall-through, and exceptional states. Joins merge
only branches that reach the join. A reachable unsupported or unprovable path makes the merged
result `Unknown`; the resolver never picks the first reverse descendant assignment.

- Direct assignments replace the incoming binding.
- `if`, `unless`, `case`, ternary, postfix modifiers, `&&`, and `||` preserve every reachable path.
- Loops include the zero-iteration incoming binding and every observed intermediate body binding.
- A protected `begin` records binding states at potential throw points. Rescue starts from the
  proven exceptional state, so a pure `value = "ready"` before a potentially raising call is visible
  in rescue. Call arguments are evaluated in source order, and the post-argument state is recorded
  at the enclosing call operation, so `consume(value = "ready")` exposes `String` if `consume`
  raises. If argument evaluation or an assignment itself may raise before establishing its result,
  that exceptional state remains `Unknown` instead of being narrowed by a later operation state.
  Else runs only after a normally falling-through body. Ensure transforms outgoing flow but does
  not become the protected expression's value.
- Method and block parameters are incoming bindings. External/internal parameter names use the
  internal body name.
- Local variables stop at the nearest method/macro or file boundary. Block parameters shadow outer
  bindings only inside their block.
- Instance variables stop at the nearest class, module, struct, or enum. Nested and sibling types
  never inherit lexical instance-variable evidence from an outer type.

## Expression Values

Scalar literals resolve to Crystal's default runtime types. Arrays, hashes, and tuples preserve
their structured rendered types, including ordered element/key/value unions. Unknown collection
members make the collection unknown.

Conditional expression values merge only falling-through paths. `if`, `unless`, and `case` share
the same structured execution result, so a terminating arm contributes its return but not an
assignment value. Missing `else` paths contribute `Nil`. Every `elsif`, `when`, `in`, and rescue
path participates in source order; a reachable unknown path makes the result unknown.

Logical operators return values, not a fixed `Bool`:

- Always-truthy left operands make `left && right` return `right` and `left || right` return `left`.
- `Nil` and literal `false` take the inverse paths.
- `Bool` is mixed because it can represent true or false.
- Mixed `Bool`/`Nil` unions retain only reachable short-circuit left alternatives and include the
  right result only when the right operand can execute.

Comparisons resolve to `Bool`. Arithmetic is supported only when both operand sets are the same
known type; unsupported promotion or overload cases remain unknown.

## Reachability And Returns

Statement analysis produces return types, an optional falling-through value, and `fallsThrough`.
An unconditional return stops its statement list. Conditional returns remain reachable alongside
fall-through paths. If every branch of an `if`, `case`, or protected body terminates, later returns
and implicit tails are unreachable and excluded.

Unannotated method results merge every reachable explicit return with the reachable implicit tail.
Method-level rescue, else, and ensure use the same protected-body semantics as `begin`. Ensure
affects termination; its ordinary expression value does not replace the protected value. Direct and
mutual recursion terminate as `Unknown`, and completed method results are memoized per session.

## Type Identity And Hierarchy

Simple constants resolve by ordered lexical identity: the nearest enclosing qualified candidate is
checked first, then each outer candidate, then the global identity. The first exact identity shadows
later candidates. Qualified and absolute references require their exact identity. Generic roots are
normalized by one exact-root utility for receiver, superclass, include, and extend lookup.

`CrystalMethodHierarchy` is session-owned and caches exact declarations, methods by exact type,
edges, metadata, named results, and all-method collections. Named macro uncertainty is
name-sensitive: a controlled `hidden` method does not suppress unconditional `visible`, while an
interpolated unknown method name conservatively affects every named lookup. Hierarchy order is direct declaration,
reverse include/extend exposure order, then superclass traversal. Results carry stable depth,
precedence, exact receiver identity, receiver mode, and one shared canonical signature key.

Named lookup filters the shared hierarchy while preserving name-specific completeness. The
all-method API marks uncertain macro-controlled or cross-file duplicate metadata incomplete.
Implicit-self methods take precedence. One exact top-level method remains a fallback only when the
implicit-self hierarchy has no candidate; ambiguous top-level overloads are unknown.

## Constructors

Constructor and record collision classification is neutral and shared by DOT targets and PSI
references. Ordered lexical candidates choose a record before a type at the same exact identity,
then continue outward only when that identity has neither. An exact class or struct may resolve through
`self.new`, then `initialize`, then implicit construction. Modules and enums are unavailable;
abstract classes are rejected; macro-controlled or incomplete exact declarations are incomplete.
Multiple constructor methods remain a multi-target result for navigation/inspection consumers, but
a single PSI reference resolves them as ambiguous (`null`).
Constructor expressions preserve the exact qualified receiver identity as their instance result.
Record macro fallback remains a separate declaration path before normal type construction where
the call consumer supports records.

## Compatibility APIs

`CrystalTypeInference.inferType(...)` preserves pre-analysis behavior by evidence source:

- Typed parameter annotations return the first union arm with outer generic arguments removed.
- Assignment expression results return the full rendered type, such as `Array(Int32)`,
  `Hash(String, Int32)`, or `Int32 | Nil`.

Union-preserving completion and expression consumers use structured session results or the explicit
preserving adapters. `CrystalExpressionTypeResolver` remains a nullable wrapper over neutral
resolution for existing inspection callers.

## Conservative Limits

- Completed-call overload selection is not argument-aware; multiple exact candidates are unknown.
- Generic type parameters are not substituted through method signatures.
- Nil/type narrowing from conditions is not modeled.
- Proc result inference and custom operator overload resolution are not modeled.
- Cross-file reopening precedence remains incomplete when Crystal load order cannot be proven.
