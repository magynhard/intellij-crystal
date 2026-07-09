# Completion

Specs to define the behaviour of code completion in the Crystal plugin.

## General Behaviours

### Triggering

| Trigger | Context | Popup Content |
|---------|---------|---------------|
| `.` after CONSTANT | Static methods of the class + `.new` |
| `.` after identifier | Instance methods (via type inference) |
| `::` after CONSTANT | Nested types |
| Ctrl+Space anywhere | Context-dependent (scope items, classes, etc.) |

### Disambiguation

Completion respects namespace hierarchy. When multiple classes share the same simple
name (e.g. `Foo::Sub` and `Bar::Sub`), only methods/types from the correct enclosing
class are suggested.

### Uppercase-Prefix Rule

Free-text completion distinguishes between lowercase and uppercase prefixes:

- **Lowercase prefix** → only scope items (parameters, local variables, @vars, class methods). No classes or stdlib types.
- **Uppercase prefix** → scope items + classes + stdlib types.
- **Empty prefix** → scope items + classes + stdlib types.

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
- After integer literals on the same line
- After float literals on the same line

```crystal
"hello"     # ← no completion after the closing quote
42          # ← no completion after 42
3.14        # ← no completion after 3.14
```

---

## Specific Behaviours

### Free-Text Completion

Free-text completion offers scope-aware suggestions based on the current cursor position.

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

### Method Chain Completion

Completion after method chains would resolve the return type of the previous method:

```crystal
# Currently NOT supported:
"hello".reverse.       # ← would need to know reverse returns String
[1, 2, 3].first.      # ← would need to know first returns Int32
users.select(&.active?).map(&.name).  # ← would need generic type resolution
```

**Challenges:** Requires a return-type table for all stdlib methods, generic type parameter resolution, and union-type handling. Crystal's compiler does this natively, but the PSI-based approach doesn't have access to the compiler's type system.

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

### `require` Path Completion

Completion after `require` would show local `.cr` files:

```crystal
require "./  # ← would show local .cr files
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
