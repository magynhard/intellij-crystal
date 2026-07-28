# Completion

Specs to define the behaviour of code completion in the Crystal plugin.

## Architecture

`CrystalCompletionContributor` owns IntelliJ registration and the ordered dispatch policy. The
require, suppression, override, type, class-body, annotation, DOT, namespace, and free-text
branches remain in that contributor because their order and early returns are behaviorally
significant.

Context classification is implemented by package-level helpers in `CrystalCompletionContext`.
Candidate generation is split between `CrystalLocalCompletionProvider` for scope-sensitive
locals, parameters, variables, and methods, and `CrystalSymbolCompletionProvider` for classes
and file/class-body constants. Indexed type and method access goes through `CrystalIndexService`;
constants and instance/class variables are collected from live PSI because no declaration indexes
exist for them. Local candidates share one deduplication set across all candidate sources. The split
preserves the contributor's established dispatch order, ranking, deduplication, and results.

## General Behaviours

### Triggering

| Trigger | Context | Popup Content |
|---------|---------|---------------|
| `.` after a type object | Static methods of the type + `.new` when constructible |
| `.` after a value expression | Instance methods of every resolved receiver type |
| `::` after CONSTANT | Nested types |
| Ctrl+Space anywhere | Context-dependent (scope items, classes, etc.) |

### Disambiguation

Completion respects namespace hierarchy. When multiple classes share the same simple
name (e.g. `Foo::Sub` and `Bar::Sub`), only methods/types from the correct enclosing
class are suggested.

### Uppercase-Prefix Rule

Free-text completion distinguishes between lowercase and uppercase prefixes:

- **Lowercase prefix** → scope items (parameters, local variables, @vars, class methods) + top-level (global) methods. No classes or stdlib types.
- **Uppercase prefix** → scope items + classes + stdlib types.
- **Empty prefix** → scope items + classes + stdlib types + top-level methods.

This prevents hundreds of class names from polluting the popup when typing lowercase identifiers.

**Example:**
```crystal
name = "hello"
na     # ← only scope items (name), no class names
Na     # ← scope items + class names starting with "Na"
       # ← (empty) scope items + class names
```

### Suppression

No completion is offered in these contexts:

- Inside string literals (not interpolation)
- After integer literals on the same line, unless followed by a member-access DOT
- After float literals on the same line, unless followed by a member-access DOT

```crystal
"hello"     # ← no completion after the closing quote
42          # ← no completion after 42
3.14        # ← no completion after 3.14
```

**Exception — `require` path completion:** the caret inside a `require "...<caret>"` string IS offered completion, even though it is inside a string literal. Dispatch and lookup construction are specified in [`require.md`](require.md).

---

## Specific Behaviours

### Free-Text Completion

Free-text completion offers scope-aware suggestions based on the current cursor position.

The synthesized `require` keyword is a context-gated exception to ordinary
free-text candidates. It is offered only when the typed prefix starts an
independent file-scope statement, including between top-level compile-time
macro-control directives. It is not offered in runtime control flow, blocks,
type or callable bodies, macro definitions, DOT calls, namespace access, type
annotations, assignment values, arguments, conditions, unfinished multiline
expressions, or other larger expressions. Real methods named `require` remain
available through normal DOT completion. See [`require.md`](require.md) for
insertion and path-completion details.

The parser still creates `CrystalRequireStatement` PSI in excluded contexts.
`CrystalRequireContextInspection` reports the corresponding compiler error
instead of exposing a generic parser error.

#### Scope-Aware Local Variables

Variables are collected from the **enclosing scope** only:

1. If inside a method → scan the method body
2. If inside a block (not in a method) → scan the block
3. If at top-level → scan the entire file

Assignments **after** the caret position are excluded (forward-reference not allowed in Crystal).

```crystal
def foo
  x = 1        # ← included (before caret)
  y = 2        # ← included (before caret)
  z = 3        # ← NOT included (after caret)
end
```

Variables from **other methods** in the same file are NOT shown:

```crystal
def foo
  x = 1
end

def bar
  x =  # ← x from foo() is NOT suggested here
end
```

#### Block Parameters

Block parameters (`do |x, y|`) are collected from **all enclosing blocks** (not just the nearest one).

```crystal
[1, 2, 3].each do |item|
  [4, 5, 6].each do |inner|
    # ← inner, item are both suggested
  end
end
```

Block parameters use shorthand instance variable assignment syntax (`@param`), which is stripped to `param`:

```crystal
record Point, @x : Int32, @y : Int32

# Block params from record: x, y (not @x, @y)
```

**Priority:** 120 (highest)

#### Method Parameters

Parameters from the enclosing method definition are suggested.

```crystal
def greet(name : String, age : Int32)
  # ← name, age are suggested
end
```

**Priority:** 100

#### For-Loop Variables

Variables from `for` loops are collected from the `for` keyword up to the `in` keyword.

```crystal
for item in [1, 2, 3]
  # ← item is suggested
end

for key, value in {"a" => 1}
  # ← key, value are suggested
end
```

**Priority:** 90

#### Instance and Class Variables

Instance variables (`@var`) and class variables (`@@var`) of the **enclosing class** are suggested as soon as `@` (or `@@`) is typed — not only after a name character. They are collected from **all methods of the enclosing class** (e.g. `@name` defined in `initialize` is offered inside `greet`), because they are class fields available throughout the class.

- Typing `@`  → both `@instance` and `@@class` variables are offered.
- Typing `@@` → only `@@class` variables are offered.
- Typing `@na` → only `@name` (and any `@@name`-style) matches the prefix.

The auto-popup appears on `@` (via `CrystalAtCompletionConfidence`). String literals and `@[` annotation context are excluded.

```crystal
class Foo
  def initialize
    @name = "hello"
    @@count = 0
  end

  def greet
    @  # ← @name, @@count suggested (both)
    @@ # ← @@count suggested (class vars only)
  end
end
```

Nested classes are isolated: an inner class's `@vars` are not offered in the outer class.

> **Note on a bare sigil:** A standalone `@` with nothing typed after it is lexed as a loose `AT` token that the parser attaches directly to the file, which can truncate the enclosing class node in the PSI. As soon as a name character follows (`@n`), the token parses as a proper `INSTANCE_VAR` inside the class and scope resolution works normally. Completion of `@name` and `@@name` is therefore fully reliable once any name character is present; the bare-sigil case is best-effort (class scope resolved by caret offset).

**Priority:** 40

#### Class Method Priority

When inside a method, methods of the **enclosing class** (via `CrystalMethodByClassIndex`) are suggested with higher priority than global methods.

```crystal
class Foo
  def bar
    # ← bar, baz (own class methods) appear before global methods
  end

  def baz
  end
end
```

**Priority:** 30 (own class methods), 20 (inherited class methods), 0 (global methods)

#### Global (Top-Level) Methods

Top-level `def` definitions (methods defined outside any class/module/struct/enum) are suggested in **every** context — at the top level, inside a class method, or inside a block.

Two sources are merged:

1. **Current file (immediate)** — the live PSI of the file being edited is scanned for top-level `def`s, so just-typed or unsaved methods appear immediately without waiting for the stub index to be built or updated. Top-level defs are hoisted in Crystal (callable above their definition site within the file), so the entire file is scanned without the forward-reference restriction that applies to local variables.
2. **Stub index (`CrystalTopLevelMethodIndex`)** — cross-file project defs plus stdlib top-level helpers (`puts`, `pp`, `p`, `print`, `exit`, …), mirroring how class name completion includes stdlib types like `String`/`Int32`. The result's prefix matcher pre-filters names in-memory before loading any PSI from the index, keeping the popup responsive.

The lookup shows the full parameter signature:

```crystal
def kung(foo : String)
  foo
end

kung(  # ← shows (foo : String)
```

```crystal
class Foo
  def bar
    ku   # ← kung (top-level) and bar (own class method) are both suggested
  end
end
```

Self-receiver methods (`def self.xxx`) are **not** indexed or collected as top-level methods, including when such a definition appears directly at file level. Class-scoped self-receiver methods only appear via dot-completion on their enclosing class. A bare `kung` call will not resolve to `def self.kung` inside a class.

Local variables, parameters, and class methods of the enclosing class all take priority over (and dedup against) global methods of the same name. Only one lookup entry per name appears in the popup.

**Priority:** 0

#### Inherited Methods

For classes with a superclass (`class Foo < Bar`), methods from the **direct parent class** are suggested with priority 20.

```crystal
class Animal
  def speak
  end
end

class Dog < Animal
  def bark
    # ← bark (own), speak (inherited) are suggested
  end
end
```

Only the **direct superclass** is queried (no hierarchy traversal for performance).

---

### Dot-Completion

#### Shared Receiver Normalization Contract

`CrystalReceiverExpression` provides neutral PSI infrastructure for every consumer that needs a
conservative DOT receiver identity. Inspection resolvers and the expression-completion receiver
resolver both use the same normalization and exact constant-root contract.

`normalize(receiver)` promotes variable-access leaves to their composite PSI and unwraps nested
`CrystalExpression` and `CrystalGroupedExpression` nodes only while each wrapper contains exactly
one significant receiver expression. A grouped assignment is the nearest legal multi-expression
parenthesized form in the current grammar and remains opaque. Parenthesized comma-separated
expressions are not currently legal `grouped_expression` syntax; if such PSI becomes legal later,
the comma guard must continue to keep it opaque.

`extractExactConstantTypeRoot(receiver)` accepts complete constant identities such as `Foo`,
`Outer::Foo`, and `::Foo`, plus generic type-object calls such as `Box(Int32)` when every argument
is itself an exact type reference. It returns the written qualified root (`Box` for `Box(Int32)`).
Lowercase or dynamic namespace roots such as `value::Foo`, grouped assignments, multiple
expressions, macro interpolation, calls with value arguments, class variables, and otherwise
ambiguous receiver identities return no exact constant root.

#### Expression Receiver Analysis

`CrystalCompletionReceiverResolver` is the completion-facing receiver boundary. It finds the
member-access `DOT` immediately before IntelliJ's completion position. `CrystalPostfixChain` then
decomposes the complete semantic postfix prefix for both completion and neutral expression
analysis. The grammar can flatten a chain into
multiple `CrystalDotCallAccess` siblings or attach an argumentless continuation as a
`CrystalImplicitObjectCall` inside the preceding access's bare-argument PSI; both shapes are
processed recursively in source order for arbitrarily long argumentless or mixed chains. Every
attached implicit call is treated as a continuation only when its DOT is source-adjacent to the
preceding call. Whitespace before the DOT denotes a genuine bare argument, such as
`service.consume .helper`, and is not folded into the receiver chain. Every
postfix component before the completion dot must be recognized; bracket/index access and other
unsupported tails produce `Unknown` rather than allowing analysis of a shorter prefix. Receiver
text is not rebuilt with source regexes. Transparent grouping
is normalized through `CrystalReceiverExpression`; incomplete groups, direct or non-transparent
grouped assignment receivers, macro-interpolated receivers, unknown variables, ambiguous type
identities, and decimal points inside float tokens produce `Unknown`. Assignments nested inside a
supported `if`, `case`, or ternary receiver do not invalidate that receiver.

Exact constant paths are classified before value inference. All runtime values and completed calls
delegate to one `CrystalTypeResolutionSession` from the neutral `de.magynhard.crystal.analysis`
layer. The session owns ordered type sets, forward incoming/outgoing flow, truthiness-aware logical
values, rescue/else/ensure paths, structured reachability, exact constructor/type identities,
method returns, recursion guards, and cached StubIndex/hierarchy lookups. It never
collects assignments across a containing file or uses a project-wide first-name method fallback.

The nearest exact lexically visible indexed declaration produces
a type object with its simple and qualified identity; qualified and absolute paths are marked as
explicit identities, including qualified generic roots such as `Outer::Box(Int32)`. The completion
receiver resolver delegates every other receiver directly to the same neutral session and adapts
its ordered type set without a completion-owned inference path. Completed constructors produce
their exact receiver type only for complete concrete class and struct declarations; abstract
classes, modules, and enums remain `Unknown`. Completed methods require an exact indexed receiver identity and target
method; explicit return annotations are preserved, while unannotated returns use existing body
inference. Exactly one method candidate must remain for each exact receiver identity after
receiver, static/instance mode, and name filtering. Multiple overloads remain `Unknown` until
argument-aware applicability is shared with completion. Unknown or ambiguous targets remain
`Unknown`.

Top-level unions are expanded in source order, so typed parameters and explicit method returns of
`Foo | Bar` produce `Foo`, then `Bar`. Transparent outer type grouping is removed before splitting,
so `(Foo | Bar)` and `((Foo | Bar))` produce the same ordered set. Generic argument parentheses are
not transparent: `Array(Int32 | String)` produces the single outer lookup type `Array`. Empty
normalized names are discarded.

For parameters with external and internal names, receiver inference matches body references to the
internal name (the second identifier); ordinary one-name parameters retain that sole identifier.
Call-site external-name matching remains owned by argument handling and is unchanged.

Assignment expressions have the type of their right-hand side. Completion accepts assignment-valued
branches when the RHS resolves, but `if`, `case`, and ternary result inference is complete-or-unknown:
if any reachable branch has no type, receiver analysis returns `Unknown` instead of narrowing to the
typed sibling branches.

`CrystalExpressionTypeResolver` and `CrystalTypeInference` are compatibility adapters over the
neutral result. The legacy public inference API keeps annotation first-arm/base normalization while
assignment-derived values retain full rendered generic/union types. Completion and PSI references
do not depend on completion-owned or legacy file-wide inference.

`CrystalCompletionHelper` accepts an ordered list of exact receiver type roots and resolves them
through one neutral `CrystalTypeResolutionSession`. It collects instance methods with the shared
hierarchy metadata, preserves receiver and hierarchy order, and merges identical canonical
signatures across types while retaining distinct overloads. Qualified roots remain exact, so types
with the same simple name do not leak methods across namespaces. Primitive and generic outer roots
use the same indexed declaration path as project types. The existing single-type helper delegates
to this multi-type API.

`CrystalCompletionContributor` dispatches DOT completion exclusively through this receiver result.
`TypeObject` receivers are delegated to a focused provider that preserves static-method rendering,
qualified filtering, record constructor signatures, synthetic `new` behavior, icons, and priority.
`ValueTypes` receivers are passed as one ordered set to the multi-type helper, so every union branch
contributes methods, identical canonical signatures appear once, and distinct overloads remain
separate. The contributor no longer classifies receivers from the token immediately before the DOT.

Receiver dispatch runs before ordinary numeric-literal suppression. Consequently, `3.` is direct
`Int32` member access and `3.14.` is `Float64` member access, while completion inside the float token
in `3.1` is not DOT completion. A syntactically recognized member-access DOT whose receiver resolves
to `Unknown` returns no candidates and never falls through to unrelated free-text project methods.

Transparent grouping has full lookup parity: `Foo.`, `(Foo).`, and `((Foo)).` produce the same
type-object candidates, and grouping likewise preserves qualified/absolute constants, modules,
records, runtime variables, typed parameters, instance variables, scalar literals, collections,
operators, and supported method-result expressions. Runtime values receive instance methods only;
they do not receive constructors or static-only methods, and modules do not receive `new`.

#### Static Method Completion (`CONSTANT.method`)

Shows all static methods (`def self.xxx`) of the given class.

```crystal
String.new        # ← new is offered
String.build do   # ← build is offered
```

When the constant is part of a namespace (`Foo::Sub.method`), only methods from `Foo::Sub` are shown (not from `Bar::Sub`).

```crystal
module Foo
  class Sub
    def self.bar
    end
  end
end

module Bar
  class Sub
    def self.baz
    end
  end
end

Foo::Sub.  # ← bar is offered, NOT baz
```

#### `.new` Constructor Completion

When typing `.new` after a class name, the **initialize parameters** are shown:

```crystal
class Person
  def initialize(@name : String, @age : Int32)
  end
end

Person.new(  # ← shows (name : String, age : Int32)
```

For classes **without** an `initialize` method, `.new` is still offered but with no parameters.

For **record** macros, `.new` is offered with the record's field parameters:

```crystal
record Point, x : Int32, y : Int32

Point.new(  # ← shows (x : Int32, y : Int32)
```

#### Instance Method Completion (`variable.method`)

Shows instance methods based on **type inference**:

```crystal
name = "hello"    # ← inferred type: String
name.             # ← String methods (to_s, upcase, length, etc.)
```

If the type is **unknown** (no annotation, no assignment), **no methods** are offered. There is no fallback to showing all project methods.

```crystal
def foo(x)
  x.  # ← no methods offered (type of x is unknown)
end

def bar(x : String)
  x.  # ← String methods offered (type annotation)
end
```

#### Instance Variable Dot-Completion (`@var.method`)

Instance variables are resolved via type inference:

```crystal
class Foo
  @name = "hello"

  def bar
    @name.  # ← String methods offered
  end
end
```

---

### Type Annotations (`:`)

When typing `:` in a type annotation context, stdlib types and project types are offered.

```crystal
x : Str  # ← String, Struct, etc. are suggested
```

Filters by prefix. Includes nested types from the enclosing class:

```crystal
class Foo
  class Inner
  end

  x : In  # ← Inner, Int32, etc. are suggested
end
```

---

### Namespace Completion (`::`)

When typing `::` after a CONSTANT, nested types are offered:

```crystal
Foo::  # ← types nested inside Foo
```

Auto-popup is triggered automatically (no Ctrl+Space needed). The `CrystalTypedHandler.checkAutoPopup()` detects the second `:` and schedules the popup.

---

### Annotations (`@[`)

When typing `@[`, a hardcoded list of Crystal annotations is offered:

```crystal
@[  # ← Flags, Link, AlwaysInline, etc. are suggested
```

---

### Class Body

When the caret is inside a class/struct body **but not inside a method**, class body macros/keywords are offered:

```crystal
class Foo
  get  # ← getter, getter!, getter? are suggested
  inc  # ← include is suggested
end
```

---

### Override Methods (`def ` in class)

When typing `def ` inside a class body, common methods to override are suggested:

```crystal
class Foo
  def   # ← initialize, to_s, inspect, ==, hash, etc. are suggested
end
```

The override suggestion inserts the full method signature with `super` call:

```crystal
class Foo
  def to_s(io)
    super
  end
end
```

---

## Priority Schema

| Item | Priority | Icon | Bold |
|------|----------|------|------|
| Block parameters | 120 | Parameter | ✓ |
| Method parameters | 100 | Parameter | ✓ |
| For-loop variables | 90 | Variable | ✓ |
| Local variables | 50 | Variable | ✓ |
| Instance/class variables | 40 | Variable | ✓ |
| Own class methods | 30 | Method | ✗ |
| Inherited class methods | 20 | Method | ✗ |
| Global methods | 0 | Method | ✗ |

---

## Edge Cases

### Forward-Reference Excluded
Assignments **after** the caret are not included:
```crystal
x = 1
y = 2
# ← y is NOT suggested here (it's after the caret)
y = 3
```

### Nested Block Parameters
All enclosing block parameters are suggested (not just the nearest):
```crystal
[1].each do |a|
  [2].each do |b|
    [3].each do |c|
      # ← a, b, c all suggested
    end
  end
end
```

### Shorthand Instance Variable Block Parameters
Block parameters using `@param` syntax are stripped to `param`:
```crystal
# From record Point, @x, @y
# Block param shows as "x" not "@x"
```

### Multiple For-Loop Variables
Multiple variables in `for key, value in hash` are all suggested.

### Namespace Disambiguation
`Foo::Sub.method` only shows methods from `Foo::Sub`, not from `Bar::Sub`.

### Unknown Type Fallback
When type inference fails for `variable.method`, **no methods** are offered (not all project methods).

### `.new` Without `initialize`
Classes without an explicit `initialize` method still offer `.new` but with no parameters.

---

## Future Features (Not Yet Implemented)

The following features are documented as potential future enhancements:

### Enum Value Completion

Completion after enum type would show enum values:

```crystal
enum Color
  Red
  Green
  Blue
end

Color.  # ← would show Red, Green, Blue
```

### `include` Module Completion

Completion after `include` would show available modules:

```crystal
class Foo
  include  # ← would show all available modules
end
```

Currently only hardcoded `include` and `extend` are offered as class body macros.

### Constant Completion

Completion of constants defined in the project:

```crystal
MY_CONSTANT = 42

x = MY_  # ← would show MY_CONSTANT
```
