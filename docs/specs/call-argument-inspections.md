# Call Argument Inspections

Behavioral specification for validating Crystal method call arguments against `def` signatures.

## Scope

`CrystalArgumentCountInspection` validates calls to methods declared with `def`. It reports missing required arguments, excess arguments, and unknown named arguments when the call can be resolved to one or more exact method definitions.

The same resolution, overload, and diagnostic rules apply to every supported call syntax:

```crystal
process()
process value
process
Processor.process(value)
Processor.process value
Processor.process
processor.process(value)
processor.process value
processor.process
```

Parenthesized, bare-argument, and argumentless calls differ only in how their arguments are extracted from PSI. Syntax must not change receiver resolution, hierarchy lookup, overload applicability, constructor precedence, or suppression.

`CrystalTypeCheckInspection` matches named arguments against the parameter's call-site name. Constructor instance-variable assignment shorthand such as `initialize(@age : Int32)` derives that name by removing `@`, so `Boo.new age: value` must validate `value` against `Int32` exactly like `initialize(age : Int32)`. This shorthand is distinct from Crystal's separate `external internal` parameter-name syntax.

FFI functions declared with `lib fun` are outside the current inspection scope because they require separate declaration indexing and call resolution.

## Signature-Only Requirement Model

Whether an argument is required is determined exclusively from the method signature. Assignments, nil checks, fallback expressions, type annotations, and all other statements inside the method body have no effect on caller requirements.

```crystal
def add(x, y)
  y ||= 0
  x + y
end

add 5 # Missing required argument: y
```

The body assignment does not make `y` optional. The signature must declare the default:

```crystal
def add(x, y = 0)
  x + y
end
```

## Parameter Classification

| Signature syntax | Classification | Valid argumentless call |
|------------------|----------------|-------------------------|
| `x` | Required | No |
| `x : Int32` | Required | No |
| `x : String?` | Required | No |
| `x = default_value` | Optional | Yes |
| `x : Type = default_value` | Optional | Yes |
| `x : Type? = nil` | Optional | Yes |
| `*args` | Optional variadic | Yes |
| `**options` | Optional variadic | Yes |

A type annotation never changes arity. In particular, a nilable parameter still requires an explicit argument unless the signature assigns a default:

```crystal
def greet(name : String?)
end

greet        # Missing required argument: name
greet nil    # Valid
greet "Ada"  # Valid
```

An explicit default value is the only way to make a regular parameter optional:

```crystal
def greet(name : String? = nil)
end

greet # Valid
```

Splat and double-splat parameters accept zero arguments themselves. They do not make other regular parameters optional:

```crystal
def log(level, *messages)
end

log # Missing required argument: level

def configure(mode, **options)
end

configure # Missing required argument: mode
```

Combined signatures retain the same classification rules:

```crystal
def configure(nilable : String?, optional = 1, *rest,
              named_required, named_optional = 2, **options)
end

configure # Missing required arguments: nilable, named_required
```

Block parameters are excluded from argument-count calculation. Block-presence validation is a separate concern.

## Parameter Ordering

Required positional parameters precede optional positional parameters. Parameters after a named-only separator or positional splat are named-only. A named-only parameter without a default remains required and must be reported when an argumentless call omits it:

```crystal
def build(required, optional = 10, *rest, named_required, named_optional = 0)
end

build # Missing required arguments: required, named_required
```

Full enforcement of positional use after a named-only separator and signature ordering is tracked separately in `TODO.md`.

## Call Discovery And Ownership

Argumentless calls without parentheses do not contain an argument-list PSI element:

- A direct call such as `greet` is represented as `CrystalVariableReference`.
- A receiver call such as `Greeter.greet` contains `CrystalDotCallAccess` with neither `CrystalCallArgs` nor `CrystalBareArgumentList`.

The inspection must visit these PSI forms directly and evaluate them with an empty argument list. It must not change the grammar or reinterpret variable PSI globally. Each syntactic call site is inspected exactly once:

- A `CrystalVariableReference` is eligible when it is not already the method name of an explicit call and is not a receiver or namespace segment. Ownership checks skip both whitespace and explicit newline tokens so multiline DOT chains remain receiver calls. An eligible reference may be nested as an operand, assignment value, return value, or argument because Crystal permits argumentless method calls in expression positions.
- A `CrystalDotCallAccess` owns an argumentless DOT-call only when both `callArgs` and `bareArgumentList` are absent.
- Parenthesized and bare-argument calls remain owned by their call-expression or argument-list visitor paths. Their nested `CrystalDotCallAccess` and method-name leaves must not create duplicate diagnostics.

Direct variable references are checked only when a local-only resolver does not identify a regular parameter, block parameter, or preceding local assignment and dedicated StubIndex queries do not identify a type declaration or same-named macro. Parameter shadowing uses every directly declared identifier in a destructured parameter and uses only the internal declaration name when an external call-site name is present. The inspection never invokes general reference resolution or `CrystalMethodIndex` for this path. This preserves local/declaration shadowing and prevents a same-named indexed method from producing a false positive. Other declaration kinds and full Crystal call-precedence modeling remain outside this focused change.

After local-shadow filtering, direct calls query only `CrystalTopLevelMethodIndex`. Instance methods and class methods enclosed by unrelated types are never candidates for an unqualified argumentless call. All same-named top-level overloads are evaluated. The lookup must use StubIndex and must not scan project files. Unsupported inherited or otherwise inexact unqualified resolution remains tracked in `TODO.md`.

## DOT-Call Receiver Resolution

Every DOT-call must resolve its receiver to one distinct, exact type identity before method candidates are considered. Resolution uses only these sources:

- A constant, qualified constant, or absolute constant receiver resolved by lexical namespace rules. Explicit qualified and absolute receivers retain their written identity; a simple constant must intersect type-index results with its enclosing lexical namespace prefixes, including the global prefix, to produce one distinct qualified identity.
- The nearest preceding assignment to a local variable in the current lexical scope, provided that assignment resolves to one exact type. A later assignment is irrelevant. A nearer reassignment replaces all older receiver information at that call site; if the nearer assignment cannot resolve exactly, the call is suppressed instead of falling back to an older assignment.
- A method or block parameter with one exact type restriction.
- An instance variable with an exact declared type, including a typed instance-variable parameter.
- An instance variable whose relevant assignments consistently infer the same exact type.

Relevant instance-variable assignments are all assignments to that instance variable within the current enclosing type declaration, regardless of source order or control-flow branch. Assignments inside nested type declarations and assignments from inherited type bodies are excluded. Every relevant assignment must resolve to the same exact type; an unknown, ambiguous, union, nilable, or conflicting assignment suppresses resolution rather than selecting a type by proximity or source order. Support for inherited instance-variable declarations and assignments is deferred in `TODO.md`.

Transparent parentheses do not change receiver identity. A grouped receiver is transparent only when it contains one expression whose complete structure is a local/instance-variable access or a constant path composed of an optional leading `::`, one constant root, and namespace accesses. The resolver preserves the full written qualified or absolute path through nested grouping. Assignment, comma, conditional, union, nilable, call-valued, or other composite expressions are not transparent and remain suppressed.

Constant receivers search class methods. Inferred value receivers search instance methods. Top-level methods, instance methods on unrelated types, and class methods on unrelated types never enter the candidate set. Resolution and hierarchy lookup use StubIndex-backed declarations across project and synthetic-library scopes and must not scan project files or fall back to a method name alone.

The receiver's exact type and its ancestors form one hierarchy lookup. All exact same-named overloads from that hierarchy form the candidate set and are evaluated independently. An unrelated declaration must not enter the set, and a rejecting declaration at a nearer hierarchy level must not block an inherited overload that accepts the call.

`CrystalDotCallTargetResolver.resolve(access)` is the shared target-resolution boundary. `CrystalCallExtractor` derives one descriptor from the owning `CrystalDotCallAccess`, containing the exact receiver PSI, normalized receiver spelling, method-name PSI/name, and the argument holder. The access itself is the holder for argumentless calls; `CrystalCallArgs` and `CrystalBareArgumentList` remain the holders for parenthesized and bare calls.

Hierarchy traversal carries both exact qualified type identity and method exposure mode. Direct declarations are searched first, then later `include`/`extend` edges before earlier edges, and finally the superclass while preserving static or instance receiver mode. An `include` exposes module instance methods to instance lookup; an `extend` exposes module instance methods to static lookup. Metadata is combined across exact type reopenings. Every simple-name StubIndex result is filtered back to its declaration's qualified identity before it can contribute a type, edge, or method.

Hierarchy completeness is part of resolution. A missing, ambiguous, unsupported, or conflicting relevant superclass/include/extend identity suppresses the call instead of returning a partial candidate set or an implicit constructor. Edges for the other receiver mode are irrelevant: unresolved `extend` metadata does not suppress instance lookup, and unresolved `include` metadata does not suppress static lookup.

Qualified enclosing declarations contribute every namespace prefix. Inside `class Outer::Runner`, a simple `Service` therefore considers `Outer::Runner::Service`, `Outer::Service`, and global `Service`. The same candidate generation applies to constant calls, typed parameters, local constructor assignments, and instance-variable annotations.

`extend self` exposes the exact type's instance methods to its static receiver while retaining direct `def self.name` methods. Legal visibility wrappers around include/extend statements do not hide those edges. Methods or relevant edges inside macro-control regions are not unconditional evidence and make the affected hierarchy lookup incomplete. The same rule applies when an exact class/module/struct declaration or reopening is itself enclosed by a file-level or type-level macro-control sibling region.

Nearest-first candidate deduplication uses a resolver-specific structural signature rather than completion display text. Explicitly delimited fields retain parameter kind, separate external/internal names, normalized type restrictions (including direct `type_union` restrictions on anonymous block parameters), named-only position, and required/default shape. Default expression contents do not participate. Within one file, later exact type methods and repeated include/extend edges take precedence. Identical signatures from exact reopenings in different files suppress because require/load precedence is not indexed; callable-distinct cross-file overloads remain candidates. Multiple relevant include/extend edges contributed by different files also suppress until their load precedence can be proven.

Task 3 returns exact candidate definition sets only. Argument applicability and uniquely closest rejecting-overload selection remain the responsibility of Task 4, after target resolution succeeds.

## Constructor Resolution

Calls to `.new` use one constructor-specific overload pool for every call syntax. Against the exact receiver's applicable hierarchy, combine:

1. All explicit `def self.new` definitions, including inherited definitions.
2. All `initialize` definitions, including inherited definitions, because Crystal's compiler exposes each initializer through an implicit `new` forwarder.

Explicit `self.new` methods are ordered first for stable navigation and presentation. They coexist with callable-distinct initializer-backed overloads, but shadow an initializer forwarder when one of that initializer's default-expanded dispatch signatures is identical. Positional parameter names and default values do not distinguish dispatch signatures; parameter kinds, type restrictions, required blocks, and named-only call names do. Only when the resulting pool is empty does the resolver provide implicit zero-argument construction.

A generic constructor receiver such as `Box(Int32).new` resolves to the exact root identity `Box` before applying this precedence. The direct constructor target and any downstream local assignment use the same structural extractor. Every generic argument must itself be a constant-only type root; call-valued, union, nilable, or otherwise composite arguments suppress resolution. Record/type collisions use the shared exact-identity constructor precedence described below.

The `self.new` definition-set search traverses direct exact types and static superclasses only. It does not traverse `extend` edges or become incomplete from unresolved or macro-controlled extends, because extended module instance methods named `new` are not constructor overloads. The `initialize` search retains normal instance superclass/include hierarchy semantics. Both searches must be complete before the combined pool is authoritative. Ordinary static method calls continue to treat unresolved relevant extend edges as incomplete.

The inspection evaluates the complete combined overload pool and accepts the call when any explicit `self.new` or initializer-backed overload accepts. This is required for stdlib types such as `Deque(T)`, whose explicit `self.new(array)` and `self.new(size, &block)` overloads coexist with `initialize`, `initialize(initial_capacity)`, and `initialize(size, value)`; `Deque(Int32).new` therefore resolves through the parameterless initializer. When no overload accepts, the inspection reports against its uniquely closest overload. Implicit construction supplies an exact zero-argument signature only when both explicit definition sets are empty; positional or named arguments are excess, resolved splat and double-splat counts are excess on the method name, unresolved expansions suppress validation, and a block pass does not count as an argument.

Implicit construction is only trustworthy when the receiver's declaring source parsed cleanly. A grammar gap that breaks a stdlib file (historically: setter definitions like `def host=` in `uri.cr`) can drop the type's `initialize` from the index while its type declaration survives, producing false "expected at most 0" reports on valid constructor calls. The stdlib source parse canary (`CrystalStdlibSourceParseTest`) guards this class of root cause directly; see method-definitions-and-bare-calls.md for the grammar rules. The same file also documents the macro-uncertainty rule: macro-generated sibling names no longer suppress explicitly written definitions, so constructors in classes like `HTTP::Client` (which generates methods via `{% for %}`) resolve to their textual `def self.new`/`initialize` overloads.

Every constant `.new` spelling delegates to the shared neutral constructor resolver. Simple constants select the nearest lexical identity across current-file records and exact indexed types; qualified and absolute constants use their exact written identity. A record produces `RecordFallback` when it occupies the selected identity. When a record and indexed type share that exact identity, the record wins before class/struct constructor lookup. A nearer nested class or struct therefore takes precedence over a farther global record, while a nearer nested record takes precedence over a farther indexed type. Inferred record value receivers remain unsupported; records are not treated as ordinary indexed classes.

Record fallback retains exact identity. A record nested as `Other::Config` cannot satisfy top-level `Config.new`, while simple `Config.new` inside `Other` and explicit `Other::Config.new` both select that record. A record declaration inside a file-level or type-level macro-control region is conditional evidence and suppresses resolution when its identity would otherwise be selected. An unrelated `A::Config` does not suppress exact class `B::Config`. An exact abstract class is not constructible; `.new` suppresses rather than producing an implicit constructor.

## Suppression

Argument diagnostics require exact resolution. The inspection emits no argument-count or named-argument diagnostic when the target depends on any of the following:

- An unknown or ambiguous receiver identity.
- A union or nilable receiver type, until control-flow narrowing supplies one exact non-nil type.
- Conflicting local-variable receiver information that prevents one exact nearest assignment from being resolved.
- Conflicting instance-variable assignment types.
- A class-variable receiver.
- A record instance method until record values use exact generated-signature resolution.
- A macro-interpolated receiver, method name, or constructor target.

Macro interpolation contained only inside an argument does not suppress an otherwise exact target. Task 4 decides whether and how that argument can be validated.

Unknown methods and calls with no exact receiver-specific declaration are likewise suppressed, except that constructor resolution can produce the implicit zero-argument signature defined above. A resolved declaration may still reject the supplied arguments and produce a diagnostic. The inspection must prefer no diagnostic over a name-only guess.

Candidate discovery applies cheap PSI and name gates before reference or index resolution. It performs only direct StubIndex queries and introduces no project-wide file scans.

## Overloads

Every overload in the exact candidate set is evaluated independently. A call is valid when at least one overload accepts its positional and named arguments. For example, an argumentless call is valid when at least one overload accepts zero arguments:

```crystal
def process(value)
end

def process
end

process # Valid
```

When no overload accepts the supplied arguments, the inspection reports from the uniquely closest overload using the existing overload ranking. Deterministic selection between equally ranked overloads with different parameter names remains deferred in `TODO.md`.

## Diagnostics

Missing arguments use the existing message format:

```text
Missing required argument(s): 'name', 'age'
```

The diagnostic uses `ProblemHighlightType.GENERIC_ERROR` and highlights the innermost method-name element:

- `greet` in a direct call.
- `create` in `Factory.create`.

Unknown or unresolved calls do not produce argument-count diagnostics.

## Verification Matrix

Automated tests must cover the following behavior across parenthesized, bare-argument, and argumentless syntax where each form is legal:

- A direct top-level method with one or more required parameters.
- Constant, qualified constant, and absolute constant receivers.
- Class and instance receiver methods with required, optional, excess, positional, and named arguments.
- Constructors resolved through the combined explicit `self.new` and initializer-backed overload pool, including a call accepted only by an initializer while explicit `self.new` definitions also exist.
- Implicit zero-argument construction only when both explicit definition sets are empty; rejecting explicit overloads must not fall through to implicit construction.
- Receiver types inferred from the nearest local assignment, typed parameters, typed instance variables, and consistent instance-variable assignments collected across the current enclosing type regardless of source order or control-flow branch while excluding nested and inherited type bodies.
- Local reassignment before and after a call, proving that only the nearest preceding assignment controls that call site and that an unresolved nearer assignment suppresses fallback.
- Methods inherited by class and instance receivers, including a nearer inapplicable declaration that must not block an inherited applicable overload.
- Overloads where one overload accepts the supplied arguments and calls where one uniquely closest rejecting overload supplies the diagnostic.
- Same-named class methods on unrelated receiver types, which must not affect the call.
- Same-named instance and top-level methods, which must not satisfy or invalidate a constant receiver call.
- Untyped, typed, and nilable required parameters.
- Parameters with explicit defaults.
- Splat and double-splat-only signatures.
- Required parameters combined with splat or double-splat parameters.
- Required named-only parameters.
- A combined nilable/default/splat/named-only/double-splat signature that reports only required regular parameters.
- Local variables, regular parameters, destructured method/block/macro parameters, internal parameter names, type declarations, and same-named macros that shadow indexed method names.
- Simple constant receivers resolved through an unambiguous enclosing lexical namespace, including rejection of ambiguous and unrelated namespace identities.
- Simple record constructors validated through exact current-file `RecordFallback`, including argumentless calls and lexical collisions with indexed types.
- Suppression for unknown, ambiguous, union, nilable, conflicting, class-variable, macro-controlled record, record-instance, and macro-interpolated targets.
- Unknown methods and calls without an exact receiver-specific declaration.
- Single ownership of every call site, with no duplicate diagnostics from nested DOT-call, call-expression, argument-list, or method-name PSI.

Existing direct-call shadowing, parameter classification, excess-argument, named-argument, and splat-expansion behavior remains unchanged except where this contract explicitly unifies resolution across call syntax. Record instance calls remain suppressed pending exact generated-signature resolution; exact qualified record constructors use the shared constructor resolver now.
