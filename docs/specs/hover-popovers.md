# Hover Popovers

Specifications for hover/tooltip popups in the Crystal IntelliJ plugin.

## Overview

The plugin shows type information when hovering over variables and parameters
in the editor. These popups use the same visual pattern: a two-line display
with the type (clickable link) on line 1 and the identifier name on line 2.

## Display Formats

### Variable Hover

When hovering over a variable identifier (definition or usage):

```
String (Variable)
my_variable_name
```

### Parameter Hover

When hovering over a parameter name:

```
String (Parameter)
age
```

### Class/Method Hover

When hovering over a class name or method definition, the existing QuickDoc
popup shows the full signature with syntax highlighting and doc comments.

## Examples

| Crystal Code | Hover Target | Display |
|---|---|---|
| `x = "hello"` | `x` (definition) | `String (Variable)` / `x` |
| `puts x` | `x` (usage) | `String (Variable)` / `x` |
| `x = 1` | `x` | `Int32 (Variable)` / `x` |
| `x = [1, 2]` | `x` | `Array (Variable)` / `x` |
| `x = {"a" => 1}` | `x` | `Hash(String, Int32) (Variable)` / `x` |
| `x = {1, "hi"}` | `x` | `Tuple(Int32, String) (Variable)` / `x` |
| `x = true ? 1 : nil` | `x` | `Int32 \| Nil (Variable)` / `x` |
| `@name = "hi"` | `@name` | `String (Variable)` / `@name` |
| `def foo(name : String)` | `name` | `String (Parameter)` / `name` |

## Behavior

- **Variable with known type:** Show inferred type + `(Variable)` label.
- **Variable with unknown type:** Show `Any (Variable)`.
- **Parameter hover:** Unchanged — continues to show `(Parameter)` label.
- **Instance variables (`@var`):** Same as local variables — show inferred type.
- **Usage sites:** Hover works on variable usages (e.g. `puts arr`), not just definitions.
- **Method arguments:** Hover works inside method calls (e.g. `foo(x)`).

## Numeric Type Linking

Integer and Float types are linked to their parent type documentation since
they don't have individual documentation pages:

| Type | Linked To |
|---|---|
| `Int8`, `Int16`, `Int32`, `Int64`, `Int128` | `Int` |
| `UInt8`, `UInt16`, `UInt32`, `UInt64`, `UInt128` | `Int` |
| `Float32`, `Float64` | `Float` |

This applies to:
- Parameter type annotations (`def foo(x : Int32)`)
- Inferred variable types (`x = 42` → `Int32`)
- Any other type display in popups

## Implementation

Extend `CrystalDocumentationProvider`:

1. In `getCustomDocumentationElement` — unwrap argument wrappers
   (`CrystalArgument`, `CrystalBareArgument`) and detect variable identifiers.
2. In `resolveTarget` — recognize variable identifiers, return directly.
3. In `generateDoc` — detect variable targets, call `buildVariableDocumentation()`.
4. `buildVariableSignatureHtml` renders HTML: clickable type link + `(Variable)` label.
5. `wrapTypeLinks` links numeric types to parent (`Int`/`Float`) via
   `resolveNumericTypeLink()`.

## Files

- `src/main/kotlin/de/magynhard/crystal/documentation/CrystalDocumentationProvider.kt`
- `src/test/kotlin/de/magynhard/crystal/documentation/CrystalDocumentationProviderTest.kt`
