# Type Inference

## Ownership

`de.magynhard.crystal.analysis` is the single owner of PSI-based type resolution. A
`CrystalTypeResolutionSession` returns either an ordered `CrystalTypeResolution.Known` set or
`Unknown`. Completion, expression inspections, PSI references, and legacy string APIs adapt this
result; they do not call each other to infer types.

Each session owns PSI memoization, method-return memoization and recursion guards, exact type and
method caches, and one cached `CrystalMethodHierarchy`. Runtime lookup uses `CrystalIndexService`
and never scans project files.

Each session also captures exactly one immutable effective-source snapshot from its PSI context.
Neutral StubIndex candidate lists for types, named methods, and methods by enclosing type are
filtered against that snapshot before exact identity, hierarchy metadata, ambiguity, duplicate
signature, or completeness logic runs. The visible program consists of the current file, its
forward transitive require closure, and the configured prelude closure. Reverse dependents,
unrequired sibling files, and optional reopenings outside that set cannot affect resolution.

Consumers must construct sessions from the actual PSI location being analyzed. Completion passes
its completion position for both value and type-object receivers; a `Project` or project directory
is not a valid substitute because it has no file load context. Missing or incomplete effective
sources remain authoritative and do not trigger an all-project fallback.

## Sequential Variable Flow

Variable lookup composes source-ordered PSI from the active lexical boundary to the use site. Each
statement produces a binding with provenance, fall-through, and exceptional states. Joins merge
only branches that reach the join. A reachable unsupported or unprovable path makes the merged
result `Unknown`; the resolver never picks the first reverse descendant assignment.

- Direct assignments replace the incoming binding.
- `if`, `unless`, `case`, ternary, postfix modifiers, `&&`, and `||` preserve every reachable path.
- Expression-position `return`, `break`, and `next` evaluate their ordered values and then terminate
  their path. Assignments in those values can reach rescue or other abrupt continuations but never
  leak into a later fall-through variable state, including logical, ternary, grouped, call-argument,
  and indexed-assignment RHS positions. Logical chains retain cumulative truthiness with `&&`
  precedence over `||`. Return exits propagate outward; break exits join the enclosing loop's outgoing
  state; next exits feed its subsequent iterations. A protected `begin`'s ensure body transforms its
  normal, exceptional, and abrupt continuations before they reach their destinations.
- Indexed assignments evaluate receiver/index components before the RHS. Compound forms add a
  potentially raising getter phase, every falling RHS path can reach a potentially raising setter,
  and `||=`/`&&=` also retain the path that skips the RHS. A postfix rescue handler receives the
  merged states from every reachable failure phase; a non-raising ordinary assignment does not
  make its rescue handler reachable.
- Destructuring assignments (`x, y = …`) evaluate every right-hand value first and then bind
  one variable per local target with assignment provenance. Comma-separated values map
  positionally (exact count modulo splat); array right-hand sides contribute their element
  type to every target because destructuring indexes; tuple right-hand sides resolve
  positionally; any other single right-hand side resolves through its indexed element type
  (`Array(T)` element, `Tuple(...)` positional). Splat targets collect the rest (`Array`
  from arrays, `Tuple` preserving order from tuples and multi-value right-hand sides).
  Count-mismatched multi-value forms, too-small tuples, and non-indexable single right-hand
  sides stay `Unknown`, as do indexed/member/macro targets as bindings. The whole
  multi-assignment expression evaluates to its last target's type.
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

Index reads resolve through the receiver's collection type: `Array(T)`, `Slice(T)`, and
`StaticArray(T, N)` yield `T`, `Hash(K, V)` yields `V`, `String` yields `Char`, and `Tuple(...)`
yields the literal-indexed element (or the union of every element for a dynamic index). A range or
multi-argument index yields the container again (`arr[1..2]` is an `Array`, `str[1, 2]` a `String`),
and the nil-safe `[]?` postfix adds `Nil` to the element (`arr[i]?` is `T | Nil`). Chained indexes
(`matrix[row][col]`) resolve one step at a time. Collections outside the name table (`Deque(T)`,
the `Indexable` modules, and user types with their own `def [](...)`) resolve the element through
the shared exact call resolver's `[]` overload return annotation, so no name-only guess is made.
Unknown receivers and unsupported collections stay `Unknown`; the resolver never falls back to the
receiver type for an element read. An indexed receiver seeds a following dot-call chain with the
element type, so `lines[0].strip` and `by_name["a"].value` resolve through the element's methods
instead of degrading to `Unknown`.

Crystal's nilable shorthand `T?` is expanded to `T | Nil` while annotations, parameters, and return
types are parsed, so a nilable generic (`Array(String)?`) participates in element extraction and
union compatibility instead of staying an opaque pseudo-type name. Because condition-based narrowing
is not modeled, a nilable collection indexed without `?` still yields the non-nil element (the `Nil`
union member is dropped by the element mapping), and `arr[i]?` keeps `T | Nil`.

Conditional expression values merge only falling-through paths. `if`, `unless`, and `case` share
the same structured execution result, so a terminating arm contributes its return but not an
assignment value. Missing `else` paths contribute `Nil`. Every `elsif`, `when`, `in`, and rescue
path participates in source order; a reachable unknown path makes the result unknown.

Simple indexed assignments have the value of their RHS. Postfix `if`/`unless` adds `Nil` for the
skipped path, and postfix `rescue` merges the successful RHS with the handler value. Compound
indexed assignment values remain `Unknown` until the getter and operator can resolve exactly.

Logical operators return values, not a fixed `Bool`:

- Always-truthy left operands make `left && right` return `right` and `left || right` return `left`.
- `Nil` and literal `false` take the inverse paths.
- `Bool` is mixed because it can represent true or false.
- Mixed `Bool`/`Nil` unions retain only reachable short-circuit left alternatives and include the
  right result only when the right operand can execute.

Equality and relational comparisons resolve to `Bool`. Spaceship `<=>`, regex
match `=~`, and not-match `!~` dispatch through the same exact overload
resolver: an applicable annotated overload decides the result (`String#=~` is
`Int32 | Nil`), while an unannotated or ambiguous overload stays `Unknown`
instead of a token-based boolean guess.

Overloadable arithmetic and bitwise operators dispatch exactly like method
calls (`left op right` binds as `left.op(right)`) with the flattened operand's
own postfix chain resolved before the dispatch (`Time.utc - date.to_utc` calls
`Time#-(Time) : Time::Span`, never a `to_utc` on the operator result). An
overload is applicable when one of its annotated parameter type sets
intersects the right operand's type set; the distinct merged return
annotations of all applicable overloads decide the result. Crystal's
precedence hierarchy selects the applied dispatch order, so
`a - b * c` types as `Moment#-(Offset)` when the multiplication returns
`Offset`. Receivers without any applicable overload degrade to `Unknown`
exactly like a crystal compile error; the compiler-imposed numeric primitive
family retains the plain-merging semantics for same-typed operands without
annotated overloads. Union-mixed or unresolvable operands stay unknown.

## Reachability And Returns

Statement analysis produces return types, an optional falling-through value, and `fallsThrough`.
An unconditional return stops its statement list. Conditional returns remain reachable alongside
fall-through paths. If every branch of an `if`, `case`, or protected body terminates, later returns
and implicit tails are unreachable and excluded.

Unannotated method results merge every reachable explicit return with the reachable implicit tail.
Comma-separated return values resolve as `Tuple(...)` in source order. For a postfix return,
assignments in those values belong only to the returning branch; the guard-false fallthrough
state includes effects from evaluating the guard but not from unevaluated return values.
Postfix rescue evaluates abrupt values before its handler and remains abrupt after a successful
handler; it never exposes a false fall-through path to a method's implicit tail.
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
interpolated unknown method name conservatively affects every named lookup. Macro interpolation in
a method body does not affect its method-name metadata. All-method completion is best-effort: it
omits macro-controlled methods and dynamically named methods while retaining every certain method.
Named lookup remains strict and incomplete when its requested name is uncertain. Hierarchy order is
direct declaration, reverse include/extend exposure order, then superclass traversal. Results carry
stable depth, precedence, exact receiver identity, receiver mode, and one shared canonical signature
key.

Named lookup filters the shared hierarchy while preserving name-specific completeness. The
all-method API remains incomplete for ambiguous hierarchy edges and cross-file duplicate metadata,
but not merely because an unrelated method name is macro-controlled.
Implicit-self methods take precedence. One exact top-level method remains a fallback only when the
implicit-self hierarchy has no candidate; ambiguous top-level overloads are unknown.

Crystal's compiler-imposed hierarchy is represented as neutral exact hierarchy edges. The stdlib
source never declares these superclasses, so they are implicit (verified against the compiler):
signed `Int8`/`Int16`/`Int32`/`Int64`/`Int128` and unsigned `UInt8`/`UInt16`/`UInt32`/`UInt64`/
`UInt128` inherit `Int` (the stdlib source declares no abstract `UInt`; `UInt32.superclass == Int`),
`Float32`/`Float64` inherit `Float`, `Int` and `Float` inherit `Number`, and
`Number`/`Struct`/`Enum`/`Bool`/`Char`/`Symbol` inherit `Value`, while `Value` and `Reference`
inherit `Object`. A `class` or `struct` declaration without a `superclassClause` inherits the
compiler-imposed base — `Reference` for classes, `Struct` for structs — so every resolved type
reaches `Object` and methods written inside a reopened `class Object` (e.g. `Object#to_json : String`
from `require "json"`, the target a bare `{…}.to_json` call dispatches to) are exposed to every
receiver for lookup, navigation, argument validation, and completion. User-defined `enum Status`
declarations inherit the `Enum` base (the compiler-imposed parent — NOT `Struct`; verified via
`Status < Enum`), so enum receivers reach `Enum` (to_s/hash/<=>) and through `Value` the `Object`
root (`{…}.to_json` from `require "json"`). An enum's `: Type` suffix (e.g. `enum Color : UInt8`)
is the underlying storage type and never treated as a superclass edge. `Object` itself stays the
hierarchy root: it receives no implicit edge and cannot self-loop. Modules cannot inherit and keep
no superclass. The implicit base edge degrades gracefully: when the base declaration (`Reference`,
`Struct`, `Enum`, `Value`, `Object`) is not indexed in the caller's require closure, the edge is
dropped and the type stays chainless but complete, exactly like a type without any hierarchy.
Traversal uses the same visited set as explicit hierarchy traversal and remains cycle-safe. The
edge contributes methods only when the parent declaration is indexed.

## Constructors

Constructor and record collision classification is neutral and shared by DOT targets and PSI
references. Ordered lexical candidates choose a record before a type at the same exact identity,
then continue outward only when that identity has neither. An exact class or struct combines explicit
`self.new` methods with the implicit `new` overloads forwarded to every `initialize`; only an empty
combined set yields implicit zero-argument construction. Modules and enums are unavailable;
abstract classes are rejected; macro-controlled or incomplete exact declarations are incomplete.
Multiple constructor methods remain a multi-target result. `CrystalDotCallReference.multiResolve()`
returns every exact overload for IntelliJ's navigation chooser, while `resolve()` returns a target
only when exactly one remains. The same polyvariant rule applies to regular static and instance
methods.
Constructor expressions preserve the exact qualified receiver identity as their instance result.
Record macro fallback remains a separate declaration path before normal type construction where
the call consumer supports records.

## Compatibility APIs

`CrystalTypeInference.inferType(...)` preserves pre-analysis behavior by evidence source:

- Typed parameter annotations return the first union arm with outer generic arguments removed.
- Assignment expression results return the full rendered type, such as `Array(Int32)`,
  `Hash(String, Int32)`, or `Int32 | Nil`.

Union-preserving completion and expression consumers use structured session results directly.
`CrystalExpressionTypeResolver` remains a nullable wrapper over neutral resolution for existing
inspection callers; unused union-preserving compatibility adapters are not retained.

Unsuffixed decimal, hexadecimal (`0x`), octal (`0o`), and binary (`0b`) integer literals retain
unsuffixed numeric metadata so compatibility checks can apply Crystal's literal autocasting rules.
An explicit integer suffix such as `_i64` or `_u16` clears that metadata and preserves the declared
type.

## Conservative Limits

- Completed-call overload selection is not argument-aware; multiple exact candidates are unknown.
- Generic type parameters are not substituted through method signatures.
- Nil/type narrowing from conditions is not modeled.
- Proc result inference is not modeled.
- Cross-file reopening precedence remains strict and incomplete when multiple relevant declarations
  inside the effective source snapshot have an order that the index cannot prove. Reopenings outside
  the current file's prelude-plus-forward-require boundary do not participate at all.
- Declarations outside the effective source snapshot are ignored rather than used to resolve an
  otherwise missing type or method.
- Nonphysical injected Crystal PSI has no exact Crystal caller file. It therefore receives only the
  configured prelude closure. Core literal methods remain available inside ECR, but project, shard,
  current-host, reverse, and sibling context is not inferred. An all-project fallback is forbidden.
