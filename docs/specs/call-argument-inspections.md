# Call Argument Inspections

Behavioral specification for validating Crystal method call arguments against `def` signatures.

## Scope

`CrystalArgumentCountInspection` validates calls to methods declared with `def`. It reports missing required arguments, excess arguments, and unknown named arguments when the call can be resolved to one or more method definitions.

The inspection already covers parenthesized calls and calls with bare arguments. The immediate change defined here only adds discovery and missing-argument validation for argumentless calls without parentheses:

```crystal
process()
process value
process
Processor.process
```

The first two forms document existing behavior and must remain unchanged. Only `process` and `Processor.process` are new inspection entry points.

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

This change guarantees missing-argument detection for argumentless calls. Full enforcement of positional use after a named-only separator, external parameter names, and signature ordering is tracked separately in `TODO.md`.

## Argumentless Call Discovery

Argumentless calls without parentheses do not contain an argument-list PSI element:

- A direct call such as `greet` is represented as `CrystalVariableReference`.
- A receiver call such as `Greeter.greet` contains `CrystalDotCallAccess` with neither `CrystalCallArgs` nor `CrystalBareArgumentList`.

The inspection must visit these PSI forms directly and evaluate them with an empty argument list. It must not change the grammar or reinterpret variable PSI globally. Each syntactic call site is inspected exactly once:

- A `CrystalVariableReference` is eligible when it is not already the method name of an explicit call and is not a receiver or namespace segment. It may be nested as an operand, assignment value, return value, or argument because Crystal permits argumentless method calls in expression positions.
- A `CrystalDotCallAccess` is eligible only when both `callArgs` and `bareArgumentList` are absent.
- Existing parenthesized and bare-argument calls remain owned by their current call-expression or argument-list visitor paths.

Direct variable references are checked only when local resolution does not identify a regular parameter, block parameter, preceding local assignment, type declaration, or same-named macro. Parameter shadowing uses the parameter's internal declaration name when an external call-site name is present. This preserves local/declaration shadowing and prevents a same-named indexed method from producing a false positive. Other declaration kinds and full Crystal call-precedence modeling remain outside this focused change.

After local-shadow filtering, direct calls query only `CrystalTopLevelMethodIndex`. Instance methods and class methods enclosed by unrelated types are never candidates for an unqualified argumentless call. All same-named top-level overloads are evaluated. The lookup must use StubIndex and must not scan project files.

Argumentless DOT-calls in this change are limited to constant and qualified constant receivers. The receiver must resolve to an exact class or module identity. Candidates come only from that receiver's `def self.<name>` definitions; instance methods and top-level methods are excluded. All matching class-method overloads are evaluated. Unknown, ambiguous, inferred instance, and `.new` receiver calls remain unchanged and do not enter the new argumentless path.

Candidate discovery applies cheap PSI and name gates before reference or index resolution. It performs only direct StubIndex queries and introduces no project-wide file scans.

## Overloads

Every applicable overload is evaluated independently. An argumentless call is valid when at least one overload accepts zero arguments:

```crystal
def process(value)
end

def process
end

process # Valid
```

When no overload accepts zero arguments, the inspection reports the missing parameters from the uniquely closest overload using the existing overload ranking. Deterministic selection between equally ranked overloads with different parameter names remains deferred in `TODO.md`.

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

Automated tests cover argumentless calls without parentheses for:

- A direct top-level method with one or more required parameters.
- A constant receiver method with required parameters.
- A qualified constant receiver method.
- Same-named class methods on unrelated receiver types, which must not affect the call.
- Same-named instance and top-level methods, which must not satisfy or invalidate a constant receiver call.
- Untyped, typed, and nilable required parameters.
- Parameters with explicit defaults.
- Splat and double-splat-only signatures.
- Required parameters combined with splat or double-splat parameters.
- Required named-only parameters.
- A combined nilable/default/splat/named-only/double-splat signature that reports only required regular parameters.
- Overloads where one overload accepts zero arguments.
- Multiple rejecting overloads with one uniquely closest overload supplying the diagnostic.
- Local variables, regular parameters, block parameters, internal parameter names, type declarations, and same-named macros that shadow indexed method names.
- Unknown methods and unresolved, ambiguous, inherited-only, or inferred-instance receivers.
- Parenthesized and bare-argument calls that remain owned by existing visitor paths and produce no duplicate diagnostics.

Existing parenthesized, bare-argument, excess-argument, named-argument, splat-expansion, constructor, record, and type-checking behavior remains unchanged. This task adds no new constructor or record coverage.
