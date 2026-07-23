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

Full enforcement of positional use after a named-only separator, external parameter names, and signature ordering is tracked separately in `TODO.md`.

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

Constant receivers search class methods. Inferred value receivers search instance methods. Top-level methods, instance methods on unrelated types, and class methods on unrelated types never enter the candidate set. Resolution and hierarchy lookup use StubIndex-backed declarations and must not scan project files or fall back to a method name alone.

The receiver's exact type and its ancestors form one hierarchy lookup. All exact same-named overloads from that hierarchy form the candidate set and are evaluated independently. An unrelated declaration must not enter the set, and a rejecting declaration at a nearer hierarchy level must not block an inherited overload that accepts the call.

## Constructor Resolution

Calls to `.new` use constructor-specific precedence for every call syntax. Against the exact receiver's applicable hierarchy, select the first non-empty definition set in this order:

1. All `def self.new` definitions, including inherited definitions.
2. Only when no `def self.new` definitions exist, all `initialize` definitions, including inherited definitions.
3. Only when neither definition set exists, implicit zero-argument construction.

Constructor fallback depends only on definition-set presence, never on whether an overload accepts the supplied arguments. Once a non-empty `self.new` or `initialize` set is selected, the inspection evaluates that full overload set and reports against its uniquely closest overload when none accepts. It must not fall through to the next constructor source after selecting a non-empty set. Implicit construction supplies an exact zero-argument signature only when both explicit definition sets are empty; a call that supplies arguments is diagnosed against that signature rather than treated as an unknown target.

## Suppression

Argument diagnostics require exact resolution. The inspection emits no argument-count or named-argument diagnostic when the target depends on any of the following:

- An unknown or ambiguous receiver identity.
- A union or nilable receiver type, until control-flow narrowing supplies one exact non-nil type.
- Conflicting local-variable receiver information that prevents one exact nearest assignment from being resolved.
- Conflicting instance-variable assignment types.
- A class-variable receiver.
- A record constructor or record instance method until records use the shared call resolver.
- A macro-interpolated receiver, method name, or constructor target.

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
- Constructors resolved through a non-empty `self.new` definition set, fallback to a non-empty `initialize` definition set only when no `self.new` definitions exist, and implicit zero-argument construction only when neither definition set exists.
- Constructor definition-set precedence when no overload in the selected set accepts: rejecting `self.new` definitions must not fall through to `initialize` or implicit construction, and rejecting `initialize` definitions must not fall through to implicit construction.
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
- Suppression for unknown, ambiguous, union, nilable, conflicting, class-variable, record, and macro-interpolated targets.
- Unknown methods and calls without an exact receiver-specific declaration.
- Single ownership of every call site, with no duplicate diagnostics from nested DOT-call, call-expression, argument-list, or method-name PSI.

Existing direct-call shadowing, parameter classification, excess-argument, named-argument, and splat-expansion behavior remains unchanged except where this contract explicitly unifies resolution across call syntax. Records remain suppressed pending the shared-resolver follow-up in `TODO.md`.
